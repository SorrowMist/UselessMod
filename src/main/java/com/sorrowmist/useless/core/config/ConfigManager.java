package com.sorrowmist.useless.core.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ConfigManager {
    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.Builder SERVER_BUILDER = new ModConfigSpec.Builder();
    // 维度生成配置
    private static final ModConfigSpec.ConfigValue<String> BORDER_BLOCK;
    private static final ModConfigSpec.ConfigValue<String> FILL_BLOCK;
    private static final ModConfigSpec.ConfigValue<String> CENTER_BLOCK;
    // 塑料平台生成层数
    private static final ModConfigSpec.IntValue PLATFORM_LAYERS;
    // 塑料平台起始Y值
    private static final ModConfigSpec.IntValue PLATFORM_START_Y;
    // 是否生成基岩层
    private static final ModConfigSpec.BooleanValue GENERATE_BEDROCK;
    // 基岩层是否固定生成在世界最底层
    private static final ModConfigSpec.BooleanValue BEDROCK_AT_BOTTOM;

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
    // 牛排工具附魔等级配置
    private static final ModConfigSpec.IntValue FORTUNE_LEVEL;
    private static final ModConfigSpec.IntValue LOOTING_LEVEL;
    // 牛排工具挖掘速度配置
    private static final ModConfigSpec.DoubleValue BEEF_TOOL_MINING_SPEED;

    // 战利品大爆发触发概率配置 (百分比)
    private static final ModConfigSpec.IntValue FESTIVE_DROP_CHANCE;

    // Mekanism 升级配置
    private static final ModConfigSpec.IntValue TIME_MULTIPLIER;
    private static final ModConfigSpec.IntValue ELECTRICITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue CAPACITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue MAX_UPGRADE;

    static {
        COMMON_BUILDER.push("dimension_generation");
        BORDER_BLOCK = COMMON_BUILDER
                .comment("边框方块, 若不存在则使用蓝色羊毛")
                .define("border_block", "useless_mod:aqua_glow_plastic");

        FILL_BLOCK = COMMON_BUILDER
                .comment("填充方块, 若不存在则使用白色羊毛")
                .define("fill_block", "useless_mod:white_glow_plastic");

        CENTER_BLOCK = COMMON_BUILDER
                .comment("中心方块, 若不存在则使用灰色羊毛")
                .define("center_block", "useless_mod:light_gray_glow_plastic");

        PLATFORM_LAYERS = COMMON_BUILDER
                .comment("塑料平台生成层数")
                .defineInRange("platform_layers", 69, 1, 256);

        PLATFORM_START_Y = COMMON_BUILDER
                .comment("平台起始Y值(若无基岩实际会比该数值高1)")
                .defineInRange("platform_start_y", -64, -64, 256);

        GENERATE_BEDROCK = COMMON_BUILDER
                .comment("是否生成基岩层, 默认生成")
                .define("generate_bedrock", true);

        BEDROCK_AT_BOTTOM = COMMON_BUILDER
                .comment("基岩层是否固定生成在世界最底层(Y=-64), 而非紧贴塑料层下方",
                        "开启后无论塑料起始层为多少, 基岩都生成在最底层")
                .define("bedrock_at_bottom", false);
        COMMON_BUILDER.pop();

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

        SERVER_BUILDER.push("server");
        SERVER_BUILDER.pop();

        // 牛排工具连锁挖掘配置
        COMMON_BUILDER.push("beef_tool");
        ENABLE_POTION_EFFECTS = COMMON_BUILDER
                .comment("是否启用药水效果")
                .define("enable_potion_effects", true);

        ENABLE_FLIGHT_EFFECT = COMMON_BUILDER
                .comment("是否启用飞行效果")
                .define("enable_flight_effect", true);

        // 自定义药水效果列表
        CUSTOM_POTION_EFFECTS = COMMON_BUILDER
                .comment("自定义药水效果列表, 格式: \"modid:effect_name,amplifier\"",
                        "多个效果用分号(;)分隔",
                        "例如: \"minecraft:regeneration,5;minecraft:speed,2\"",
                        "注意: 等级从1开始计算, 1表示I级, 2表示II级, 以此类推")
                .define("custom_potion_effects",
                        "minecraft:saturation,1;minecraft:regeneration,6;minecraft:night_vision,1;minecraft:fire_resistance,1;minecraft:water_breathing,1;minecraft:resistance,6",
                        str -> str instanceof String s && s.matches("^([a-zA-Z0-9_.-]+:[a-zA-Z0-9_./-]+,\\d+)(;[a-zA-Z0-9_.-]+:[a-zA-Z0-9_./-]+,\\d+)*$"));

        CHAIN_MINING_RANGE_X = COMMON_BUILDER
                .comment("连锁挖掘的X轴范围半径")
                .defineInRange("chain_mining_range_x", 8, 1, 32);

        CHAIN_MINING_RANGE_Y = COMMON_BUILDER
                .comment("连锁挖掘的Y轴范围半径")
                .defineInRange("chain_mining_range_y", 8, 1, 32);

        CHAIN_MINING_RANGE_Z = COMMON_BUILDER
                .comment("连锁挖掘的Z轴范围半径")
                .defineInRange("chain_mining_range_z", 8, 1, 255);

        CHAIN_MINING_MAX_BLOCKS = COMMON_BUILDER
                .comment("连锁挖掘的最大方块数量")
                .defineInRange("chain_mining_max_blocks", 1000, 1, 10000);

        // 牛排工具附魔等级配置
        FORTUNE_LEVEL = COMMON_BUILDER
                .comment("牛排工具时运附魔等级")
                .defineInRange("fortune_level", 10, 1, 127);

        LOOTING_LEVEL = COMMON_BUILDER
                .comment("牛排工具抢夺附魔等级")
                .defineInRange("looting_level", 10, 1, 127);

        // 战利品大爆发触发概率配置
        FESTIVE_DROP_CHANCE = COMMON_BUILDER
                .comment("战利品大爆发触发概率 (百分比, 1-100%)")
                .defineInRange("festive_drop_chance", 5, 1, 100);

        // 牛排工具挖掘速度配置
        BEEF_TOOL_MINING_SPEED = COMMON_BUILDER
                .comment("牛排工具基础挖掘速度")
                .defineInRange("beef_tool_mining_speed", 10.0, 1.0, 1000.0);
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

        COMMON_SPEC = COMMON_BUILDER.build();
        CLIENT_SPEC = CLIENT_BUILDER.build();
        SERVER_SPEC = SERVER_BUILDER.build();
    }

    // 获取方块方法
    public static Block getBorderBlock() {
        return getBlockFromString(BORDER_BLOCK.get(), Blocks.BLUE_WOOL);
    }

    public static Block getFillBlock() {
        return getBlockFromString(FILL_BLOCK.get(), Blocks.WHITE_WOOL);
    }

    public static Block getCenterBlock() {
        return getBlockFromString(CENTER_BLOCK.get(), Blocks.GRAY_WOOL);
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

    // 获取塑料平台层数
    public static int getPlatformLayers() {
        return PLATFORM_LAYERS.get();
    }

    // 获取是否生成基岩层
    public static boolean shouldGenerateBedrock() {
        return GENERATE_BEDROCK.get();
    }

    // 获取基岩层是否固定生成在世界最底层
    public static boolean shouldBedrockAtBottom() {
        return BEDROCK_AT_BOTTOM.get();
    }

    // 获取塑料平台起始Y值
    public static int getPlatformStartY() {
        return PLATFORM_START_Y.get();
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

    private static Block getBlockFromString(String blockId, Block fallback) {
        try {
            ResourceLocation location = ResourceLocation.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.get(location);
            return block != Blocks.AIR ? block : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    // 统一检查是否是平台方块
    public static boolean isPlatformBlock(Block block) {
        return block == getFillBlock() ||
                block == getBorderBlock() ||
                block == getCenterBlock();
    }
}
