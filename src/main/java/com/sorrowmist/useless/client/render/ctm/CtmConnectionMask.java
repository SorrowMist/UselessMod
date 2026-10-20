package com.sorrowmist.useless.client.render.ctm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class CtmConnectionMask {
    private CtmConnectionMask() {
    }

    public static long collect(
            BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int family = CtmConnectionRules.family(state);
        if (family == CtmConnectionRules.NONE) {
            return 0L;
        }
        long packed = 0L;
        for (Direction face : Direction.values()) {
            CtmFaceAxes axes = CtmFaceAxes.forFace(face);
            int mask = 0;
            if (connects(level, pos, face, family, axes.left(), null)) {
                mask |= CtmQuadrantSelector.LEFT;
            }
            if (connects(level, pos, face, family, axes.right(), null)) {
                mask |= CtmQuadrantSelector.RIGHT;
            }
            if (connects(level, pos, face, family, axes.up(), null)) {
                mask |= CtmQuadrantSelector.UP;
            }
            if (connects(level, pos, face, family, axes.down(), null)) {
                mask |= CtmQuadrantSelector.DOWN;
            }
            if (connects(level, pos, face, family, axes.up(), axes.left())) {
                mask |= CtmQuadrantSelector.UP_LEFT;
            }
            if (connects(level, pos, face, family, axes.up(), axes.right())) {
                mask |= CtmQuadrantSelector.UP_RIGHT;
            }
            if (connects(level, pos, face, family, axes.down(), axes.left())) {
                mask |= CtmQuadrantSelector.DOWN_LEFT;
            }
            if (connects(level, pos, face, family, axes.down(), axes.right())) {
                mask |= CtmQuadrantSelector.DOWN_RIGHT;
            }
            packed |= (long) mask << (face.ordinal() * Byte.SIZE);
        }
        return packed;
    }

    public static int forFace(long packed, Direction face) {
        return (int) (packed >>> (face.ordinal() * Byte.SIZE)) & 0xFF;
    }

    private static boolean connects(
            BlockAndTintGetter level, BlockPos origin, Direction face, int family,
            Direction firstOffset, Direction secondOffset) {
        BlockPos neighborPos = origin.relative(firstOffset);
        if (secondOffset != null) {
            neighborPos = neighborPos.relative(secondOffset);
        }
        if (CtmConnectionRules.family(level.getBlockState(neighborPos)) != family) {
            return false;
        }
        BlockPos occluderPos = neighborPos.relative(face);
        return CtmConnectionRules.family(level.getBlockState(occluderPos)) != family;
    }
}
