package com.sorrowmist.useless.api.enums;

import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * 高级合金炉的6个逻辑面（相对于方块朝向）。
 * <p>
 * 用于将GUI中的面配置映射到世界中的实际方向，映射结果随方块水平朝向变化。
 */
public enum FurnaceFace {
    TOP,
    LEFT,
    FRONT,
    RIGHT,
    BOTTOM,
    BACK;

    public static final int COUNT = values().length;

    /**
     * 根据方块的水平朝向，将逻辑面转换为世界方向。
     *
     * @param facing 方块的水平朝向（FRONT所指方向）
     * @return 世界方向
     */
    public Direction toDirection(Direction facing) {
        return switch (this) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
        };
    }

    /**
     * 根据方块的水平朝向，将世界方向转换为逻辑面。
     *
     * @param side   世界方向
     * @param facing 方块的水平朝向（FRONT所指方向）
     * @return 对应的逻辑面，无法匹配时返回null
     */
    @Nullable
    public static FurnaceFace fromDirection(Direction side, Direction facing) {
        if (side == Direction.UP) return TOP;
        if (side == Direction.DOWN) return BOTTOM;
        if (side == facing) return FRONT;
        if (side == facing.getOpposite()) return BACK;
        if (side == facing.getClockWise()) return LEFT;
        if (side == facing.getCounterClockWise()) return RIGHT;
        return null;
    }
}
