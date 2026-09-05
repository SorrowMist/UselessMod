package com.sorrowmist.useless.core.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
    // 植物盆生长速度配置
    private static final ModConfigSpec.IntValue BOTANY_POT_GROWTH_MULTIPLIER;
    // 植物盆渲染配置
    private static final ModConfigSpec.BooleanValue ENABLE_BOTANY_POT_RENDERING;

    // 矩阵样板数量配置
    private static final ModConfigSpec.IntValue MATRIX_PATTERN_COUNT;

    // 药水效果配置
    private static final ModConfigSpec.BooleanValue ENABLE_POTION_EFFECTS;
    private static final ModConfigSpec.BooleanValue ENABLE_FLIGHT_EFFECT;
    
    // 自定义药水效果配置 - 每个列表条目格式: "modid:effect_name,amplifier"
    // 持续时间固定为20000 tick, 等级从1开始计算
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_POTION_EFFECTS;

    // 牛排工具连锁挖掘配置
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_X;
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_Y;
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_Z;
    private static final ModConfigSpec.IntValue CHAIN_MINING_MAX_BLOCKS;
    // 连锁挖掘等价组：命中同一条目的方块视为同类
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CHAIN_MINING_EQUIVALENT_GROUPS;
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_FLIGHT_SPEED;
    // 牛排工具附魔等级配置
    private static final ModConfigSpec.IntValue FORTUNE_LEVEL;
    private static final ModConfigSpec.IntValue LOOTING_LEVEL;
    // 牛排工具挖掘速度配置
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_MINING_SPEED;
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_ENTITY_INTERACTION_RANGE;
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_BLOCK_INTERACTION_RANGE;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BEEF_TOOL_FORCE_MINING_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<String> BEEF_TOOL_FORCE_KILL_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<String> BEEF_TOOL_FORCE_KILL_NON_LIVING_WHITELIST;
    // 牛排工具范围伤害配置（半径）
    private static final ModConfigSpec.IntValue BEEF_AOE_DAMAGE_RANGE_X;
    private static final ModConfigSpec.IntValue BEEF_AOE_DAMAGE_RANGE_Y;
    private static final ModConfigSpec.IntValue BEEF_AOE_DAMAGE_RANGE_Z;
    private static final ModConfigSpec.IntValue BEEF_AOE_DAMAGE_MAX_TARGETS;
    // 牛排工具范围磁力吸附配置（半径）
    private static final ModConfigSpec.IntValue BEEF_MAGNET_RANGE_X;
    private static final ModConfigSpec.IntValue BEEF_MAGNET_RANGE_Y;
    private static final ModConfigSpec.IntValue BEEF_MAGNET_RANGE_Z;

    // 战利品大爆发触发概率配置 (百分比)
    private static final ModConfigSpec.IntValue FESTIVE_DROP_CHANCE;

    // Mekanism 升级配置
    private static final ModConfigSpec.IntValue TIME_MULTIPLIER;
    private static final ModConfigSpec.IntValue ELECTRICITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue CAPACITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue MAX_UPGRADE;

    // 万象炉从AE网络抽取能量配置
    private static final ModConfigSpec.BooleanValue FURNACE_DRAW_APPFLUX_ENERGY;
    private static final ModConfigSpec.BooleanValue FURNACE_DRAW_AE_ENERGY;

    // 万象炉配方转换配置
    private static final ModConfigSpec.BooleanValue ENABLE_CRAFTING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_SMELTING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_BREWING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_FARMERS_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_EXTRA_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_CRABBERS_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_CASUALNESS_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_EXPANDED_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_BREWIN_AND_CHEWIN_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_NOMADS_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_UBES_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_BARBEQUES_DELIGHT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_YOUKAI_HOMECOMING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_KALEIDOSCOPE_COOKERY_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_KALEIDOSCOPE_GRILLING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_KALEIDOSCOPE_TAVERN_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_EXTENDED_AE_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_ADVANCED_AE_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_MEKANISM_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_MEKANISM_GENERATORS_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_APP_MEK_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_AE2_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_AE2CS_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_INDUSTRIAL_FOREGOING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_ACTUALLY_ADDITIONS_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_ARS_NOUVEAU_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_MYSTICAL_AGRICULTURE_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_AE2LT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_DATA_ENERGISTICS_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_PRODUCTIVE_BEES_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_DRACONIC_EVOLUTION_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_POWAH_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_EXTENDED_CRAFTING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_AVARITIA_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_NEO_ECO_AE_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_NATURES_AURA_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_FORBIDDEN_ARCANUS_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_OCCULTISM_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_MALUM_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_ENDER_IO_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_CREATE_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_ORITECH_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_NEOVITAE_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_UFO_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_MODERN_INDUSTRIALIZATION_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_IMMERSIVE_ENGINEERING_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_PNEUMATICCRAFT_RECIPE_CONVERSION;
    private static final ModConfigSpec.BooleanValue ENABLE_BIG_REACTORS_RECIPE_CONVERSION;
    private static final Map<String, ModConfigSpec.BooleanValue> RECIPE_CONVERSION_OPTIONS;

    private static final ModConfigSpec.IntValue OMNIVERSAL_PATTERN_SLOTS;
    private static final ModConfigSpec.IntValue OMNIVERSAL_MOLD_SLOTS;
    private static final ModConfigSpec.IntValue OMNIVERSAL_PASSIVE_PATTERN_SLOTS;
    private static final ModConfigSpec.IntValue OMNIVERSAL_DECODE_CACHE_CAPACITY;
    private static final ModConfigSpec.IntValue ORE_GENERATOR_SLOTS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>>
            USELESS_DIMENSION_FLOOR_BLOCK_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>>
            USELESS_DIMENSION_FLOOR_BLOCK_WHITELIST;
    private static final ModConfigSpec.ConfigValue<List<? extends String>>
            AE2_GIFT_PACKAGE_ITEMS;
    private static final String DIMENSION_FLOOR_BLACKLIST_NAME = "Useless Dimension floor block blacklist";
    private static final String DIMENSION_FLOOR_WHITELIST_NAME = "Useless Dimension floor block whitelist";
    private static volatile List<String> cachedUselessDimensionFloorBlockBlacklist = List.of();
    private static volatile BlockBlacklistMatcher uselessDimensionFloorBlockBlacklistMatcher =
            BlockBlacklistMatcher.empty(DIMENSION_FLOOR_BLACKLIST_NAME);
    private static volatile List<String> cachedUselessDimensionFloorBlockWhitelist = List.of();
    private static volatile BlockBlacklistMatcher uselessDimensionFloorBlockWhitelistMatcher =
            BlockBlacklistMatcher.empty(DIMENSION_FLOOR_WHITELIST_NAME);
    private static volatile List<String> cachedBeefToolForceMiningBlacklist = List.of();
    private static volatile BlockBlacklistMatcher beefToolForceMiningBlacklistMatcher =
            BlockBlacklistMatcher.empty("beef tool force mining blacklist");
    private static volatile List<String> cachedChainMiningEquivalentGroups = List.of();
    private static volatile ChainMatchGroups chainMiningEquivalentGroups = ChainMatchGroups.empty();

    static {
        // Server config: these values change world or machine behavior.
        SERVER_BUILDER.push("game_mechanics");
        BOTANY_POT_GROWTH_MULTIPLIER = SERVER_BUILDER
                .comment("植物盆生长倍率 - 1.0为原版速度, 2.0为2倍速度")
                .defineInRange("botany_pot_growth_multiplier", 1, 1, Integer.MAX_VALUE);

        MATRIX_PATTERN_COUNT = SERVER_BUILDER
                .comment("矩阵样板槽位倍数 - 减少数量时请保持槽位空！否则可能会造成样板丢失")
                .defineInRange("matrix_pattern_count", 1, 1, 100);
        SERVER_BUILDER.pop();

        // Client config: rendering-only options must never affect server behavior.
        CLIENT_BUILDER.push("game_mechanics");
        ENABLE_BOTANY_POT_RENDERING = CLIENT_BUILDER
                .comment("是否启用植物盆作物渲染")
                .define("enable_botany_pot_rendering", true);
        CLIENT_BUILDER.pop();

        SERVER_BUILDER.translation("useless_mod.configuration.omniversal_multiblock_alloy_furnace")
                .push("omniversal_multiblock_alloy_furnace");
        OMNIVERSAL_PATTERN_SLOTS = SERVER_BUILDER
                .comment("ME pattern assembly slots. Values are normalized to pages of 27.")
                .translation("useless_mod.configuration.pattern_slots")
                .defineInRange("pattern_slots", 108, 27, 540);
        OMNIVERSAL_MOLD_SLOTS = SERVER_BUILDER
                .comment("Omniversal mold hub slots. Values are normalized to pages of 27.")
                .translation("useless_mod.configuration.mold_slots")
                .defineInRange("mold_slots", 108, 27, 540);
        OMNIVERSAL_PASSIVE_PATTERN_SLOTS = SERVER_BUILDER
                .comment("Passive crafting hatch slots. Higher coil tiers unlock this capacity gradually.")
                .translation("useless_mod.configuration.passive_pattern_slots")
                .defineInRange("passive_pattern_slots", 30, 1, 540);
        OMNIVERSAL_DECODE_CACHE_CAPACITY = SERVER_BUILDER
                .comment("Maximum decoded omniversal pattern entries kept per level. Takes effect after restart.")
                .translation("useless_mod.configuration.decode_cache_capacity")
                .defineInRange("decode_cache_capacity", 2048, 64, 16384);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.translation("useless_mod.configuration.ore_generator")
                .push("ore_generator");
        ORE_GENERATOR_SLOTS = SERVER_BUILDER
                .comment("Ore generator sample slots. Slots above this value remain recovery-only.")
                .translation("useless_mod.configuration.ore_generator_slots")
                .defineInRange("ore_generator_slots", 9, 1, 540);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.translation("useless_mod.configuration.ae2_gift_package")
                .push("ae2_gift_package");
        AE2_GIFT_PACKAGE_ITEMS = SERVER_BUILDER
                .comment("Items granted by the AE2 gift package. Format: modid:item_id,count.",
                        "Missing items and entries with invalid quantities are skipped.")
                .translation("useless_mod.configuration.ae2_gift_package.items")
                .defineListAllowEmpty("items", defaultAE2GiftPackageItems(), () -> "",
                        ConfigManager::isValidAE2GiftPackageEntry);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.translation("useless_mod.configuration.beef_tool")
                .push("beef_tool");
        CUSTOM_POTION_EFFECTS = SERVER_BUILDER
                .comment("Custom potion effects. Format: modid:effect_id,amplifier.",
                        "Use one effect per list entry. Missing effects and invalid levels are skipped.")
                .translation("useless_mod.configuration.custom_potion_effects")
                .defineListAllowEmpty("custom_potion_effects", defaultCustomPotionEffects(), () -> "",
                        ConfigManager::isValidCustomPotionEffectEntry);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.translation("useless_mod.configuration.useless_dimension")
                .push("useless_dimension");
        USELESS_DIMENSION_FLOOR_BLOCK_BLACKLIST = SERVER_BUILDER
                .comment("Blocks that cannot be used for Useless Dimension borders, fills, or centers",
                        "A block matching this list remains blocked even if it matches the whitelist",
                        "Use exact block IDs, #block tags, or * wildcard patterns")
                .translation("useless_mod.configuration.useless_dimension_floor_block_blacklist")
                .defineListAllowEmpty("floor_block_blacklist", List.<String>of(), () -> "",
                        entry -> entry instanceof String);
        USELESS_DIMENSION_FLOOR_BLOCK_WHITELIST = SERVER_BUILDER
                .comment("When non-empty, only matching blocks can be used for Useless Dimension borders, fills, or centers",
                        "Leave empty to allow every block that is not on the blacklist",
                        "Use exact block IDs, #block tags, or * wildcard patterns")
                .translation("useless_mod.configuration.useless_dimension_floor_block_whitelist")
                .defineListAllowEmpty("floor_block_whitelist", List.<String>of(), () -> "",
                        entry -> entry instanceof String);
        SERVER_BUILDER.pop();

        // 牛排工具连锁挖掘配置
        // Server config: gameplay options are authoritative on the logical server.
        SERVER_BUILDER.translation("useless_mod.configuration.beef_tool").push("beef_tool");
        ENABLE_POTION_EFFECTS = SERVER_BUILDER
                .comment("是否启用药水效果")
                .translation("useless_mod.configuration.enable_potion_effects")
                .define("enable_potion_effects", true);

        ENABLE_FLIGHT_EFFECT = SERVER_BUILDER
                .comment("是否启用飞行效果")
                .translation("useless_mod.configuration.enable_flight_effect")
                .define("enable_flight_effect", true);

        BEEF_TOOL_FLIGHT_SPEED = SERVER_BUILDER
                .comment("牛排工具飞行速度")
                .translation("useless_mod.configuration.beef_tool_flight_speed")
                .defineInRange("beef_tool_flight_speed", 0.05, 0.01, 1.0);

        CHAIN_MINING_RANGE_X = SERVER_BUILDER
                .comment("连锁挖掘的X轴范围半径")
                .translation("useless_mod.configuration.chain_mining_range_x")
                .defineInRange("chain_mining_range_x", 8, 1, 32);

        CHAIN_MINING_RANGE_Y = SERVER_BUILDER
                .comment("连锁挖掘的Y轴范围半径")
                .translation("useless_mod.configuration.chain_mining_range_y")
                .defineInRange("chain_mining_range_y", 8, 1, 255);

        CHAIN_MINING_RANGE_Z = SERVER_BUILDER
                .comment("连锁挖掘的Z轴范围半径")
                .translation("useless_mod.configuration.chain_mining_range_z")
                .defineInRange("chain_mining_range_z", 8, 1, 32);

        BEEF_AOE_DAMAGE_RANGE_X = SERVER_BUILDER
                .comment("范围伤害的X轴范围半径")
                .translation("useless_mod.configuration.beef_aoe_damage_range_x")
                .defineInRange("beef_aoe_damage_range_x", 5, 1, 64);

        BEEF_AOE_DAMAGE_RANGE_Y = SERVER_BUILDER
                .comment("范围伤害的Y轴范围半径")
                .translation("useless_mod.configuration.beef_aoe_damage_range_y")
                .defineInRange("beef_aoe_damage_range_y", 3, 1, 64);

        BEEF_AOE_DAMAGE_RANGE_Z = SERVER_BUILDER
                .comment("范围伤害的Z轴范围半径")
                .translation("useless_mod.configuration.beef_aoe_damage_range_z")
                .defineInRange("beef_aoe_damage_range_z", 5, 1, 64);

        BEEF_AOE_DAMAGE_MAX_TARGETS = SERVER_BUILDER
                .comment("范围伤害单次最多命中的实体数量")
                .translation("useless_mod.configuration.beef_aoe_damage_max_targets")
                .defineInRange("beef_aoe_damage_max_targets", 64, 1, 1024);

        BEEF_MAGNET_RANGE_X = SERVER_BUILDER
                .comment("击杀后范围磁力吸附的X轴范围半径")
                .translation("useless_mod.configuration.beef_magnet_range_x")
                .defineInRange("beef_magnet_range_x", 5, 1, 64);

        BEEF_MAGNET_RANGE_Y = SERVER_BUILDER
                .comment("击杀后范围磁力吸附的Y轴范围半径")
                .translation("useless_mod.configuration.beef_magnet_range_y")
                .defineInRange("beef_magnet_range_y", 3, 1, 64);

        BEEF_MAGNET_RANGE_Z = SERVER_BUILDER
                .comment("击杀后范围磁力吸附的Z轴范围半径")
                .translation("useless_mod.configuration.beef_magnet_range_z")
                .defineInRange("beef_magnet_range_z", 5, 1, 64);

        CHAIN_MINING_MAX_BLOCKS = SERVER_BUILDER
                .comment("连锁挖掘的最大方块数量")
                .translation("useless_mod.configuration.chain_mining_max_blocks")
                .defineInRange("chain_mining_max_blocks", 1000, 1, 1000000);

        CHAIN_MINING_EQUIVALENT_GROUPS = SERVER_BUILDER
                .comment("连锁挖掘等价组，命中同一条目的方块视为同类，可以一起连锁",
                        "原点方块命中多条时取并集; 一条都不命中时仅连锁完全相同的方块",
                        "支持精确方块ID、#方块标签和*通配符，留空则保持仅连锁相同方块",
                        "示例: \"#minecraft:logs\", \"#c:ores\", \"*_ore\"")
                .translation("useless_mod.configuration.chain_mining_equivalent_groups")
                .defineListAllowEmpty("chain_mining_equivalent_groups", List.<String>of(), () -> "",
                        entry -> entry instanceof String);

        // 牛排工具附魔等级配置
        FORTUNE_LEVEL = SERVER_BUILDER
                .comment("牛排工具时运附魔等级")
                .translation("useless_mod.configuration.fortune_level")
                .defineInRange("fortune_level", 10, 1, 127);

        LOOTING_LEVEL = SERVER_BUILDER
                .comment("牛排工具抢夺附魔等级")
                .translation("useless_mod.configuration.looting_level")
                .defineInRange("looting_level", 10, 1, 127);

        // 战利品大爆发触发概率配置
        FESTIVE_DROP_CHANCE = SERVER_BUILDER
                .comment("战利品大爆发触发概率 (百分比, 1-100%)")
                .translation("useless_mod.configuration.festive_drop_chance")
                .defineInRange("festive_drop_chance", 5, 1, 100);

        // 牛排工具挖掘速度配置
        BEEF_TOOL_MINING_SPEED = SERVER_BUILDER
                .comment("牛排工具基础挖掘速度")
                .translation("useless_mod.configuration.beef_tool_mining_speed")
                .defineInRange("beef_tool_mining_speed", 10.0, 1.0, 1000.0);

        BEEF_TOOL_ENTITY_INTERACTION_RANGE = SERVER_BUILDER
                .comment("牛排工具实体触及范围加成, 重启游戏生效")
                .translation("useless_mod.configuration.beef_tool_entity_interaction_range")
                .defineInRange("beef_tool_entity_interaction_range", 8.0, 0.0, 1024.0);

        BEEF_TOOL_BLOCK_INTERACTION_RANGE = SERVER_BUILDER
                .comment("牛排工具方块触及范围加成, 重启游戏生效")
                .translation("useless_mod.configuration.beef_tool_block_interaction_range")
                .defineInRange("beef_tool_block_interaction_range", 8.0, 0.0, 1024.0);

        BEEF_TOOL_FORCE_MINING_BLACKLIST = SERVER_BUILDER
                .comment("牛排工具强制挖掘黑名单，不会被强制挖掘的方块",
                        "支持精确方块ID、#方块标签和*通配符")
                .translation("useless_mod.configuration.beef_tool_force_mining_blacklist")
                .defineListAllowEmpty("beef_tool_force_mining_blacklist", List.<String>of(), () -> "",
                        entry -> entry instanceof String);

        BEEF_TOOL_FORCE_KILL_BLACKLIST = SERVER_BUILDER
                .comment("牛排工具强制击杀黑名单, 多个实体ID用分号分隔, 例如 minecraft:wither;modid:boss")
                .translation("useless_mod.configuration.beef_tool_force_kill_blacklist")
                .define("beef_tool_force_kill_blacklist", "");

        BEEF_TOOL_FORCE_KILL_NON_LIVING_WHITELIST = SERVER_BUILDER
                .comment("牛排工具非生物实体强制击杀白名单, 多个实体ID用分号分隔")
                .translation("useless_mod.configuration.beef_tool_force_kill_non_living_whitelist")
                .define("beef_tool_force_kill_non_living_whitelist", "draconicevolution:guardian_crystal");
        SERVER_BUILDER.pop();

        SERVER_BUILDER.push("mekanism_upgrade");
        TIME_MULTIPLIER = SERVER_BUILDER
                .comment("速度升级增强倍率")
                .defineInRange("time_multiplier", 1, 1, Integer.MAX_VALUE);

        ELECTRICITY_MULTIPLIER = SERVER_BUILDER
                .comment("能量升级节电增强倍率")
                .defineInRange("electricity_multiplier", 1, 1, Integer.MAX_VALUE);

        CAPACITY_MULTIPLIER = SERVER_BUILDER
                .comment("能量升级储电增强倍率")
                .defineInRange("capacity_multiplier", 1, 1, Integer.MAX_VALUE);

        MAX_UPGRADE = SERVER_BUILDER
                .comment("机器可接受的最大速度/能量升级数量, 重启游戏生效")
                .defineInRange("max_upgrade", 16, 1, 64);
        SERVER_BUILDER.pop();

        SERVER_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace")
                .push("advanced_alloy_furnace");
        FURNACE_DRAW_APPFLUX_ENERGY = SERVER_BUILDER
                .comment("万象炉是否自动从所在AE网络抽取AppliedFlux(应用通量)存储的FE能量",
                        "需要安装AppliedFlux且网络中有通量元件, 每tick抽取量受熔炉最大输入速率限制")
                .define("draw_appflux_energy", true);

        FURNACE_DRAW_AE_ENERGY = SERVER_BUILDER
                .comment("万象炉是否直接抽取AE网络自身的能量(按 1 AE = 2 FE 折算)",
                        "警告: 会与网络中其他设备争抢供电, 网络储能不足时可能导致设备频繁掉线",
                        "在AppliedFlux抽取之后作为补充, 每tick总抽取量受熔炉最大输入速率限制")
                .define("draw_ae_energy", false);
        SERVER_BUILDER.pop();

        // Common config: adapter registration happens during common setup on both physical sides.
        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace")
                .push("advanced_alloy_furnace");
        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion")
                .push("recipe_conversion");
        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion.minecraft")
                .push("minecraft");
        ENABLE_CRAFTING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_crafting_recipe_conversion", false);
        ENABLE_SMELTING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_smelting_recipe_conversion", true);
        ENABLE_BREWING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_brewing_recipe_conversion", true);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion.farmers_delight")
                .push("farmers_delight");
        ENABLE_FARMERS_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_farmersdelight_recipe_conversion", true);
        ENABLE_EXTRA_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_extradelight_recipe_conversion", true);
        ENABLE_CRABBERS_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_crabbersdelight_recipe_conversion", true);
        ENABLE_CASUALNESS_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_casualnessdelight_recipe_conversion", true);
        ENABLE_EXPANDED_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_expandeddelight_recipe_conversion", true);
        ENABLE_BREWIN_AND_CHEWIN_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_brewinandchewin_recipe_conversion", true);
        ENABLE_NOMADS_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_nomadsdelight_recipe_conversion", true);
        ENABLE_UBES_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ubesdelight_recipe_conversion", true);
        ENABLE_BARBEQUES_DELIGHT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_barbequesdelight_recipe_conversion", true);
        ENABLE_YOUKAI_HOMECOMING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_youkaishomecoming_recipe_conversion", true);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion.kaleidoscope")
                .push("kaleidoscope");
        ENABLE_KALEIDOSCOPE_COOKERY_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_kaleidoscope_cookery_recipe_conversion", true);
        ENABLE_KALEIDOSCOPE_GRILLING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_kaleidoscope_grilling_recipe_conversion", true);
        ENABLE_KALEIDOSCOPE_TAVERN_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_kaleidoscope_tavern_recipe_conversion", true);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion.ae")
                .push("ae");
        ENABLE_EXTENDED_AE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_extendedae_recipe_conversion", true);
        ENABLE_ADVANCED_AE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_advanced_ae_recipe_conversion", true);
        ENABLE_APP_MEK_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_appmek_recipe_conversion", true);
        ENABLE_AE2_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ae2_recipe_conversion", true);
        ENABLE_AE2CS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ae2cs_recipe_conversion", true);

        ENABLE_AE2LT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ae2lt_recipe_conversion", true);
        ENABLE_DATA_ENERGISTICS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_data_energistics_recipe_conversion", true);
        ENABLE_NEO_ECO_AE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_neo_eco_ae_recipe_conversion", true);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion.mekanism")
                .push("mekanism");
        ENABLE_MEKANISM_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_mekanism_recipe_conversion", true);
        ENABLE_MEKANISM_GENERATORS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_mekanism_generators_recipe_conversion", true);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.translation("useless_mod.configuration.advanced_alloy_furnace.recipe_conversion.other")
                .push("other");
        ENABLE_INDUSTRIAL_FOREGOING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_industrial_foregoing_recipe_conversion", true);
        ENABLE_ACTUALLY_ADDITIONS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_actually_additions_recipe_conversion", true);
        ENABLE_ARS_NOUVEAU_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ars_nouveau_recipe_conversion", true);
        ENABLE_MYSTICAL_AGRICULTURE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_mystical_agriculture_recipe_conversion", true);
        ENABLE_PRODUCTIVE_BEES_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_productive_bees_recipe_conversion", true);
        ENABLE_DRACONIC_EVOLUTION_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_draconic_evolution_recipe_conversion", true);
        ENABLE_POWAH_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_powah_recipe_conversion", true);
        ENABLE_EXTENDED_CRAFTING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_extended_crafting_recipe_conversion", true);
        ENABLE_AVARITIA_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_avaritia_recipe_conversion", true);
        ENABLE_NATURES_AURA_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_natures_aura_recipe_conversion", true);
        ENABLE_FORBIDDEN_ARCANUS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_forbidden_arcanus_recipe_conversion", true);
        ENABLE_OCCULTISM_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_occultism_recipe_conversion", true);
        ENABLE_MALUM_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_malum_recipe_conversion", true);
        ENABLE_ENDER_IO_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ender_io_recipe_conversion", true);
        ENABLE_CREATE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_create_recipe_conversion", true);
        ENABLE_ORITECH_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_oritech_recipe_conversion", true);
        ENABLE_NEOVITAE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_neovitae_recipe_conversion", true);
        ENABLE_UFO_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ufo_recipe_conversion", true);
        ENABLE_MODERN_INDUSTRIALIZATION_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_modern_industrialization_recipe_conversion", true);
        ENABLE_IMMERSIVE_ENGINEERING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_immersiveengineering_recipe_conversion", true);
        ENABLE_PNEUMATICCRAFT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_pneumaticcraft_recipe_conversion", true);
        ENABLE_BIG_REACTORS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_bigreactors_recipe_conversion", true);
        COMMON_BUILDER.pop();

        RECIPE_CONVERSION_OPTIONS = Map.ofEntries(
                Map.entry("farmersdelight", ENABLE_FARMERS_DELIGHT_RECIPE_CONVERSION),
                Map.entry("extradelight", ENABLE_EXTRA_DELIGHT_RECIPE_CONVERSION),
                Map.entry("crabbersdelight", ENABLE_CRABBERS_DELIGHT_RECIPE_CONVERSION),
                Map.entry("casualnessdelight", ENABLE_CASUALNESS_DELIGHT_RECIPE_CONVERSION),
                Map.entry("expandeddelight", ENABLE_EXPANDED_DELIGHT_RECIPE_CONVERSION),
                Map.entry("brewinandchewin", ENABLE_BREWIN_AND_CHEWIN_RECIPE_CONVERSION),
                Map.entry("nomads_delight", ENABLE_NOMADS_DELIGHT_RECIPE_CONVERSION),
                Map.entry("nomadsdelight", ENABLE_NOMADS_DELIGHT_RECIPE_CONVERSION),
                Map.entry("ubesdelight", ENABLE_UBES_DELIGHT_RECIPE_CONVERSION),
                Map.entry("barbequesdelight", ENABLE_BARBEQUES_DELIGHT_RECIPE_CONVERSION),
                Map.entry("youkaisfeasts", ENABLE_YOUKAI_HOMECOMING_RECIPE_CONVERSION),
                Map.entry("youkaishomecoming", ENABLE_YOUKAI_HOMECOMING_RECIPE_CONVERSION),
                Map.entry("kaleidoscope_cookery", ENABLE_KALEIDOSCOPE_COOKERY_RECIPE_CONVERSION),
                Map.entry("kaleidoscope_grilling", ENABLE_KALEIDOSCOPE_GRILLING_RECIPE_CONVERSION),
                Map.entry("kaleidoscope_tavern", ENABLE_KALEIDOSCOPE_TAVERN_RECIPE_CONVERSION),
                Map.entry("extendedae", ENABLE_EXTENDED_AE_RECIPE_CONVERSION),
                Map.entry("advanced_ae", ENABLE_ADVANCED_AE_RECIPE_CONVERSION),
                Map.entry("mekanism", ENABLE_MEKANISM_RECIPE_CONVERSION),
                Map.entry("mekanismgenerators", ENABLE_MEKANISM_GENERATORS_RECIPE_CONVERSION),
                Map.entry("appmek", ENABLE_APP_MEK_RECIPE_CONVERSION),
                Map.entry("ae2", ENABLE_AE2_RECIPE_CONVERSION),
                Map.entry("ae2cs", ENABLE_AE2CS_RECIPE_CONVERSION),
                Map.entry("industrialforegoing", ENABLE_INDUSTRIAL_FOREGOING_RECIPE_CONVERSION),
                Map.entry("actuallyadditions", ENABLE_ACTUALLY_ADDITIONS_RECIPE_CONVERSION),
                Map.entry("ars_nouveau", ENABLE_ARS_NOUVEAU_RECIPE_CONVERSION),
                Map.entry("mysticalagriculture", ENABLE_MYSTICAL_AGRICULTURE_RECIPE_CONVERSION),
                Map.entry("ae2lt", ENABLE_AE2LT_RECIPE_CONVERSION),
                Map.entry("data_energistics", ENABLE_DATA_ENERGISTICS_RECIPE_CONVERSION),
                Map.entry("productivebees", ENABLE_PRODUCTIVE_BEES_RECIPE_CONVERSION),
                Map.entry("draconicevolution", ENABLE_DRACONIC_EVOLUTION_RECIPE_CONVERSION),
                Map.entry("powah", ENABLE_POWAH_RECIPE_CONVERSION),
                Map.entry("extendedcrafting", ENABLE_EXTENDED_CRAFTING_RECIPE_CONVERSION),
                Map.entry("avaritia", ENABLE_AVARITIA_RECIPE_CONVERSION),
                Map.entry("neoecoae", ENABLE_NEO_ECO_AE_RECIPE_CONVERSION),
                Map.entry("naturesaura", ENABLE_NATURES_AURA_RECIPE_CONVERSION),
                Map.entry("forbidden_arcanus", ENABLE_FORBIDDEN_ARCANUS_RECIPE_CONVERSION),
                Map.entry("occultism", ENABLE_OCCULTISM_RECIPE_CONVERSION),
                Map.entry("malum", ENABLE_MALUM_RECIPE_CONVERSION),
                Map.entry("enderio", ENABLE_ENDER_IO_RECIPE_CONVERSION),
                Map.entry("create", ENABLE_CREATE_RECIPE_CONVERSION),
                Map.entry("oritech", ENABLE_ORITECH_RECIPE_CONVERSION),
                Map.entry("neovitae", ENABLE_NEOVITAE_RECIPE_CONVERSION),
                Map.entry("ufo", ENABLE_UFO_RECIPE_CONVERSION),
                Map.entry("modern_industrialization", ENABLE_MODERN_INDUSTRIALIZATION_RECIPE_CONVERSION),
                Map.entry("immersiveengineering", ENABLE_IMMERSIVE_ENGINEERING_RECIPE_CONVERSION),
                Map.entry("pneumaticcraft", ENABLE_PNEUMATICCRAFT_RECIPE_CONVERSION),
                Map.entry("bigreactors", ENABLE_BIG_REACTORS_RECIPE_CONVERSION));
        COMMON_BUILDER.pop();
        COMMON_BUILDER.pop();

        COMMON_SPEC = COMMON_BUILDER.build();
        CLIENT_SPEC = CLIENT_BUILDER.build();
        SERVER_SPEC = SERVER_BUILDER.build();
    }

    private static ModConfigSpec.BooleanValue defineRecipeConversionOption(
            String key, boolean defaultValue) {
        return COMMON_BUILDER
                .comment("是否启用该配方来源的配方转换", "修改后重启游戏生效")
                .translation("useless_mod.configuration." + key)
                .define(key, defaultValue);
    }

    // 获取配置值方法
    private static List<String> defaultCustomPotionEffects() {
        return List.of(
                "minecraft:saturation,1",
                "minecraft:regeneration,6",
                "minecraft:night_vision,1",
                "minecraft:fire_resistance,1",
                "minecraft:water_breathing,1",
                "minecraft:resistance,6"
        );
    }

    private static boolean isValidCustomPotionEffectEntry(Object entry) {
        if (!(entry instanceof String value)) {
            return false;
        }

        String[] parts = value.split(",", -1);
        if (parts.length != 2 || ResourceLocation.tryParse(parts[0].trim()) == null) {
            return false;
        }

        try {
            return Integer.parseInt(parts[1].trim()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static List<String> defaultAE2GiftPackageItems() {
        List<String> items = new ArrayList<>();
        items.add("ae2:creative_energy_cell,1");
        items.add("ae2:fluix_covered_cable,64");
        items.add("ae2:wireless_access_point,1");
        items.add("ae2:wireless_booster,64");
        items.add("ae2:wireless_crafting_terminal,1");
        items.add("ae2:crafting_terminal,1");

        if (ModList.get().isLoaded("extendedae_plus")) {
            items.add("extendedae_plus:infinity_biginteger_cell,1");
        } else {
            items.add("ae2:item_storage_cell_256k,8");
        }

        if (ModList.get().isLoaded("extendedae")) {
            items.add("extendedae:ex_drive,1");
        } else {
            items.add("ae2:drive,1");
        }

        if (ModList.get().isLoaded("ae2wtlib")) {
            items.add("ae2wtlib:quantum_bridge_card,1");
            items.add("ae2:quantum_ring,8");
            items.add("ae2:quantum_link,1");
            items.add("ae2:quantum_entangled_singularity,2");
        }

        return List.copyOf(items);
    }

    private static boolean isValidAE2GiftPackageEntry(Object entry) {
        if (!(entry instanceof String value)) {
            return false;
        }

        String[] parts = value.split(",", -1);
        if (parts.length != 2 || ResourceLocation.tryParse(parts[0].trim()) == null) {
            return false;
        }

        try {
            return Integer.parseInt(parts[1].trim()) > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    public static int getBotanyPotGrowthMultiplier() {
        return getConfigValue(BOTANY_POT_GROWTH_MULTIPLIER);
    }

    public static boolean shouldEnableBotanyPotRendering() {
        return getConfigValue(ENABLE_BOTANY_POT_RENDERING);
    }

    public static int getMatrixPatternCount() {
        return getConfigValue(MATRIX_PATTERN_COUNT);
    }

    public static int getTimeMultiplier() {
        return getConfigValue(TIME_MULTIPLIER);
    }

    public static int getElectricityMultiplier() {
        return getConfigValue(ELECTRICITY_MULTIPLIER);
    }

    public static int getChainMiningMaxBlocks() {
        return getConfigValue(CHAIN_MINING_MAX_BLOCKS);
    }

    public static int getCapacityMultiplier() {
        return getConfigValue(CAPACITY_MULTIPLIER);
    }

    public static int getMaxUpgrade() {
        return getConfigValue(MAX_UPGRADE);
    }

    // 万象炉AE网络抽电配置
    public static boolean isFurnaceDrawAppfluxEnergyEnabled() {
        return getConfigValue(FURNACE_DRAW_APPFLUX_ENERGY);
    }

    public static boolean isFurnaceDrawAeEnergyEnabled() {
        return getConfigValue(FURNACE_DRAW_AE_ENERGY);
    }

    public static boolean isCraftingRecipeConversionEnabled() {
        return getConfigValue(ENABLE_CRAFTING_RECIPE_CONVERSION);
    }

    public static boolean isSmeltingRecipeConversionEnabled() {
        return getConfigValue(ENABLE_SMELTING_RECIPE_CONVERSION);
    }

    public static boolean isBrewingRecipeConversionEnabled() {
        return getConfigValue(ENABLE_BREWING_RECIPE_CONVERSION);
    }

    public static boolean isRecipeConversionEnabled(String sourceId) {
        ModConfigSpec.BooleanValue option = RECIPE_CONVERSION_OPTIONS.get(sourceId);
        return option == null || getConfigValue(option);
    }

    public static int getOmniversalPatternSlots() {
        return normalizeInventorySlots(getConfigValue(OMNIVERSAL_PATTERN_SLOTS));
    }

    public static int getOmniversalMoldSlots() {
        return normalizeInventorySlots(getConfigValue(OMNIVERSAL_MOLD_SLOTS));
    }

    public static int getOmniversalPassivePatternSlots() {
        return Math.max(1, Math.min(540, getConfigValue(OMNIVERSAL_PASSIVE_PATTERN_SLOTS)));
    }

    public static int getOmniversalDecodeCacheCapacity() {
        return Math.max(64, Math.min(16384, getConfigValue(OMNIVERSAL_DECODE_CACHE_CAPACITY)));
    }

    public static int getOreGeneratorSlots() {
        return Math.max(1, Math.min(540, getConfigValue(ORE_GENERATOR_SLOTS)));
    }

    private static int normalizeInventorySlots(int value) {
        int clamped = Math.max(27, Math.min(540, value));
        return Math.max(27, clamped / 27 * 27);
    }

    // 获取连锁挖掘范围配置
    public static int getChainMiningRangeX() {
        return getConfigValue(CHAIN_MINING_RANGE_X);
    }

    public static int getChainMiningRangeY() {
        return getConfigValue(CHAIN_MINING_RANGE_Y);
    }

    public static int getChainMiningRangeZ() {
        return getConfigValue(CHAIN_MINING_RANGE_Z);
    }

    // 获取牛排工具范围伤害配置
    public static int getBeefAoeDamageRangeX() {
        return getConfigValue(BEEF_AOE_DAMAGE_RANGE_X);
    }

    public static int getBeefAoeDamageRangeY() {
        return getConfigValue(BEEF_AOE_DAMAGE_RANGE_Y);
    }

    public static int getBeefAoeDamageRangeZ() {
        return getConfigValue(BEEF_AOE_DAMAGE_RANGE_Z);
    }

    public static int getBeefAoeDamageMaxTargets() {
        return getConfigValue(BEEF_AOE_DAMAGE_MAX_TARGETS);
    }

    // 获取牛排工具范围磁力吸附配置
    public static int getBeefMagnetRangeX() {
        return getConfigValue(BEEF_MAGNET_RANGE_X);
    }

    public static int getBeefMagnetRangeY() {
        return getConfigValue(BEEF_MAGNET_RANGE_Y);
    }

    public static int getBeefMagnetRangeZ() {
        return getConfigValue(BEEF_MAGNET_RANGE_Z);
    }

    public static List<String> getChainMiningEquivalentGroups() {
        return readConfigList(CHAIN_MINING_EQUIVALENT_GROUPS);
    }

    /**
     * 构造本次连锁扫描使用的"同类方块"判定。
     * 每次扫描调用一次，返回的对象内部带有单次扫描的判定缓存，不要跨扫描复用。
     */
    public static ChainEquivalence getChainMiningEquivalence(Block origin) {
        return chainMiningEquivalentGroups().forOrigin(origin);
    }

    public static double getBeefToolFlightSpeed() {
        return getConfigValue(BEEF_TOOL_FLIGHT_SPEED);
    }

    // 获取牛排工具附魔等级配置
    public static int getFortuneLevel() {
        return getConfigValue(FORTUNE_LEVEL);
    }

    public static int getLootingLevel() {
        return getConfigValue(LOOTING_LEVEL);
    }

    // 获取节日掉落触发概率
    public static int getFestiveDropChance() {
        return getConfigValue(FESTIVE_DROP_CHANCE);
    }

    // 获取牛排工具基础挖掘速度
    public static double getBeefToolMiningSpeed() {
        return getConfigValue(BEEF_TOOL_MINING_SPEED);
    }

    public static double getBeefToolEntityInteractionRange() {
        return getConfigValue(BEEF_TOOL_ENTITY_INTERACTION_RANGE);
    }

    public static double getBeefToolBlockInteractionRange() {
        return getConfigValue(BEEF_TOOL_BLOCK_INTERACTION_RANGE);
    }

    public static List<String> getBeefToolForceMiningBlacklist() {
        return readConfigList(BEEF_TOOL_FORCE_MINING_BLACKLIST);
    }

    public static List<String> getAE2GiftPackageItems() {
        return resolveAE2GiftPackageItems(readConfigList(AE2_GIFT_PACKAGE_ITEMS));
    }

    /**
     * Upgrades the automatic fallback entries from older server configs when an
     * AE2 addon is installed after the config was first created. Explicit custom
     * entries are left untouched.
     */
    private static List<String> resolveAE2GiftPackageItems(List<String> configured) {
        if (configured.isEmpty()) {
            return configured;
        }

        List<String> resolved = new ArrayList<>(configured);
        if (ModList.get().isLoaded("extendedae_plus")) {
            replaceLegacyAE2GiftStorageCells(resolved);
        }
        if (ModList.get().isLoaded("extendedae")) {
            replaceLegacyAE2GiftEntry(resolved,
                    "ae2:drive", 1,
                    "extendedae:ex_drive,1");
        }

        return List.copyOf(resolved);
    }

    private static void replaceLegacyAE2GiftStorageCells(List<String> entries) {
        String replacement = "extendedae_plus:infinity_biginteger_cell,1";
        if (containsAE2GiftItem(entries, "extendedae_plus:infinity_biginteger_cell")) {
            return;
        }

        int firstLegacyEntry = -1;
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            if (entry == null) {
                continue;
            }

            String[] parts = entry.split(",", -1);
            if (parts.length != 2 || !"ae2:item_storage_cell_256k".equals(parts[0].trim())) {
                continue;
            }

            if (parsePositiveInteger(parts[1].trim()) != 8) {
                continue;
            }
            if (firstLegacyEntry < 0) {
                firstLegacyEntry = index;
            }
        }

        if (firstLegacyEntry < 0) {
            return;
        }

        for (int index = entries.size() - 1; index >= 0; index--) {
            String entry = entries.get(index);
            if (entry == null) {
                continue;
            }

            String[] parts = entry.split(",", -1);
            if (parts.length == 2
                    && "ae2:item_storage_cell_256k".equals(parts[0].trim())
                    && parsePositiveInteger(parts[1].trim()) == 8) {
                entries.remove(index);
            }
        }
        entries.add(firstLegacyEntry, replacement);
    }

    private static void replaceLegacyAE2GiftEntry(
            List<String> entries, String legacyItemId, int legacyCount, String replacementEntry) {
        String replacementItemId = replacementEntry.substring(0, replacementEntry.indexOf(','));
        if (containsAE2GiftItem(entries, replacementItemId)) {
            return;
        }

        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            if (entry == null) {
                continue;
            }

            String[] parts = entry.split(",", -1);
            if (parts.length == 2
                    && legacyItemId.equals(parts[0].trim())
                    && parsePositiveInteger(parts[1].trim()) == legacyCount) {
                entries.set(index, replacementEntry);
                return;
            }
        }
    }

    private static int parsePositiveInteger(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean containsAE2GiftItem(List<String> entries, String itemId) {
        for (String entry : entries) {
            if (entry == null) {
                continue;
            }

            int separator = entry.indexOf(',');
            if (separator >= 0 && itemId.equals(entry.substring(0, separator).trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBeefToolForceMiningBlockBlacklisted(ResourceLocation blockId) {
        return beefToolForceMiningBlacklistMatcher().matches(blockId);
    }

    public static List<String> getUselessDimensionFloorBlockBlacklist() {
        return readConfigList(USELESS_DIMENSION_FLOOR_BLOCK_BLACKLIST);
    }

    public static List<String> getUselessDimensionFloorBlockWhitelist() {
        return readConfigList(USELESS_DIMENSION_FLOOR_BLOCK_WHITELIST);
    }

    public static boolean isUselessDimensionFloorBlockBlacklisted(ResourceLocation blockId) {
        return uselessDimensionFloorBlockBlacklistMatcher().matches(blockId);
    }

    public static boolean isUselessDimensionFloorBlockAllowed(ResourceLocation blockId) {
        return isUselessDimensionFloorBlockAllowed(blockId,
                uselessDimensionFloorBlockBlacklistMatcher(),
                uselessDimensionFloorBlockWhitelistMatcher());
    }

    static boolean isUselessDimensionFloorBlockAllowed(
            ResourceLocation blockId,
            BlockBlacklistMatcher blacklist,
            BlockBlacklistMatcher whitelist) {
        if (blockId == null || blacklist.matches(blockId)) return false;
        return whitelist.isEmpty() || whitelist.matches(blockId);
    }

    public static List<String> getBeefToolForceKillBlacklist() {
        return splitEntityIdList(getConfigValue(BEEF_TOOL_FORCE_KILL_BLACKLIST));
    }

    public static List<String> getBeefToolForceKillNonLivingWhitelist() {
        return splitEntityIdList(getConfigValue(BEEF_TOOL_FORCE_KILL_NON_LIVING_WHITELIST));
    }

    // 获取药水效果配置
    public static boolean shouldEnablePotionEffects() {
        return getConfigValue(ENABLE_POTION_EFFECTS);
    }

    public static boolean shouldEnableFlightEffect() {
        return getConfigValue(ENABLE_FLIGHT_EFFECT);
    }

    // 获取自定义药水效果配置列表
    public static List<String> getCustomPotionEffects() {
        return readConfigList(CUSTOM_POTION_EFFECTS);
    }

    private static List<String> splitEntityIdList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(";"));
    }

    private static List<String> readConfigList(
            ModConfigSpec.ConfigValue<List<? extends String>> value) {
        List<? extends String> configured = getConfigValue(value);
        return configured == null ? List.of() : List.copyOf(configured);
    }

    private static <T> T getConfigValue(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return value.getDefault();
        }
    }

    private static BlockBlacklistMatcher uselessDimensionFloorBlockBlacklistMatcher() {
        List<String> configured = getUselessDimensionFloorBlockBlacklist();
        if (!configured.equals(cachedUselessDimensionFloorBlockBlacklist)) {
            synchronized (ConfigManager.class) {
                if (!configured.equals(cachedUselessDimensionFloorBlockBlacklist)) {
                    uselessDimensionFloorBlockBlacklistMatcher =
                            new BlockBlacklistMatcher(configured, DIMENSION_FLOOR_BLACKLIST_NAME);
                    cachedUselessDimensionFloorBlockBlacklist = configured;
                }
            }
        }
        return uselessDimensionFloorBlockBlacklistMatcher;
    }

    private static BlockBlacklistMatcher uselessDimensionFloorBlockWhitelistMatcher() {
        List<String> configured = getUselessDimensionFloorBlockWhitelist();
        if (!configured.equals(cachedUselessDimensionFloorBlockWhitelist)) {
            synchronized (ConfigManager.class) {
                if (!configured.equals(cachedUselessDimensionFloorBlockWhitelist)) {
                    uselessDimensionFloorBlockWhitelistMatcher =
                            new BlockBlacklistMatcher(configured, DIMENSION_FLOOR_WHITELIST_NAME);
                    cachedUselessDimensionFloorBlockWhitelist = configured;
                }
            }
        }
        return uselessDimensionFloorBlockWhitelistMatcher;
    }

    private static BlockBlacklistMatcher beefToolForceMiningBlacklistMatcher() {
        List<String> configured = getBeefToolForceMiningBlacklist();
        if (!configured.equals(cachedBeefToolForceMiningBlacklist)) {
            synchronized (ConfigManager.class) {
                if (!configured.equals(cachedBeefToolForceMiningBlacklist)) {
                    beefToolForceMiningBlacklistMatcher =
                            new BlockBlacklistMatcher(configured, "beef tool force mining blacklist");
                    cachedBeefToolForceMiningBlacklist = configured;
                }
            }
        }
        return beefToolForceMiningBlacklistMatcher;
    }

    private static ChainMatchGroups chainMiningEquivalentGroups() {
        List<String> configured = getChainMiningEquivalentGroups();
        if (!configured.equals(cachedChainMiningEquivalentGroups)) {
            synchronized (ConfigManager.class) {
                if (!configured.equals(cachedChainMiningEquivalentGroups)) {
                    chainMiningEquivalentGroups = new ChainMatchGroups(configured);
                    cachedChainMiningEquivalentGroups = configured;
                }
            }
        }
        return chainMiningEquivalentGroups;
    }

}
