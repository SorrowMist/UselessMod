package com.sorrowmist.useless.api.tool;

import net.minecraft.network.chat.Component;

public enum EnchantMode {
    SILK_TOUCH("silk_touch", "tooltip.useless_mod.silk_touch_mode"),
    FORTUNE("fortune", "tooltip.useless_mod.fortune_mode");

    private final String name;
    private final String tooltipKey;

    EnchantMode(String name, String tooltipKey) {
        this.name = name;
        this.tooltipKey = tooltipKey;
    }

    public static int getTotal() {return values().length;}

    public String getName() {return this.name;}

    public Component getTooltip() {return Component.translatable(this.tooltipKey);}
}