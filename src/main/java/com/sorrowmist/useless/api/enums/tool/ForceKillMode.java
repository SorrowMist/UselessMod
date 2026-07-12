package com.sorrowmist.useless.api.enums.tool;

import net.minecraft.network.chat.Component;

public enum ForceKillMode {
    DAMAGE_ONLY("damage_only", "tooltip.useless_mod.force_kill_mode.damage_only"),
    DIE("die", "tooltip.useless_mod.force_kill_mode.die"),
    KILL("kill", "tooltip.useless_mod.force_kill_mode.kill"),
    REMOVE("remove", "tooltip.useless_mod.force_kill_mode.remove");

    private final String name;
    private final String tooltipKey;

    ForceKillMode(String name, String tooltipKey) {
        this.name = name;
        this.tooltipKey = tooltipKey;
    }

    public String getName() {return this.name;}

    public Component getTooltip() {return Component.translatable(this.tooltipKey);}
}
