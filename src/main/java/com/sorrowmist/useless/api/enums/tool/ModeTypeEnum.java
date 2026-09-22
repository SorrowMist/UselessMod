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

    // 自动熔炼：true = 挖掘掉落物按熔炉配方熔炼后再入库
    AUTO_SMELT_ENABLED("auto_smelt_enabled", "tooltip.useless_mod.auto_smelt_mode"),
    AUTO_SMELT_DISABLED("auto_smelt_disabled", "tooltip.useless_mod.auto_smelt_mode"),
    
    // AE存储优先模式
    AE_STORAGE_PRIORITY_ENABLED("ae_storage_priority_enabled", "tooltip.useless_mod.ae_storage_priority_mode"),
    AE_STORAGE_PRIORITY_DISABLED("ae_storage_priority_disabled", "tooltip.useless_mod.ae_storage_priority_mode"),

    // AE 连接模式：右键有 AE 节点的机器，把它接入工具绑定的那张网
    AE_NETWORK_CONNECT_ENABLED("ae_network_connect_enabled", "tooltip.useless_mod.ae_network_connect_mode"),
    AE_NETWORK_CONNECT_DISABLED("ae_network_connect_disabled", "tooltip.useless_mod.ae_network_connect_mode"),

    WRENCH_TAG_ENABLED("wrench_tag_enabled", "tooltip.useless_mod.wrench_tag_mode"),
    WRENCH_TAG_DISABLED("wrench_tag_disabled", "tooltip.useless_mod.wrench_tag_mode"),

    CONSTRUCTION_WAND_ENABLED("construction_wand_enabled", "tooltip.useless_mod.construction_wand_mode"),
    CONSTRUCTION_WAND_DISABLED("construction_wand_disabled", "tooltip.useless_mod.construction_wand_mode"),
    CONSTRUCTION_WAND_ANGEL_CORE("construction_wand_angel_core", "tooltip.useless_mod.construction_wand_angel_core"),
    CONSTRUCTION_WAND_DESTRUCTION_CORE("construction_wand_destruction_core", "tooltip.useless_mod.construction_wand_destruction_core"),

    FORCE_KILL("force_kill", "tooltip.useless_mod.force_kill_enabled_mode"),

    BEEF_MALUM_SPIRIT_ENABLED("beef_malum_spirit_enabled", "tooltip.useless_mod.beef_malum_spirit_mode"),
    BEEF_MALUM_SPIRIT_DISABLED("beef_malum_spirit_disabled", "tooltip.useless_mod.beef_malum_spirit_mode"),

    BEEF_MYSTICAL_AGRICULTURE_ENABLED("beef_mystical_agriculture_enabled",
            "tooltip.useless_mod.beef_mystical_agriculture_mode"),
    BEEF_MYSTICAL_AGRICULTURE_DISABLED("beef_mystical_agriculture_disabled",
            "tooltip.useless_mod.beef_mystical_agriculture_mode"),

    BEEF_BEHEADING_ENABLED("beef_beheading_enabled", "tooltip.useless_mod.beef_beheading_mode"),
    BEEF_BEHEADING_DISABLED("beef_beheading_disabled", "tooltip.useless_mod.beef_beheading_mode"),

    BEEF_TIME_ACCELERATION_ENABLED("beef_time_acceleration_enabled", "tooltip.useless_mod.time_acceleration_mode"),
    BEEF_TIME_ACCELERATION_DISABLED("beef_time_acceleration_disabled", "tooltip.useless_mod.time_acceleration_mode"),

    BEEF_INVULNERABILITY_ENABLED("beef_invulnerability_enabled", "tooltip.useless_mod.beef_invulnerability_mode"),
    BEEF_INVULNERABILITY_DISABLED("beef_invulnerability_disabled", "tooltip.useless_mod.beef_invulnerability_mode"),

    BEEF_CAPTURE_ENABLED("beef_capture_enabled", "tooltip.useless_mod.beef_capture_mode"),
    BEEF_CAPTURE_DISABLED("beef_capture_disabled", "tooltip.useless_mod.beef_capture_mode"),

    BEEF_TELEPORT_ENABLED("beef_teleport_enabled", "tooltip.useless_mod.beef_teleport_mode"),
    BEEF_TELEPORT_DISABLED("beef_teleport_disabled", "tooltip.useless_mod.beef_teleport_mode"),

    BEEF_AOE_DAMAGE_ENABLED("beef_aoe_damage_enabled", "tooltip.useless_mod.beef_aoe_damage_mode"),
    BEEF_AOE_DAMAGE_DISABLED("beef_aoe_damage_disabled", "tooltip.useless_mod.beef_aoe_damage_mode"),

    BEEF_MAGNET_ENABLED("beef_magnet_enabled", "tooltip.useless_mod.beef_magnet_mode"),
    BEEF_MAGNET_DISABLED("beef_magnet_disabled", "tooltip.useless_mod.beef_magnet_mode"),

    BEEF_ADVANCED_STEALTH_ENABLED("beef_advanced_stealth_enabled", "tooltip.useless_mod.beef_advanced_stealth_mode"),
    BEEF_ADVANCED_STEALTH_DISABLED("beef_advanced_stealth_disabled", "tooltip.useless_mod.beef_advanced_stealth_mode"),

    // 土壤右键模式：true = 锄头优先（耕地），false = 铲子优先（草径）
    BEEF_FARMLAND_MODE_ENABLED("beef_farmland_mode_enabled", "tooltip.useless_mod.beef_farmland_mode"),
    BEEF_FARMLAND_MODE_DISABLED("beef_farmland_mode_disabled", "tooltip.useless_mod.beef_farmland_mode"),

    // 顺手收菜：true = 右键成熟作物时收菜并保留种子在地里
    BEEF_CROP_HARVEST_ENABLED("beef_crop_harvest_enabled", "tooltip.useless_mod.beef_crop_harvest_mode"),
    BEEF_CROP_HARVEST_DISABLED("beef_crop_harvest_disabled", "tooltip.useless_mod.beef_crop_harvest_mode"),

    // 剪刀功能：true = 造化杖可剪羊毛/剪掉落，并对外声明剪刀能力
    BEEF_SHEARS_ENABLED("beef_shears_enabled", "tooltip.useless_mod.beef_shears_mode"),
    BEEF_SHEARS_DISABLED("beef_shears_disabled", "tooltip.useless_mod.beef_shears_mode"),

    // 打火石功能：true = 右键可点燃营火/蜡烛，或在可点火位置放火
    BEEF_FLINT_AND_STEEL_ENABLED("beef_flint_and_steel_enabled", "tooltip.useless_mod.beef_flint_and_steel_mode"),
    BEEF_FLINT_AND_STEEL_DISABLED("beef_flint_and_steel_disabled", "tooltip.useless_mod.beef_flint_and_steel_mode"),

    // 匠心仪式挎包：true = 右键魔典预览的五芒星，从绑定的 AE 网络取方块摆出整座仪式
    BEEF_RITUAL_SATCHEL_ENABLED("beef_ritual_satchel_enabled", "tooltip.useless_mod.beef_ritual_satchel_mode"),
    BEEF_RITUAL_SATCHEL_DISABLED("beef_ritual_satchel_disabled", "tooltip.useless_mod.beef_ritual_satchel_mode");

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

    public static ModeTypeEnum getAutoSmeltMode(boolean enabled) {
        return enabled ? AUTO_SMELT_ENABLED : AUTO_SMELT_DISABLED;
    }

    public static ModeTypeEnum getAEStoragePriorityMode(boolean enabled) {
        return enabled ? AE_STORAGE_PRIORITY_ENABLED : AE_STORAGE_PRIORITY_DISABLED;
    }

    public static ModeTypeEnum getAeNetworkConnectMode(boolean enabled) {
        return enabled ? AE_NETWORK_CONNECT_ENABLED : AE_NETWORK_CONNECT_DISABLED;
    }

    public static ModeTypeEnum getWrenchTagMode(boolean enabled) {
        return enabled ? WRENCH_TAG_ENABLED : WRENCH_TAG_DISABLED;
    }

    public static ModeTypeEnum getConstructionWandMode(boolean enabled) {
        return enabled ? CONSTRUCTION_WAND_ENABLED : CONSTRUCTION_WAND_DISABLED;
    }

    public static ModeTypeEnum getBeefInvulnerabilityMode(boolean enabled) {
        return enabled ? BEEF_INVULNERABILITY_ENABLED : BEEF_INVULNERABILITY_DISABLED;
    }

    public static ModeTypeEnum getBeefTimeAccelerationMode(boolean enabled) {
        return enabled ? BEEF_TIME_ACCELERATION_ENABLED : BEEF_TIME_ACCELERATION_DISABLED;
    }

    public static ModeTypeEnum getBeefMalumSpiritMode(boolean enabled) {
        return enabled ? BEEF_MALUM_SPIRIT_ENABLED : BEEF_MALUM_SPIRIT_DISABLED;
    }

    public static ModeTypeEnum getBeefMysticalAgricultureMode(boolean enabled) {
        return enabled ? BEEF_MYSTICAL_AGRICULTURE_ENABLED : BEEF_MYSTICAL_AGRICULTURE_DISABLED;
    }

    public static ModeTypeEnum getBeefBeheadingMode(boolean enabled) {
        return enabled ? BEEF_BEHEADING_ENABLED : BEEF_BEHEADING_DISABLED;
    }

    public static ModeTypeEnum getBeefCaptureMode(boolean enabled) {
        return enabled ? BEEF_CAPTURE_ENABLED : BEEF_CAPTURE_DISABLED;
    }

    public static ModeTypeEnum getBeefTeleportMode(boolean enabled) {
        return enabled ? BEEF_TELEPORT_ENABLED : BEEF_TELEPORT_DISABLED;
    }
    
    public static ModeTypeEnum getBeefAoeDamageMode(boolean enabled) {
        return enabled ? BEEF_AOE_DAMAGE_ENABLED : BEEF_AOE_DAMAGE_DISABLED;
    }

    public static ModeTypeEnum getBeefMagnetMode(boolean enabled) {
        return enabled ? BEEF_MAGNET_ENABLED : BEEF_MAGNET_DISABLED;
    }

    public static ModeTypeEnum getBeefAdvancedStealthMode(boolean enabled) {
        return enabled ? BEEF_ADVANCED_STEALTH_ENABLED : BEEF_ADVANCED_STEALTH_DISABLED;
    }

    public static ModeTypeEnum getBeefFarmlandMode(boolean enabled) {
        return enabled ? BEEF_FARMLAND_MODE_ENABLED : BEEF_FARMLAND_MODE_DISABLED;
    }

    public static ModeTypeEnum getBeefCropHarvestMode(boolean enabled) {
        return enabled ? BEEF_CROP_HARVEST_ENABLED : BEEF_CROP_HARVEST_DISABLED;
    }

    public static ModeTypeEnum getBeefShearsMode(boolean enabled) {
        return enabled ? BEEF_SHEARS_ENABLED : BEEF_SHEARS_DISABLED;
    }

    public static ModeTypeEnum getBeefFlintAndSteelMode(boolean enabled) {
        return enabled ? BEEF_FLINT_AND_STEEL_ENABLED : BEEF_FLINT_AND_STEEL_DISABLED;
    }

    public static ModeTypeEnum getBeefRitualSatchelMode(boolean enabled) {
        return enabled ? BEEF_RITUAL_SATCHEL_ENABLED : BEEF_RITUAL_SATCHEL_DISABLED;
    }

    public String getName() {return this.name;}
    
    public Component getTooltip() {return Component.translatable(this.tooltipKey);}
    
    public Component getStatusComponent(boolean active) {
        return Component.translatable(
                active ? "tooltip.useless_mod.enable" : "tooltip.useless_mod.disable"
        ).withStyle(active ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
}
