package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.*;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Canonical 3x4x3 template shared by validation, preview and automatic construction. */
public final class OmniversalAlloyFurnaceStructure {
    public static final int COIL_COUNT = 16;
    public static final int FUNCTIONAL_CASING_COUNT = 17;
    /** Assembly, hub, one maintenance hatch and the muffler replace four shell positions. */
    public static final int CASING_COUNT = 13;
    public static final BlockPos PREFERRED_PATTERN_ASSEMBLY_POS = new BlockPos(-1, 0, 0);
    public static final BlockPos PREFERRED_MOLD_HUB_POS = new BlockPos(1, 0, 0);
    public static final BlockPos PREFERRED_MAINTENANCE_HATCH_POS = new BlockPos(0, 3, 2);
    public static final BlockPos PREFERRED_MUFFLER_POS = new BlockPos(0, 3, 1);
    private static final List<Entry> ENTRIES = createEntries();

    private OmniversalAlloyFurnaceStructure() {}

    public enum Part { CORE, CASING, COIL, AIR }
    public enum FunctionalPart {
        PATTERN_ASSEMBLY, MOLD_HUB, PASSIVE_HATCH, MAINTENANCE_HATCH, AUTO_MAINTENANCE_HATCH, MUFFLER_HATCH
    }
    public record Entry(BlockPos localPos, Part part) {
        public BlockPos worldPos(BlockPos corePos, Direction facing) { return toWorld(corePos, facing, localPos); }
    }
    public record Mismatch(BlockPos worldPos, Part expected, BlockState actual) {}
    public record ValidationResult(boolean valid, int coilTier,
                                   @Nullable BlockPos patternAssemblyPos, @Nullable BlockPos moldHubPos,
                                   @Nullable BlockPos passiveHatchPos, @Nullable BlockPos maintenanceHatchPos,
                                   @Nullable BlockPos mufflerHatchPos, List<Mismatch> mismatches) {
        public ValidationResult {
            patternAssemblyPos = immutable(patternAssemblyPos);
            moldHubPos = immutable(moldHubPos);
            passiveHatchPos = immutable(passiveHatchPos);
            maintenanceHatchPos = immutable(maintenanceHatchPos);
            mufflerHatchPos = immutable(mufflerHatchPos);
            mismatches = List.copyOf(mismatches);
        }
    }

    @Nullable private static BlockPos immutable(@Nullable BlockPos pos) { return pos == null ? null : pos.immutable(); }
    public static List<Entry> entries() { return ENTRIES; }

    public static BlockPos toWorld(BlockPos corePos, Direction facing, BlockPos localPos) {
        Direction front = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        return corePos.relative(front.getClockWise(), localPos.getX()).above(localPos.getY())
                .relative(front.getOpposite(), localPos.getZ());
    }

    public static ValidationResult validate(LevelReader level, BlockPos corePos, Direction facing) {
        List<Mismatch> errors = new ArrayList<>();
        Map<FunctionalPart, Integer> counts = new EnumMap<>(FunctionalPart.class);
        Map<FunctionalPart, BlockPos> positions = new EnumMap<>(FunctionalPart.class);
        int tier = 0;
        for (Entry entry : ENTRIES) {
            BlockPos pos = entry.worldPos(corePos, facing);
            if (!isChunkLoaded(level, pos)) return new ValidationResult(false, 0, null, null, null, null, null, List.of());
            BlockState state = level.getBlockState(pos);
            if (entry.part() == Part.COIL) {
                if (!(state.getBlock() instanceof UselessCoilBlock coil)) errors.add(new Mismatch(pos, Part.COIL, state));
                else if (tier != 0 && tier != coil.tier()) errors.add(new Mismatch(pos, Part.COIL, state));
                else tier = coil.tier();
            } else if (entry.part() == Part.CASING) {
                FunctionalPart functional = functionalPart(state);
                if (functional == null) {
                    if (!state.is(ModTags.OMNIVERSAL_FURNACE_CASINGS)) errors.add(new Mismatch(pos, Part.CASING, state));
                    continue;
                }
                if (functional == FunctionalPart.MUFFLER_HATCH && (!entry.localPos().equals(PREFERRED_MUFFLER_POS)
                        || state.getValue(DirectionalMultiblockPartBlock.FACING) != Direction.UP)) {
                    errors.add(new Mismatch(pos, Part.CASING, state));
                    continue; // A misplaced muffler cannot steal the canonical muffler's slot.
                }
                int count = counts.merge(functional, 1, Integer::sum);
                positions.putIfAbsent(functional, pos.immutable());
                if (count > 1 || functional == FunctionalPart.AUTO_MAINTENANCE_HATCH
                        && counts.getOrDefault(FunctionalPart.MAINTENANCE_HATCH, 0) > 0
                        || functional == FunctionalPart.MAINTENANCE_HATCH
                        && counts.getOrDefault(FunctionalPart.AUTO_MAINTENANCE_HATCH, 0) > 0) {
                    errors.add(new Mismatch(pos, Part.CASING, state));
                }
                if (isClaimedByAnotherCore(level, corePos, pos, functional)
                        || isSharedFunctionalPosition(level, corePos, pos)) addMismatch(errors, pos, state);
            } else if (entry.part() == Part.CORE ? !(state.getBlock() instanceof MultiblockAlloyFurnaceCoreBlock) : !state.isAir()) {
                errors.add(new Mismatch(pos, entry.part(), state));
            }
        }
        int maintenanceCount = counts.getOrDefault(FunctionalPart.MAINTENANCE_HATCH, 0)
                + counts.getOrDefault(FunctionalPart.AUTO_MAINTENANCE_HATCH, 0);
        List<Mismatch> missing = new ArrayList<>();
        // Missing fixed-position exhaust takes priority over a misplaced same-height hatch.
        addMissing(level, corePos, facing, missing, counts.getOrDefault(FunctionalPart.MUFFLER_HATCH, 0), PREFERRED_MUFFLER_POS);
        addMissing(level, corePos, facing, missing, counts.getOrDefault(FunctionalPart.PATTERN_ASSEMBLY, 0), PREFERRED_PATTERN_ASSEMBLY_POS);
        addMissing(level, corePos, facing, missing, counts.getOrDefault(FunctionalPart.MOLD_HUB, 0), PREFERRED_MOLD_HUB_POS);
        addMissing(level, corePos, facing, missing, maintenanceCount, PREFERRED_MAINTENANCE_HATCH_POS);
        errors.addAll(0, missing);
        boolean validCounts = isFunctionalPartCountsValid(counts.getOrDefault(FunctionalPart.PATTERN_ASSEMBLY, 0),
                counts.getOrDefault(FunctionalPart.MOLD_HUB, 0), counts.getOrDefault(FunctionalPart.PASSIVE_HATCH, 0),
                maintenanceCount, counts.getOrDefault(FunctionalPart.MUFFLER_HATCH, 0));
        return new ValidationResult(errors.isEmpty() && validCounts && tier > 0, tier,
                positions.get(FunctionalPart.PATTERN_ASSEMBLY), positions.get(FunctionalPart.MOLD_HUB),
                positions.get(FunctionalPart.PASSIVE_HATCH), positions.containsKey(FunctionalPart.MAINTENANCE_HATCH)
                ? positions.get(FunctionalPart.MAINTENANCE_HATCH) : positions.get(FunctionalPart.AUTO_MAINTENANCE_HATCH),
                positions.get(FunctionalPart.MUFFLER_HATCH), errors);
    }

    private static void addMissing(LevelReader level, BlockPos corePos, Direction facing,
                                   List<Mismatch> errors, int count, BlockPos preferred) {
        if (count != 0) return;
        BlockPos pos = toWorld(corePos, facing, preferred);
        errors.add(new Mismatch(pos, Part.CASING, level.getBlockState(pos)));
    }

    public static boolean isFunctionalPartCountsValid(int assembly, int hub, int passive, int maintenance, int muffler) {
        return assembly == 1 && hub == 1 && isPassiveHatchCountValid(passive) && maintenance == 1 && muffler == 1;
    }
    static boolean isPassiveHatchCountValid(int count) { return count >= 0 && count <= 1; }

    @Nullable public static FunctionalPart functionalPart(BlockState state) {
        if (state.getBlock() instanceof MePatternAssemblyBlock) return FunctionalPart.PATTERN_ASSEMBLY;
        if (state.getBlock() instanceof OmniversalMoldHubBlock) return FunctionalPart.MOLD_HUB;
        if (state.getBlock() instanceof PassiveCraftingHatchBlock) return FunctionalPart.PASSIVE_HATCH;
        if (state.getBlock() instanceof MaintenanceHatchBlock) return FunctionalPart.MAINTENANCE_HATCH;
        if (state.getBlock() instanceof AutoMaintenanceHatchBlock) return FunctionalPart.AUTO_MAINTENANCE_HATCH;
        if (state.getBlock() instanceof MufflerHatchBlock) return FunctionalPart.MUFFLER_HATCH;
        return null;
    }

    private static boolean isClaimedByAnotherCore(LevelReader reader, BlockPos core, BlockPos pos, FunctionalPart part) {
        if (!(reader instanceof Level level)) return false;
        var entity = level.getBlockEntity(pos);
        return switch (part) {
            case PATTERN_ASSEMBLY -> entity instanceof MePatternAssemblyBlockEntity assembly && assembly.isClaimedByOtherController(core);
            case MOLD_HUB -> entity instanceof OmniversalMoldHubBlockEntity hub && hub.isClaimedByOtherController(core);
            case PASSIVE_HATCH -> entity instanceof PassiveCraftingHatchBlockEntity hatch && hatch.isClaimedByOtherController(core);
            case MAINTENANCE_HATCH -> entity instanceof MaintenanceHatchBlockEntity hatch && hatch.isClaimedByOtherController(core);
            case AUTO_MAINTENANCE_HATCH -> entity instanceof AutoMaintenanceHatchBlockEntity hatch && hatch.isClaimedByOtherController(core);
            case MUFFLER_HATCH -> entity instanceof MufflerHatchBlockEntity hatch && hatch.isClaimedByOtherController(core);
        };
    }

    /**
     * Test geometry, not the first owner's formed bit: BOTH structures must reject a shared functional part.
     * Invert every horizontal template at the part, covering opposing controllers four blocks apart too.
     * No block-entity lookup/chunk load or recursive structure validation is needed (also works on clients).
     */
    public static boolean isSharedFunctionalPosition(LevelReader level, BlockPos corePos, BlockPos partPos) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Entry entry : ENTRIES) {
                if (entry.part() != Part.CASING) continue;
                BlockPos otherPos = partPos.subtract(toWorld(BlockPos.ZERO, facing, entry.localPos()));
                if (otherPos.equals(corePos) || !isChunkLoaded(level, otherPos)) continue;
                BlockState state = level.getBlockState(otherPos);
                if (state.getBlock() instanceof MultiblockAlloyFurnaceCoreBlock
                        && state.getValue(MultiblockAlloyFurnaceCoreBlock.FACING) == facing) return true;
            }
        }
        return false;
    }

    private static void addMismatch(List<Mismatch> errors, BlockPos pos, BlockState state) {
        if (errors.stream().noneMatch(e -> e.worldPos().equals(pos))) errors.add(new Mismatch(pos, Part.CASING, state));
    }

    public static void notifyNearbyCores(Level level, BlockPos changedPos) {
        if (level.isClientSide) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // A controller placement/removal can invalidate an overlapping structure above OR below it.
        for (int x = -4; x <= 4; x++) for (int y = -3; y <= 3; y++) for (int z = -4; z <= 4; z++) {
            cursor.setWithOffset(changedPos, x, y, z);
            if (!level.isLoaded(cursor)) continue;
            if (level.getBlockEntity(cursor) instanceof MultiblockAlloyFurnaceCoreBlockEntity core) core.requestStructureValidation();
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isChunkLoaded(LevelReader level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static List<Entry> createEntries() {
        List<Entry> entries = new ArrayList<>(36);
        entries.add(new Entry(BlockPos.ZERO, Part.CORE));
        entries.add(new Entry(PREFERRED_PATTERN_ASSEMBLY_POS, Part.CASING));
        entries.add(new Entry(PREFERRED_MOLD_HUB_POS, Part.CASING));
        for (int z = 1; z <= 2; z++) for (int x = -1; x <= 1; x++) entries.add(new Entry(new BlockPos(x, 0, z), Part.CASING));
        for (int y = 1; y <= 2; y++) for (int z = 0; z <= 2; z++) for (int x = -1; x <= 1; x++) {
            entries.add(new Entry(new BlockPos(x, y, z), x == 0 && z == 1 ? Part.AIR : Part.COIL));
        }
        for (int z = 0; z <= 2; z++) for (int x = -1; x <= 1; x++) entries.add(new Entry(new BlockPos(x, 3, z), Part.CASING));
        return List.copyOf(entries);
    }
}
