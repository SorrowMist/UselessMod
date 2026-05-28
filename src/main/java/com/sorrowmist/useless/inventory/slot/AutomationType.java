package com.sorrowmist.useless.inventory.slot;

/**
 * 自动化类型枚举，参考 Mekanism 的 AutomationType
 * 用于区分不同的交互方式
 */
public enum AutomationType {
    /**
     * 外部自动化（管道、漏斗等）
     */
    EXTERNAL,
    /**
     * 手动操作（玩家点击）
     */
    MANUAL,
    /**
     * 内部自动化（机器内部传输）
     */
    INTERNAL;

    /**
     * 检查是否是外部自动化
     */
    public boolean isExternal() {
        return this == EXTERNAL;
    }

    /**
     * 检查是否是手动操作
     */
    public boolean isManual() {
        return this == MANUAL;
    }

    /**
     * 检查是否是内部自动化
     */
    public boolean isInternal() {
        return this == INTERNAL;
    }
}
