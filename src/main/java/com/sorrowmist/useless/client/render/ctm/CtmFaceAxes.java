package com.sorrowmist.useless.client.render.ctm;

import net.minecraft.core.Direction;

public record CtmFaceAxes(Direction right, Direction down) {
    public Direction left() {
        return right.getOpposite();
    }

    public Direction up() {
        return down.getOpposite();
    }

    public static CtmFaceAxes forFace(Direction face) {
        return switch (face) {
            case EAST -> new CtmFaceAxes(Direction.NORTH, Direction.DOWN);
            case WEST -> new CtmFaceAxes(Direction.SOUTH, Direction.DOWN);
            case NORTH -> new CtmFaceAxes(Direction.WEST, Direction.DOWN);
            case SOUTH -> new CtmFaceAxes(Direction.EAST, Direction.DOWN);
            // Keep the horizontal-face V axis aligned with vanilla cube UVs.
            case DOWN -> new CtmFaceAxes(Direction.EAST, Direction.NORTH);
            case UP -> new CtmFaceAxes(Direction.EAST, Direction.SOUTH);
        };
    }
}
