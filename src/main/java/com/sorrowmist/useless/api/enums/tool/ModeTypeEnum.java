package com.sorrowmist.useless.api.enums.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum ModeTypeEnum {
    // 增强连锁挖矿模式开关（true=启用增强连锁，false=使用普通连锁）
    ENHANCED_CHAIN_MINING_ENABLED("enhanced_chain_mining_enabled", "tooltip.useless_mod.enhanced_chain_mining_mode"),
    ENHANCED_CHAIN_MINING_DISABLED("enhanced_chain_mining_disabled", "tooltip.useless_mod.enhanced_chain_mining_mode"),
    
    // 强制挖掘模式
    FORCE_MINING_ENABLED("force_mining_enabled", "tooltip.useless_mod.force_mining_mode"),
    FORCE_MINING_DISABLED("force_mining_disabled", "tooltip.useless_mod.force_mining_mode"),
    
    // AE存储优先模式
    AE_STORAGE_PRIORITY_ENABLED("ae_storage_priority_enabled", "tooltip.useless_mod.ae_storage_priority_mode"),
    AE_STORAGE_PRIORITY_DISABLED("ae_storage_priority_disabled", "tooltip.useless_mod.ae_storage_priority_mode"),

    FORCE_KILL("force_kill", "tooltip.useless_mod.force_kill_enabled_mode"),

    BEEF_INVULNERABILITY_ENABLED("beef_invulnerability_enabled", "tooltip.useless_mod.beef_invulnerability_mode"),
    BEEF_INVULNERABILITY_DISABLED("beef_invulnerability_disabled", "tooltip.useless_mod.beef_invulnerability_mode"),

    BEEF_CAPTURE_ENABLED("beef_capture_enabled", "tooltip.useless_mod.beef_capture_mode"),
    BEEF_CAPTURE_DISABLED("beef_capture_disabled", "tooltip.useless_mod.beef_capture_mode");

    private final String name;
    private final String tooltipKey;

    ModeTypeEnum(String name, String tooltipKey) {
        this.name = name;
        this.tooltipKey = tooltipKey;
    }

    public static int getTotal() {return values().length;}

    // 辅助方法：根据模式类型和状态获取对应的枚举值
    public static ModeTypeEnum getEnhancedChainMiningMode(boolean enabled) {
        return enabled ? ENHANCED_CHAIN_MINING_ENABLED : ENHANCED_CHAIN_MINING_DISABLED;
    }

    public static ModeTypeEnum getForceMiningMode(boolean enabled) {
        return enabled ? FORCE_MINING_ENABLED : FORCE_MINING_DISABLED;
    }

    public static ModeTypeEnum getAEStoragePriorityMode(boolean enabled) {
        return enabled ? AE_STORAGE_PRIORITY_ENABLED : AE_STORAGE_PRIORITY_DISABLED;
    }

    public static ModeTypeEnum getBeefInvulnerabilityMode(boolean enabled) {
        return enabled ? BEEF_INVULNERABILITY_ENABLED : BEEF_INVULNERABILITY_DISABLED;
    }

    public static ModeTypeEnum getBeefCaptureMode(boolean enabled) {
        return enabled ? BEEF_CAPTURE_ENABLED : BEEF_CAPTURE_DISABLED;
    }
    
    public String getName() {return this.name;}
    
    public Component getTooltip() {return Component.translatable(this.tooltipKey);}
    
    public Component getStatusComponent(boolean active) {
        return Component.translatable(
                active ? "tooltip.useless_mod.enable" : "tooltip.useless_mod.disable"
        ).withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
}
