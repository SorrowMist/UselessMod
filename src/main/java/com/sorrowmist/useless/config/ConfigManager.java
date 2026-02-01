package com.sorrowmist.useless.config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ConfigManager {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
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

    // 植物盆生长速度配置
    private static final ModConfigSpec.IntValue BOTANY_POT_GROWTH_MULTIPLIER;

    // 矩阵样板数量配置
    private static final ModConfigSpec.IntValue MATRIX_PATTERN_COUNT;

    // 药水效果配置
    private static final ModConfigSpec.BooleanValue ENABLE_POTION_EFFECTS;
    private static final ModConfigSpec.BooleanValue ENABLE_FLIGHT_EFFECT;

    // 牛排工具连锁挖掘配置
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_X;
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_Y;
    private static final ModConfigSpec.IntValue CHAIN_MINING_RANGE_Z;
    private static final ModConfigSpec.IntValue CHAIN_MINING_MAX_BLOCKS;
    // 牛排工具附魔等级配置
    private static final ModConfigSpec.IntValue FORTUNE_LEVEL;
    private static final ModConfigSpec.IntValue LOOTING_LEVEL;

    // Mekanism 升级配置
    private static final ModConfigSpec.IntValue TIME_MULTIPLIER;
    private static final ModConfigSpec.IntValue ELECTRICITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue CAPACITY_MULTIPLIER;
    private static final ModConfigSpec.IntValue MAX_UPGRADE;

    static {
        BUILDER.push("dimension_generation");
        BORDER_BLOCK = BUILDER
                .comment("边框方块，若不存在则使用蓝色羊毛")
                .define("border_block", "useless_mod:aqua_glow_plastic");

        FILL_BLOCK = BUILDER
                .comment("填充方块，若不存在则使用白色羊毛")
                .define("fill_block", "useless_mod:white_glow_plastic");

        CENTER_BLOCK = BUILDER
                .comment("中心方块，若不存在则使用灰色羊毛")
                .define("center_block", "useless_mod:light_gray_glow_plastic");

        PLATFORM_LAYERS = BUILDER
                .comment("塑料平台生成层数")
                .defineInRange("platform_layers", 69, 1, 256);

        PLATFORM_START_Y = BUILDER
                .comment("平台起始Y值(若无基岩实际会比该数值高1)")
                .defineInRange("platform_start_y", -64, -64, 256);

        GENERATE_BEDROCK = BUILDER
                .comment("是否生成基岩层，默认生成")
                .define("generate_bedrock", true);
        BUILDER.pop();

        BUILDER.push("game_mechanics");
        BOTANY_POT_GROWTH_MULTIPLIER = BUILDER
                .comment("植物盆生长倍率 - 1.0为原版速度，2.0为2倍速度")
                .defineInRange("botany_pot_growth_multiplier", 1, 1, Integer.MAX_VALUE);

        MATRIX_PATTERN_COUNT = BUILDER
                .comment("矩阵样板槽位倍数 - 减少数量时请保持槽位空！否则可能会造成样板丢失")
                .defineInRange("matrix_pattern_count", 1, 1, 100);
        BUILDER.pop();

        // 牛排工具连锁挖掘配置
        BUILDER.push("beef_tool");
        ENABLE_POTION_EFFECTS = BUILDER
                .comment("是否启用药水效果")
                .define("enable_potion_effects", true);

        ENABLE_FLIGHT_EFFECT = BUILDER
                .comment("是否启用飞行效果")
                .define("enable_flight_effect", true);

        CHAIN_MINING_RANGE_X = BUILDER
                .comment("连锁挖掘的X轴范围半径")
                .defineInRange("chain_mining_range_x", 8, 1, 32);

        CHAIN_MINING_RANGE_Y = BUILDER
                .comment("连锁挖掘的Y轴范围半径")
                .defineInRange("chain_mining_range_y", 8, 1, 32);

        CHAIN_MINING_RANGE_Z = BUILDER
                .comment("连锁挖掘的Z轴范围半径")
                .defineInRange("chain_mining_range_z", 8, 1, 255);

        CHAIN_MINING_MAX_BLOCKS = BUILDER
                .comment("连锁挖掘的最大方块数量")
                .defineInRange("chain_mining_max_blocks", 1000, 1, 10000);

        // 牛排工具附魔等级配置
        FORTUNE_LEVEL = BUILDER
                .comment("牛排工具时运附魔等级")
                .defineInRange("fortune_level", 10, 1, 127);

        LOOTING_LEVEL = BUILDER
                .comment("牛排工具抢夺附魔等级")
                .defineInRange("looting_level", 10, 1, 127);
        BUILDER.pop();

        BUILDER.push("mekanism_upgrade");
        TIME_MULTIPLIER = BUILDER
                .comment("速度升级增强倍率")
                .defineInRange("time_multiplier", 1, 1, Integer.MAX_VALUE);

        ELECTRICITY_MULTIPLIER = BUILDER
                .comment("能量升级节电增强倍率")
                .defineInRange("electricity_multiplier", 1, 1, Integer.MAX_VALUE);

        CAPACITY_MULTIPLIER = BUILDER
                .comment("能量升级储电增强倍率")
                .defineInRange("capacity_multiplier", 1, 1, Integer.MAX_VALUE);

        MAX_UPGRADE = BUILDER
                .comment("机器可接受的最大速度/能量升级数量，重启游戏生效")
                .defineInRange("max_upgrade", 16, 1, 64);
        BUILDER.pop();

        SPEC = BUILDER.build();
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

    // 获取塑料平台层数
    public static int getPlatformLayers() {
        return PLATFORM_LAYERS.get();
    }

    // 获取是否生成基岩层
    public static boolean shouldGenerateBedrock() {
        return GENERATE_BEDROCK.get();
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