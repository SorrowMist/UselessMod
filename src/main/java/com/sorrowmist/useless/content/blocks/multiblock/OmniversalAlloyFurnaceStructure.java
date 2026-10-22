package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.AutoMaintenanceHatchBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.OmniversalMoldHubBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.init.ModBlocks;
import com.sorrowmist.useless.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Canonical 3x4x3 template shared by validation, preview and automatic construction. */
public final class OmniversalAlloyFurnaceStructure {
    public static final int COIL_COUNT = 16;
    /** 外壳位总数，其中四个被功能部件占据，其余为普通外壳。 */
    public static final int FUNCTIONAL_CASING_COUNT = 17;
    /** 扣除装配仓、模具中枢、维护仓与消声仓四个功能部件后剩余的普通外壳数量。 */
    public static final int CASING_COUNT = 13;
    public static final BlockPos PREFERRED_PATTERN_ASSEMBLY_POS = new BlockPos(-1, 0, 0);
    public static final BlockPos PREFERRED_MOLD_HUB_POS = new BlockPos(1, 0, 0);
    /** 维护仓位置不限，此坐标仅用于缺失时给出可高亮的提示位。 */
    public static final BlockPos PREFERRED_MAINTENANCE_HATCH_POS = new BlockPos(0, 3, 2);
    /** 消声仓固定占据最上层中心，位置唯一且不可与其它核心共用。 */
    public static final BlockPos PREFERRED_MUFFLER_POS = new BlockPos(0, 3, 1);

    private static final List<Entry> ENTRIES = createEntries();

    private OmniversalAlloyFurnaceStructure() {
    }

    public enum Part {
        CORE,
        CASING,
        COIL,
        AIR
    }

    public enum FunctionalPart {
        PATTERN_ASSEMBLY,
        MOLD_HUB,
        PASSIVE_HATCH,
        MAINTENANCE_HATCH,
        AUTO_MAINTENANCE_HATCH,
        MUFFLER_HATCH
    }

    public record Entry(BlockPos localPos, Part part) {
        public BlockPos worldPos(BlockPos corePos, Direction facing) {
            return toWorld(corePos, facing, localPos);
        }
    }

    public record Mismatch(BlockPos worldPos, Part expected, BlockState actual) {
    }

    public record ValidationResult(boolean valid, int coilTier,
                                   @Nullable BlockPos patternAssemblyPos,
                                   @Nullable BlockPos moldHubPos,
                                   @Nullable BlockPos passiveHatchPos,
                                   @Nullable BlockPos maintenanceHatchPos,
                                   @Nullable BlockPos mufflerHatchPos,
                                   List<Mismatch> mismatches) {
        public ValidationResult {
            patternAssemblyPos = patternAssemblyPos == null ? null : patternAssemblyPos.immutable();
            moldHubPos = moldHubPos == null ? null : moldHubPos.immutable();
            passiveHatchPos = passiveHatchPos == null ? null : passiveHatchPos.immutable();
            maintenanceHatchPos = maintenanceHatchPos == null ? null : maintenanceHatchPos.immutable();
            mufflerHatchPos = mufflerHatchPos == null ? null : mufflerHatchPos.immutable();
            mismatches = List.copyOf(mismatches);
        }
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static BlockPos toWorld(BlockPos corePos, Direction facing, BlockPos localPos) {
        Direction horizontalFacing = facing.getAxis().isHorizontal() ? facing : Direction.NORTH;
        Direction right = horizontalFacing.getClockWise();
        Direction back = horizontalFacing.getOpposite();
        return corePos.relative(right, localPos.getX())
                .above(localPos.getY())
                .relative(back, localPos.getZ());
    }

    public static ValidationResult validate(LevelReader level, BlockPos corePos, Direction facing) {
        List<Mismatch> mismatches = new ArrayList<>();
        int coilTier = 0;
        int assemblyCount = 0;
        int moldHubCount = 0;
        int passiveHatchCount = 0;
        int maintenanceHatchCount = 0;
        int mufflerHatchCount = 0;
        BlockPos assemblyPos = null;
        BlockPos moldHubPos = null;
        BlockPos passiveHatchPos = null;
        BlockPos maintenanceHatchPos = null;
        FunctionalPart maintenanceHatchPart = null;
        BlockPos mufflerHatchPos = null;

        for (Entry entry : ENTRIES) {
            BlockPos worldPos = entry.worldPos(corePos, facing);
            if (!isChunkLoaded(level, worldPos)) {
                return new ValidationResult(false, 0, null, null, null, null, null, List.of());
            }
            BlockState state = level.getBlockState(worldPos);
            if (entry.part == Part.COIL) {
                if (state.getBlock() instanceof UselessCoilBlock coil) {
                    if (coilTier == 0) {
                        coilTier = coil.tier();
                    } else if (coilTier != coil.tier()) {
                        mismatches.add(new Mismatch(worldPos, Part.COIL, state));
                    }
                } else {
                    mismatches.add(new Mismatch(worldPos, Part.COIL, state));
                }
                continue;
            }
            if (entry.part == Part.CASING) {
                FunctionalPart functionalPart = functionalPart(state);
                if (functionalPart == null) {
                    if (!state.is(ModTags.OMNIVERSAL_FURNACE_CASINGS)) {
                        mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                    }
                    continue;
                }
                switch (functionalPart) {
                    case PATTERN_ASSEMBLY -> {
                        assemblyCount++;
                        if (assemblyCount == 1) assemblyPos = worldPos.immutable();
                        else mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                    }
                    case MOLD_HUB -> {
                        moldHubCount++;
                        if (moldHubCount == 1) moldHubPos = worldPos.immutable();
                        else mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                    }
                    case PASSIVE_HATCH -> {
                        passiveHatchCount++;
                        if (passiveHatchCount == 1) passiveHatchPos = worldPos.immutable();
                        else mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                    }
                    // 普通维护仓与自动维护仓二选一：两者共用一个名额，多放一个即判错。
                    case MAINTENANCE_HATCH, AUTO_MAINTENANCE_HATCH -> {
                        maintenanceHatchCount++;
                        if (maintenanceHatchCount == 1) {
                            maintenanceHatchPos = worldPos.immutable();
                            // 记录实际方块类型，归属检查要按对应方块实体判断，
                            // 否则自动维护仓会被当成普通维护仓而漏检共用。
                            maintenanceHatchPart = functionalPart;
                        } else {
                            mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                        }
                    }
                    case MUFFLER_HATCH -> {
                        // 消声仓只认最上层中心；摆在其它外壳位的消声仓一律记为错误方块，
                        // 不能先到先得，否则错位的消声仓会被认领而正确位置反被标红。
                        if (entry.localPos.equals(PREFERRED_MUFFLER_POS)) {
                            mufflerHatchCount++;
                            mufflerHatchPos = worldPos.immutable();
                        } else {
                            mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                        }
                    }
                }
                continue;
            }
            if (!matches(entry.part, state)) {
                mismatches.add(new Mismatch(worldPos, entry.part, state));
            }
        }
        boolean functionalCountsValid = isFunctionalPartCountsValid(
                assemblyCount, moldHubCount, passiveHatchCount,
                maintenanceHatchCount, mufflerHatchCount);
        // 缺功能方块时外壳本身全都合法，循环里不会产生 Mismatch，结构却判定失效。
        // 补一条指向首选位的记录，否则「未成型但没有可高亮的方块」。
        //
        // 这些补位记录统一插到列表最前：错位的功能部件会与它产生世界坐标 Y 相同的记录，
        // 客户端高亮只取其中最靠前的一条，排在前面才能保证提示落在首选位（如最上层中心），
        // 而不是落在玩家放错的那一格上。
        //
        // 消声仓必须位于最上层中心，与维护仓的首选提示位同在最上层、世界坐标 Y 相同。
        // 维护仓位置不限，消声仓位置唯一，故把消声仓排在维护仓之前，顶层缺件时优先提示中心那一格。
        List<Mismatch> missingPartMismatches = new ArrayList<>(4);
        if (mufflerHatchCount == 0) {
            BlockPos pos = toWorld(corePos, facing, PREFERRED_MUFFLER_POS);
            missingPartMismatches.add(new Mismatch(pos, Part.CASING, level.getBlockState(pos)));
        }
        if (assemblyCount == 0) {
            BlockPos pos = toWorld(corePos, facing, PREFERRED_PATTERN_ASSEMBLY_POS);
            missingPartMismatches.add(new Mismatch(pos, Part.CASING, level.getBlockState(pos)));
        }
        if (moldHubCount == 0) {
            BlockPos pos = toWorld(corePos, facing, PREFERRED_MOLD_HUB_POS);
            missingPartMismatches.add(new Mismatch(pos, Part.CASING, level.getBlockState(pos)));
        }
        if (maintenanceHatchCount == 0) {
            BlockPos pos = toWorld(corePos, facing, PREFERRED_MAINTENANCE_HATCH_POS);
            missingPartMismatches.add(new Mismatch(pos, Part.CASING, level.getBlockState(pos)));
        }
        mismatches.addAll(0, missingPartMismatches);
        if (assemblyPos != null && isClaimedByAnotherCore(level, corePos, assemblyPos,
                FunctionalPart.PATTERN_ASSEMBLY)) {
            mismatches.add(new Mismatch(assemblyPos, Part.CASING, level.getBlockState(assemblyPos)));
        }
        if (moldHubPos != null && isClaimedByAnotherCore(level, corePos, moldHubPos,
                FunctionalPart.MOLD_HUB)) {
            mismatches.add(new Mismatch(moldHubPos, Part.CASING, level.getBlockState(moldHubPos)));
        }
        if (passiveHatchPos != null && isClaimedByAnotherCore(level, corePos, passiveHatchPos,
                FunctionalPart.PASSIVE_HATCH)) {
            mismatches.add(new Mismatch(passiveHatchPos, Part.CASING, level.getBlockState(passiveHatchPos)));
        }
        if (maintenanceHatchPos != null && maintenanceHatchPart != null
                && isClaimedByAnotherCore(level, corePos, maintenanceHatchPos, maintenanceHatchPart)) {
            mismatches.add(new Mismatch(maintenanceHatchPos, Part.CASING,
                    level.getBlockState(maintenanceHatchPos)));
        }
        if (mufflerHatchPos != null && isClaimedByAnotherCore(level, corePos, mufflerHatchPos,
                FunctionalPart.MUFFLER_HATCH)) {
            mismatches.add(new Mismatch(mufflerHatchPos, Part.CASING,
                    level.getBlockState(mufflerHatchPos)));
        }
        return new ValidationResult(mismatches.isEmpty() && functionalCountsValid && coilTier > 0,
                coilTier, assemblyPos, moldHubPos, passiveHatchPos, maintenanceHatchPos,
                mufflerHatchPos, mismatches);
    }

    public static boolean isFunctionalPartCountsValid(
            int patternAssemblyCount, int moldHubCount, int passiveHatchCount,
            int maintenanceHatchCount, int mufflerHatchCount) {
        return patternAssemblyCount == 1 && moldHubCount == 1
                && isPassiveHatchCountValid(passiveHatchCount)
                && maintenanceHatchCount == 1 && mufflerHatchCount == 1;
    }

    static boolean isPassiveHatchCountValid(int count) {
        return count >= 0 && count <= 1;
    }

    @Nullable
    public static FunctionalPart functionalPart(BlockState state) {
        if (state.is(ModBlocks.ME_PATTERN_ASSEMBLY.get())) return FunctionalPart.PATTERN_ASSEMBLY;
        if (state.is(ModBlocks.OMNIVERSAL_MOLD_HUB.get())) return FunctionalPart.MOLD_HUB;
        if (state.is(ModBlocks.PASSIVE_CRAFTING_HATCH.get())) return FunctionalPart.PASSIVE_HATCH;
        if (state.is(ModBlocks.MAINTENANCE_HATCH.get())) return FunctionalPart.MAINTENANCE_HATCH;
        if (state.is(ModBlocks.AUTO_MAINTENANCE_HATCH.get())) return FunctionalPart.AUTO_MAINTENANCE_HATCH;
        if (state.is(ModBlocks.MUFFLER_HATCH.get())) return FunctionalPart.MUFFLER_HATCH;
        return null;
    }

    private static boolean isClaimedByAnotherCore(
            LevelReader level, BlockPos corePos, BlockPos partPos, FunctionalPart part) {
        if (!(level instanceof Level actualLevel)) {
            return false;
        }
        return switch (part) {
            case PATTERN_ASSEMBLY -> actualLevel.getBlockEntity(partPos)
                    instanceof MePatternAssemblyBlockEntity assembly
                    && assembly.isClaimedByOtherController(corePos);
            case PASSIVE_HATCH -> actualLevel.getBlockEntity(partPos)
                    instanceof PassiveCraftingHatchBlockEntity hatch
                    && hatch.isClaimedByOtherController(corePos);
            case MAINTENANCE_HATCH -> actualLevel.getBlockEntity(partPos)
                    instanceof MaintenanceHatchBlockEntity hatch
                    && hatch.isClaimedByOtherController(corePos);
            case AUTO_MAINTENANCE_HATCH -> actualLevel.getBlockEntity(partPos)
                    instanceof AutoMaintenanceHatchBlockEntity hatch
                    && hatch.isClaimedByOtherController(corePos);
            case MUFFLER_HATCH -> actualLevel.getBlockEntity(partPos)
                    instanceof MufflerHatchBlockEntity hatch
                    && hatch.isClaimedByOtherController(corePos);
            case MOLD_HUB -> actualLevel.getBlockEntity(partPos)
                    instanceof OmniversalMoldHubBlockEntity hub
                    && hub.isClaimedByOtherController(corePos);
        };
    }

    public static void notifyNearbyCores(Level level, BlockPos changedPos) {
        if (level.isClientSide) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -2; x <= 2; x++) {
            for (int y = -3; y <= 0; y++) {
                for (int z = -2; z <= 2; z++) {
                    cursor.setWithOffset(changedPos, x, y, z);
                    // Level#getBlockEntity loads missing chunks. During server shutdown that can
                    // resurrect a chunk which is already being unloaded and livelock ChunkMap.
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockEntity(cursor) instanceof MultiblockAlloyFurnaceCoreBlockEntity core) {
                        core.requestStructureValidation();
                    }
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isChunkLoaded(LevelReader level, BlockPos pos) {
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private static boolean matches(Part part, BlockState state) {
        return switch (part) {
            case CORE -> state.is(ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get());
            case CASING -> state.is(ModTags.OMNIVERSAL_FURNACE_CASINGS);
            case AIR -> state.isAir();
            case COIL -> state.getBlock() instanceof UselessCoilBlock;
        };
    }

    private static List<Entry> createEntries() {
        List<Entry> entries = new ArrayList<>(36);
        entries.add(new Entry(new BlockPos(0, 0, 0), Part.CORE));
        entries.add(new Entry(PREFERRED_PATTERN_ASSEMBLY_POS, Part.CASING));
        entries.add(new Entry(PREFERRED_MOLD_HUB_POS, Part.CASING));

        for (int z = 1; z <= 2; z++) {
            for (int x = -1; x <= 1; x++) {
                entries.add(new Entry(new BlockPos(x, 0, z), Part.CASING));
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) {
                for (int x = -1; x <= 1; x++) {
                    entries.add(new Entry(new BlockPos(x, y, z), x == 0 && z == 1 ? Part.AIR : Part.COIL));
                }
            }
        }
        for (int z = 0; z <= 2; z++) {
            for (int x = -1; x <= 1; x++) {
                entries.add(new Entry(new BlockPos(x, 3, z), Part.CASING));
            }
        }
        return List.copyOf(entries);
    }
}