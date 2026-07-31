package com.sorrowmist.useless.client.render.ctm;

public final class CtmQuadrantSelector {
    public static final int LEFT = 1;
    public static final int RIGHT = 1 << 1;
    public static final int UP = 1 << 2;
    public static final int DOWN = 1 << 3;
    public static final int UP_LEFT = 1 << 4;
    public static final int UP_RIGHT = 1 << 5;
    public static final int DOWN_LEFT = 1 << 6;
    public static final int DOWN_RIGHT = 1 << 7;

    private CtmQuadrantSelector() {
    }

    public static Tile select(int mask, int cornerX, int cornerY) {
        if ((cornerX & ~1) != 0 || (cornerY & ~1) != 0) {
            throw new IllegalArgumentException("CTM corner coordinates must be 0 or 1");
        }
        int horizontalBit = cornerX == 0 ? LEFT : RIGHT;
        int verticalBit = cornerY == 0 ? UP : DOWN;
        int diagonalBit = switch (cornerY * 2 + cornerX) {
            case 0 -> UP_LEFT;
            case 1 -> UP_RIGHT;
            case 2 -> DOWN_LEFT;
            default -> DOWN_RIGHT;
        };
        boolean horizontal = (mask & horizontalBit) != 0;
        boolean vertical = (mask & verticalBit) != 0;
        if (!horizontal && !vertical) {
            return Tile.base(cornerX, cornerY);
        }
        if (horizontal && vertical && (mask & diagonalBit) != 0) {
            return Tile.ctm(cornerX, cornerY);
        }
        // GregTech's X4 atlas is indexed row-first: horizontal connections
        // select the lower rows, while vertical connections select the right columns.
        return Tile.ctm(cornerX + (vertical ? 2 : 0),
                cornerY + (horizontal ? 2 : 0));
    }

    public record Tile(boolean baseTexture, int x, int y) {
        private static Tile base(int x, int y) {
            return new Tile(true, x, y);
        }

        private static Tile ctm(int x, int y) {
            return new Tile(false, x, y);
        }
    }
}
