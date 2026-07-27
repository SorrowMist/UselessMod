package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
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
    public static final int CASING_COUNT = 15;

    private static final List<Entry> ENTRIES = createEntries();

    private OmniversalAlloyFurnaceStructure() {
    }

    public enum Part {
        CORE,
        PATTERN_ASSEMBLY,
        MOLD_HUB,
        CASING,
        COIL,
        AIR
    }

    public record Entry(BlockPos localPos, Part part) {
        public BlockPos worldPos(BlockPos corePos, Direction facing) {
            return toWorld(corePos, facing, localPos);
        }
    }

    public record Mismatch(BlockPos worldPos, Part expected, BlockState actual) {
    }

    public record ValidationResult(boolean valid, int coilTier,
                                   @Nullable BlockPos passiveHatchPos,
                                   List<Mismatch> mismatches) {
        public ValidationResult {
            passiveHatchPos = passiveHatchPos == null ? null : passiveHatchPos.immutable();
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
        BlockPos passiveHatchPos = null;
        int passiveHatchCount = 0;
        for (Entry entry : ENTRIES) {
            BlockPos worldPos = entry.worldPos(corePos, facing);
            if (!isChunkLoaded(level, worldPos)) {
                return new ValidationResult(false, 0, null, List.of());
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
            if (entry.part == Part.CASING && state.is(ModBlocks.PASSIVE_CRAFTING_HATCH.get())) {
                passiveHatchCount++;
                if (isPassiveHatchCountValid(passiveHatchCount)) {
                    passiveHatchPos = worldPos.immutable();
                } else {
                    mismatches.add(new Mismatch(worldPos, Part.CASING, state));
                }
                continue;
            }
            if (!matches(entry.part, state)) {
                mismatches.add(new Mismatch(worldPos, entry.part, state));
            }
        }
        return new ValidationResult(mismatches.isEmpty() && coilTier > 0,
                coilTier, passiveHatchPos, mismatches);
    }

    static boolean isPassiveHatchCountValid(int count) {
        return count >= 0 && count <= 1;
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
            case PATTERN_ASSEMBLY -> state.is(ModBlocks.ME_PATTERN_ASSEMBLY.get());
            case MOLD_HUB -> state.is(ModBlocks.OMNIVERSAL_MOLD_HUB.get());
            case CASING -> state.is(ModTags.OMNIVERSAL_FURNACE_CASINGS);
            case AIR -> state.isAir();
            case COIL -> state.getBlock() instanceof UselessCoilBlock;
        };
    }

    private static List<Entry> createEntries() {
        List<Entry> entries = new ArrayList<>(36);
        entries.add(new Entry(new BlockPos(0, 0, 0), Part.CORE));
        entries.add(new Entry(new BlockPos(-1, 0, 0), Part.PATTERN_ASSEMBLY));
        entries.add(new Entry(new BlockPos(1, 0, 0), Part.MOLD_HUB));

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
