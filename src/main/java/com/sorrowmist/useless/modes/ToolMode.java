package com.sorrowmist.useless.modes;

import net.minecraft.network.chat.Component;

/**
 * 工具模式枚举，定义所有可用的工具模式
 */
public enum ToolMode {
    // 精准采集模式
    SILK_TOUCH("silk_touch", "tooltip.useless_mod.silk_touch_mode", 0),
    // 时运模式
    FORTUNE("fortune", "tooltip.useless_mod.fortune_mode", 1),
    // 连锁挖掘模式
    CHAIN_MINING("chain_mining", "tooltip.useless_mod.chain_mining_mode", 2),
    // 增强连锁挖掘模式
    ENHANCED_CHAIN_MINING("enhanced_chain_mining", "tooltip.useless_mod.enhanced_chain_mining_mode", 3),
    // 扳手模式
    WRENCH_MODE("wrench_mode", "tooltip.useless_mod.wrench_mode", 4),
    // 螺丝刀模式
    SCREWDRIVER_MODE("screwdriver_mode", "tooltip.useless_mod.screwdriver_mode", 5),
    // 锤子模式
    MALLET_MODE("mallet_mode", "tooltip.useless_mod.mallet_mode", 6),
    // 撬棍模式
    CROWBAR_MODE("crowbar_mode", "tooltip.useless_mod.crowbar_mode", 7),
    // 铁锤模式
    HAMMER_MODE("hammer_mode", "tooltip.useless_mod.hammer_mode", 8),
    // Mekanism配置器模式
    MEK_CONFIGURATOR("mek_configurator", "tooltip.useless_mod.mek_configurator_mode", 9),
    // 强制挖掘模式
    FORCE_MINING("force_mining", "tooltip.useless_mod.force_mining_mode", 10);
    
    private final String name;
    private final String tooltipKey;
    private final int index;
    
    ToolMode(String name, String tooltipKey, int index) {
        this.name = name;
        this.tooltipKey = tooltipKey;
        this.index = index;
    }
    
    /**
     * 获取模式的名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取模式的索引
     */
    public int getIndex() {
        return index;
    }
    
    /**
     * 获取模式的 tooltip 文本
     */
    public Component getTooltip() {
        return Component.translatable(tooltipKey);
    }
    
    /**
     * 通过索引获取模式
     */
    public static ToolMode byIndex(int index) {
        for (ToolMode mode : values()) {
            if (mode.index == index) {
                return mode;
            }
        }
        return SILK_TOUCH; // 默认返回精准采集模式
    }
    
    /**
     * 获取模式的总数
     */
    public static int getTotalModes() {
        return values().length;
    }
}