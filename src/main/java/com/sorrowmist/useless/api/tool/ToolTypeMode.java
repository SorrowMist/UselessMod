package com.sorrowmist.useless.api.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum ToolTypeMode {
    NONE_MODE("none_mode", "tooltip.useless_mod.none_mode"),
    WRENCH_MODE("wrench_mode", "tooltip.useless_mod.wrench_mode"),
    SCREWDRIVER_MODE("screwdriver_mode", "tooltip.useless_mod.screwdriver_mode"),
    MALLET_MODE("mallet_mode", "tooltip.useless_mod.mallet_mode"),
    CROWBAR_MODE("crowbar_mode", "tooltip.useless_mod.crowbar_mode"),
    HAMMER_MODE("hammer_mode", "tooltip.useless_mod.hammer_mode"),
    OMNITOOL_MODE("omnitool_mode", "tooltip.useless_mod.omnitool_mode");

    private final String name;
    private final String tooltipKey;

    ToolTypeMode(String name, String tooltipKey) {
        this.name = name;
        this.tooltipKey = tooltipKey;
    }

    public static int getTotal() {return values().length;}

    public String getName() {return this.name;}

    public Component getTooltip() {return Component.translatable(this.tooltipKey);}

    public Component getTooltip(ChatFormatting... styles) {
        return Component.translatable(this.tooltipKey).withStyle(styles);
    }
}