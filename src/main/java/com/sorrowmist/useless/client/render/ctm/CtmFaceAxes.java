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
            // Match GregTech's CTM face projection.  The vertical axis on the
            // horizontal faces is inverted relative to the world Y axis.
            case DOWN -> new CtmFaceAxes(Direction.EAST, Direction.SOUTH);
            case UP -> new CtmFaceAxes(Direction.EAST, Direction.NORTH);
        };
    }
}
