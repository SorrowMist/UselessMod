package com.sorrowmist.useless.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

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
    
    // 自定义药水效果配置 - 格式: "modid:effect_name,amplifier" (持续时间固定为20000 tick)
    // 多个效果用分号(;)分隔, 例如: "minecraft:saturation,1;minecraft:regeneration,6"
    private static final ModConfigSpec.ConfigValue<String> CUSTOM_POTION_EFFECTS;

    // 牛排工具连锁挖掘配置
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_X;
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_Y;
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_Z;
    private static final ModConfigSpec.IntValue CHAIN_MINING_MAX_BLOCKS;
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_FLIGHT_SPEED;
    // 牛排工具附魔等级配置
    private static final ModConfigSpec.IntValue FORTUNE_LEVEL;
    private static final ModConfigSpec.IntValue LOOTING_LEVEL;
    // 牛排工具挖掘速度配置
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_MINING_SPEED;
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_ENTITY_INTERACTION_RANGE;
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_BLOCK_INTERACTION_RANGE;
    private static final ModConfigSpec.ConfigValue<String> BEEF_TOOL_FORCE_KILL_BLACKLIST;
    private static final ModConfigSpec.ConfigValue<String> BEEF_TOOL_FORCE_KILL_NON_LIVING_WHITELIST;

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
    private static volatile List<String> cachedUselessDimensionFloorBlockBlacklist = List.of();
    private static volatile BlockBlacklistMatcher uselessDimensionFloorBlockBlacklistMatcher =
            BlockBlacklistMatcher.empty("blacklist");
    private static volatile List<String> cachedUselessDimensionFloorBlockWhitelist = List.of();
    private static volatile BlockBlacklistMatcher uselessDimensionFloorBlockWhitelistMatcher =
            BlockBlacklistMatcher.empty("whitelist");

    static {
        COMMON_BUILDER.push("game_mechanics");
        BOTANY_POT_GROWTH_MULTIPLIER = COMMON_BUILDER
                .comment("植物盆生长倍率 - 1.0为原版速度, 2.0为2倍速度")
                .defineInRange("botany_pot_growth_multiplier", 1, 1, Integer.MAX_VALUE);

        MATRIX_PATTERN_COUNT = COMMON_BUILDER
                .comment("矩阵样板槽位倍数 - 减少数量时请保持槽位空！否则可能会造成样板丢失")
                .defineInRange("matrix_pattern_count", 1, 1, 100);
        COMMON_BUILDER.pop();

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
        COMMON_BUILDER.translation("useless_mod.configuration.beef_tool").push("beef_tool");
        ENABLE_POTION_EFFECTS = COMMON_BUILDER
                .comment("是否启用药水效果")
                .translation("useless_mod.configuration.enable_potion_effects")
                .define("enable_potion_effects", true);

        ENABLE_FLIGHT_EFFECT = COMMON_BUILDER
                .comment("是否启用飞行效果")
                .translation("useless_mod.configuration.enable_flight_effect")
                .define("enable_flight_effect", true);

        BEEF_TOOL_FLIGHT_SPEED = COMMON_BUILDER
                .comment("牛排工具飞行速度")
                .translation("useless_mod.configuration.beef_tool_flight_speed")
                .defineInRange("beef_tool_flight_speed", 0.05, 0.01, 1.0);

        // 自定义药水效果列表
        CUSTOM_POTION_EFFECTS = COMMON_BUILDER
                .comment("自定义药水效果列表, 格式: \"modid:effect_name,amplifier\"",
                        "多个效果用分号(;)分隔",
                        "例如: \"minecraft:regeneration,5;minecraft:speed,2\"",
                        "注意: 等级从1开始计算, 1表示I级, 2表示II级, 以此类推")
                .translation("useless_mod.configuration.custom_potion_effects")
                .define("custom_potion_effects",
                        "minecraft:saturation,1;minecraft:regeneration,6;minecraft:night_vision,1;minecraft:fire_resistance,1;minecraft:water_breathing,1;minecraft:resistance,6",
                        str -> str instanceof String s && s.matches("^([a-zA-Z0-9_.-]+:[a-zA-Z0-9_./-]+,\\d+)(;[a-zA-Z0-9_.-]+:[a-zA-Z0-9_./-]+,\\d+)*$"));

        CHAIN_MINING_RANGE_X = COMMON_BUILDER
                .comment("连锁挖掘的X轴范围半径")
                .translation("useless_mod.configuration.chain_mining_range_x")
                .defineInRange("chain_mining_range_x", 8, 1, 32);

        CHAIN_MINING_RANGE_Y = COMMON_BUILDER
                .comment("连锁挖掘的Y轴范围半径")
                .translation("useless_mod.configuration.chain_mining_range_y")
                .defineInRange("chain_mining_range_y", 8, 1, 255);

        CHAIN_MINING_RANGE_Z = COMMON_BUILDER
                .comment("连锁挖掘的Z轴范围半径")
                .translation("useless_mod.configuration.chain_mining_range_z")
                .defineInRange("chain_mining_range_z", 8, 1, 32);

        CHAIN_MINING_MAX_BLOCKS = COMMON_BUILDER
                .comment("连锁挖掘的最大方块数量")
                .translation("useless_mod.configuration.chain_mining_max_blocks")
                .defineInRange("chain_mining_max_blocks", 1000, 1, 1000000);

        // 牛排工具附魔等级配置
        FORTUNE_LEVEL = COMMON_BUILDER
                .comment("牛排工具时运附魔等级")
                .translation("useless_mod.configuration.fortune_level")
                .defineInRange("fortune_level", 10, 1, 127);

        LOOTING_LEVEL = COMMON_BUILDER
                .comment("牛排工具抢夺附魔等级")
                .translation("useless_mod.configuration.looting_level")
                .defineInRange("looting_level", 10, 1, 127);

        // 战利品大爆发触发概率配置
        FESTIVE_DROP_CHANCE = COMMON_BUILDER
                .comment("战利品大爆发触发概率 (百分比, 1-100%)")
                .translation("useless_mod.configuration.festive_drop_chance")
                .defineInRange("festive_drop_chance", 5, 1, 100);

        // 牛排工具挖掘速度配置
        BEEF_TOOL_MINING_SPEED = COMMON_BUILDER
                .comment("牛排工具基础挖掘速度")
                .translation("useless_mod.configuration.beef_tool_mining_speed")
                .defineInRange("beef_tool_mining_speed", 10.0, 1.0, 1000.0);

        BEEF_TOOL_ENTITY_INTERACTION_RANGE = COMMON_BUILDER
                .comment("牛排工具实体触及范围加成, 重启游戏生效")
                .translation("useless_mod.configuration.beef_tool_entity_interaction_range")
                .defineInRange("beef_tool_entity_interaction_range", 8.0, 0.0, 1024.0);

        BEEF_TOOL_BLOCK_INTERACTION_RANGE = COMMON_BUILDER
                .comment("牛排工具方块触及范围加成, 重启游戏生效")
                .translation("useless_mod.configuration.beef_tool_block_interaction_range")
                .defineInRange("beef_tool_block_interaction_range", 8.0, 0.0, 1024.0);

        BEEF_TOOL_FORCE_KILL_BLACKLIST = COMMON_BUILDER
                .comment("牛排工具强制击杀黑名单, 多个实体ID用分号分隔, 例如 minecraft:wither;modid:boss")
                .translation("useless_mod.configuration.beef_tool_force_kill_blacklist")
                .define("beef_tool_force_kill_blacklist", "");

        BEEF_TOOL_FORCE_KILL_NON_LIVING_WHITELIST = COMMON_BUILDER
                .comment("牛排工具非生物实体强制击杀白名单, 多个实体ID用分号分隔")
                .translation("useless_mod.configuration.beef_tool_force_kill_non_living_whitelist")
                .define("beef_tool_force_kill_non_living_whitelist", "draconicevolution:guardian_crystal");
        COMMON_BUILDER.pop();

        COMMON_BUILDER.push("mekanism_upgrade");
        TIME_MULTIPLIER = COMMON_BUILDER
                .comment("速度升级增强倍率")
                .defineInRange("time_multiplier", 1, 1, Integer.MAX_VALUE);

        ELECTRICITY_MULTIPLIER = COMMON_BUILDER
                .comment("能量升级节电增强倍率")
                .defineInRange("electricity_multiplier", 1, 1, Integer.MAX_VALUE);

        CAPACITY_MULTIPLIER = COMMON_BUILDER
                .comment("能量升级储电增强倍率")
                .defineInRange("capacity_multiplier", 1, 1, Integer.MAX_VALUE);

        MAX_UPGRADE = COMMON_BUILDER
                .comment("机器可接受的最大速度/能量升级数量, 重启游戏生效")
                .defineInRange("max_upgrade", 16, 1, 64);
        COMMON_BUILDER.pop();

        COMMON_BUILDER.push("advanced_alloy_furnace");
        FURNACE_DRAW_APPFLUX_ENERGY = COMMON_BUILDER
                .comment("万象炉是否自动从所在AE网络抽取AppliedFlux(应用通量)存储的FE能量",
                        "需要安装AppliedFlux且网络中有通量元件, 每tick抽取量受熔炉最大输入速率限制")
                .define("draw_appflux_energy", true);

        FURNACE_DRAW_AE_ENERGY = COMMON_BUILDER
                .comment("万象炉是否直接抽取AE网络自身的能量(按 1 AE = 2 FE 折算)",
                        "警告: 会与网络中其他设备争抢供电, 网络储能不足时可能导致设备频繁掉线",
                        "在AppliedFlux抽取之后作为补充, 每tick总抽取量受熔炉最大输入速率限制")
                .define("draw_ae_energy", false);

        ENABLE_CRAFTING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_crafting_recipe_conversion", false);
        ENABLE_SMELTING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_smelting_recipe_conversion", true);
        ENABLE_BREWING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_brewing_recipe_conversion", true);
        ENABLE_EXTENDED_AE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_extendedae_recipe_conversion", true);
        ENABLE_ADVANCED_AE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_advanced_ae_recipe_conversion", true);
        ENABLE_MEKANISM_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_mekanism_recipe_conversion", true);
        ENABLE_MEKANISM_GENERATORS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_mekanism_generators_recipe_conversion", true);
        ENABLE_APP_MEK_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_appmek_recipe_conversion", true);
        ENABLE_AE2_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ae2_recipe_conversion", true);
        ENABLE_AE2CS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ae2cs_recipe_conversion", true);
        ENABLE_INDUSTRIAL_FOREGOING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_industrial_foregoing_recipe_conversion", true);
        ENABLE_ACTUALLY_ADDITIONS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_actually_additions_recipe_conversion", true);
        ENABLE_ARS_NOUVEAU_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ars_nouveau_recipe_conversion", true);
        ENABLE_MYSTICAL_AGRICULTURE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_mystical_agriculture_recipe_conversion", true);
        ENABLE_AE2LT_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_ae2lt_recipe_conversion", true);
        ENABLE_DATA_ENERGISTICS_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_data_energistics_recipe_conversion", true);
        ENABLE_PRODUCTIVE_BEES_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_productive_bees_recipe_conversion", true);
        ENABLE_DRACONIC_EVOLUTION_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_draconic_evolution_recipe_conversion", true);
        ENABLE_POWAH_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_powah_recipe_conversion", true);
        ENABLE_EXTENDED_CRAFTING_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_extended_crafting_recipe_conversion", true);
        ENABLE_NEO_ECO_AE_RECIPE_CONVERSION = defineRecipeConversionOption(
                "enable_neo_eco_ae_recipe_conversion", true);
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

        RECIPE_CONVERSION_OPTIONS = Map.ofEntries(
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
    public static int getBotanyPotGrowthMultiplier() {
        return BOTANY_POT_GROWTH_MULTIPLIER.get();
    }

    public static boolean shouldEnableBotanyPotRendering() {
        return ENABLE_BOTANY_POT_RENDERING.get();
    }

    public static int getMatrixPatternCount() {
        return MATRIX_PATTERN_COUNT.get();
    }

    public static int getTimeMultiplier() {
        return TIME_MULTIPLIER.get();
    }

    public static int getElectricityMultiplier() {
        return ELECTRICITY_MULTIPLIER.get();
    }

    public static int getChainMiningMaxBlocks() {
        return CHAIN_MINING_MAX_BLOCKS.get();
    }

    public static int getCapacityMultiplier() {
        return CAPACITY_MULTIPLIER.get();
    }

    public static int getMaxUpgrade() {
        return MAX_UPGRADE.get();
    }

    // 万象炉AE网络抽电配置
    public static boolean isFurnaceDrawAppfluxEnergyEnabled() {
        return FURNACE_DRAW_APPFLUX_ENERGY.get();
    }

    public static boolean isFurnaceDrawAeEnergyEnabled() {
        return FURNACE_DRAW_AE_ENERGY.get();
    }

    public static boolean isCraftingRecipeConversionEnabled() {
        return ENABLE_CRAFTING_RECIPE_CONVERSION.get();
    }

    public static boolean isSmeltingRecipeConversionEnabled() {
        return ENABLE_SMELTING_RECIPE_CONVERSION.get();
    }

    public static boolean isBrewingRecipeConversionEnabled() {
        return ENABLE_BREWING_RECIPE_CONVERSION.get();
    }

    public static boolean isRecipeConversionEnabled(String sourceId) {
        ModConfigSpec.BooleanValue option = RECIPE_CONVERSION_OPTIONS.get(sourceId);
        return option == null || option.get();
    }

    public static int getOmniversalPatternSlots() {
        return normalizeInventorySlots(OMNIVERSAL_PATTERN_SLOTS.get());
    }

    public static int getOmniversalMoldSlots() {
        return normalizeInventorySlots(OMNIVERSAL_MOLD_SLOTS.get());
    }

    public static int getOmniversalPassivePatternSlots() {
        return Math.max(1, Math.min(540, OMNIVERSAL_PASSIVE_PATTERN_SLOTS.get()));
    }

    public static int getOmniversalDecodeCacheCapacity() {
        return Math.max(64, Math.min(16384, OMNIVERSAL_DECODE_CACHE_CAPACITY.get()));
    }

    public static int getOreGeneratorSlots() {
        return Math.max(1, Math.min(540, ORE_GENERATOR_SLOTS.get()));
    }

    private static int normalizeInventorySlots(int value) {
        int clamped = Math.max(27, Math.min(540, value));
        return Math.max(27, clamped / 27 * 27);
    }

    // 获取连锁挖掘范围配置
    public static int getChainMiningRangeX() {
        return CHAIN_MINING_RANGE_X.get();
    }

    public static int getChainMiningRangeY() {
        return CHAIN_MINING_RANGE_Y.get();
    }

    public static int getChainMiningRangeZ() {
        return CHAIN_MINING_RANGE_Z.get();
    }

    public static double getBeefToolFlightSpeed() {
        return BEEF_TOOL_FLIGHT_SPEED.get();
    }

    // 获取牛排工具附魔等级配置
    public static int getFortuneLevel() {
        return FORTUNE_LEVEL.get();
    }

    public static int getLootingLevel() {
        return LOOTING_LEVEL.get();
    }

    // 获取节日掉落触发概率
    public static int getFestiveDropChance() {
        return FESTIVE_DROP_CHANCE.get();
    }

    // 获取牛排工具基础挖掘速度
    public static double getBeefToolMiningSpeed() {
        return BEEF_TOOL_MINING_SPEED.get();
    }

    public static double getBeefToolEntityInteractionRange() {
        return BEEF_TOOL_ENTITY_INTERACTION_RANGE.get();
    }

    public static double getBeefToolBlockInteractionRange() {
        return BEEF_TOOL_BLOCK_INTERACTION_RANGE.get();
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
        return splitEntityIdList(BEEF_TOOL_FORCE_KILL_BLACKLIST.get());
    }

    public static List<String> getBeefToolForceKillNonLivingWhitelist() {
        return splitEntityIdList(BEEF_TOOL_FORCE_KILL_NON_LIVING_WHITELIST.get());
    }

    // 获取药水效果配置
    public static boolean shouldEnablePotionEffects() {
        return ENABLE_POTION_EFFECTS.get();
    }

    public static boolean shouldEnableFlightEffect() {
        return ENABLE_FLIGHT_EFFECT.get();
    }

    // 获取自定义药水效果配置列表
    public static List<String> getCustomPotionEffects() {
        String value = CUSTOM_POTION_EFFECTS.get();
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(";"));
    }

    private static List<String> splitEntityIdList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(";"));
    }

    private static List<String> readConfigList(
            ModConfigSpec.ConfigValue<List<? extends String>> value) {
        try {
            List<? extends String> configured = value.get();
            return configured == null ? List.of() : List.copyOf(configured);
        } catch (IllegalStateException ignored) {
            return List.of();
        }
    }

    private static BlockBlacklistMatcher uselessDimensionFloorBlockBlacklistMatcher() {
        List<String> configured = getUselessDimensionFloorBlockBlacklist();
        if (!configured.equals(cachedUselessDimensionFloorBlockBlacklist)) {
            synchronized (ConfigManager.class) {
                if (!configured.equals(cachedUselessDimensionFloorBlockBlacklist)) {
                    uselessDimensionFloorBlockBlacklistMatcher = new BlockBlacklistMatcher(configured);
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
                            new BlockBlacklistMatcher(configured, "whitelist");
                    cachedUselessDimensionFloorBlockWhitelist = configured;
                }
            }
        }
        return uselessDimensionFloorBlockWhitelistMatcher;
    }

}
