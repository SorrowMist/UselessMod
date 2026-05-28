package com.sorrowmist.useless.inventory.slot;

/**
 * 操作类型枚举，参考 Mekanism 的 Action
 * 用于区分模拟操作和实际执行操作
 */
public enum Action {
    /**
     * 执行实际操作
     */
    EXECUTE,
    /**
     * 仅模拟操作，不实际改变状态
     */
    SIMULATE;

    /**
     * 检查是否应该执行操作
     */
    public boolean execute() {
        return this == EXECUTE;
    }

    /**
     * 检查是否只是模拟
     */
    public boolean simulate() {
        return this == SIMULATE;
    }

    /**
     * 根据条件组合操作
     *
     * @param condition 条件
     * @return 如果条件为真则返回 EXECUTE，否则返回 SIMULATE
     */
    public Action combine(boolean condition) {
        return condition ? this : SIMULATE;
    }
}
