package com.sorrowmist.useless.api.enums.tool;

import net.minecraft.network.chat.Component;

public enum ConstructionWandCoreMode {
    DEFAULT("tooltip.useless_mod.construction_wand_default_core"),
    ANGEL("tooltip.useless_mod.construction_wand_angel_core"),
    DESTRUCTION("tooltip.useless_mod.construction_wand_destruction_core");

    private final String tooltipKey;

    ConstructionWandCoreMode(String tooltipKey) {
        this.tooltipKey = tooltipKey;
    }

    public Component getTooltip() {
        return Component.translatable(this.tooltipKey);
    }
}
