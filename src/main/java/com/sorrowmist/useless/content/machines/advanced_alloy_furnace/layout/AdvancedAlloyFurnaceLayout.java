package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout;

/**
 * 高级合金炉的槽位和流体槽布局定义。
 * 该类作为机器布局的单一数据源，避免槽位编号散落在不同模块中。
 */
public final class AdvancedAlloyFurnaceLayout {
    public static final int INPUT_SLOTS_START = 0;
    public static final int INPUT_SLOTS_COUNT = 9;
    public static final int OUTPUT_SLOTS_START = 9;
    public static final int OUTPUT_SLOTS_COUNT = 9;
    public static final int CATALYST_SLOT = 18;
    public static final int MOLD_SLOT = 19;
    public static final int PATTERN_SLOTS_START = 20;
    public static final int PATTERN_SLOTS_COUNT = 108;
    public static final int PATTERN_SLOTS_END = PATTERN_SLOTS_START + PATTERN_SLOTS_COUNT - 1;
    public static final int TOTAL_SLOTS = 128;
    public static final int FLUID_TANK_COUNT = 6;

    private AdvancedAlloyFurnaceLayout() {
    }
}
