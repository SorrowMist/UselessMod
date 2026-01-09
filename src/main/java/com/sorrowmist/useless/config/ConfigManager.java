package com.sorrowmist.useless.config;

import com.sorrowmist.useless.utils.ThermalDependencyHelper;
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

    // Thermal Parallel 配置 - 只在热力系列安装时注册
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_ITEM_CAPACITY;
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_FLUID_CAPACITY;
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_ENERGY_CAPACITY;
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_ITEM_TRANSFER;
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_FLUID_TRANSFER;
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_ENERGY_TRANSFER;
    private static final ModConfigSpec.BooleanValue PARALLEL_INCREASE_ENERGY_CONSUMPTION;
    private static final ModConfigSpec.BooleanValue BASE_MOD_AFFECT_PARALLEL;
    private static final ModConfigSpec.BooleanValue ADD_EXTRA_PARALLEL_AUGMENTS_TO_TAB;

    static {
        BUILDER.push("维度生成设置");
        BORDER_BLOCK = BUILDER
                .comment("边框方块，若不存在则使用蓝色羊毛")
                .define("边框方块", "useless_mod:aqua_glow_plastic");

        FILL_BLOCK = BUILDER
                .comment("填充方块，若不存在则使用白色羊毛")
                .define("填充方块", "useless_mod:white_glow_plastic");

        CENTER_BLOCK = BUILDER
                .comment("中心方块，若不存在则使用灰色羊毛")
                .define("中心方块", "useless_mod:light_gray_glow_plastic");

        PLATFORM_LAYERS = BUILDER
                .comment("塑料平台生成层数")
                .defineInRange("塑料平台层数", 69, 1, 256);

        PLATFORM_START_Y = BUILDER
                .comment("平台起始Y值(若无基岩实际会比该数值高1)")
                .defineInRange("平台起始Y值", -64, -64, 256);

        GENERATE_BEDROCK = BUILDER
                .comment("是否生成基岩层，默认生成")
                .define("生成基岩层", true);
        BUILDER.pop();

        BUILDER.push("游戏机制设置");
        BOTANY_POT_GROWTH_MULTIPLIER = BUILDER
                .comment("植物盆生长倍率 - 1.0为原版速度，2.0为2倍速度")
                .defineInRange("植物盆生长倍率（仅限非附属的植物盆）", 1, 1, Integer.MAX_VALUE);

        MATRIX_PATTERN_COUNT = BUILDER
                .comment("矩阵样板槽位倍数 - 减少数量时请保持槽位空！否则可能会造成样板丢失")
                .defineInRange("矩阵样板槽位倍数", 1, 1, 100);
        BUILDER.pop();

        // 牛排工具连锁挖掘配置
        BUILDER.push("牛排工具设置");
        CHAIN_MINING_RANGE_X = BUILDER
                .comment("连锁挖掘的X轴范围半径")
                .defineInRange("连锁挖掘X轴范围", 8, 1, 32);

        CHAIN_MINING_RANGE_Y = BUILDER
                .comment("连锁挖掘的Y轴范围半径")
                .defineInRange("连锁挖掘Y轴范围", 8, 1, 32);

        CHAIN_MINING_RANGE_Z = BUILDER
                .comment("连锁挖掘的Z轴范围半径")
                .defineInRange("连锁挖掘Z轴范围", 8, 1, 255);

        CHAIN_MINING_MAX_BLOCKS = BUILDER
                .comment("连锁挖掘的最大方块数量")
                .defineInRange("连锁挖掘最大方块数量", 1000, 1, 10000);

        // 牛排工具附魔等级配置
        FORTUNE_LEVEL = BUILDER
                .comment("牛排工具时运附魔等级")
                .defineInRange("牛排工具时运等级", 10, 1, 127);

        LOOTING_LEVEL = BUILDER
                .comment("牛排工具抢夺附魔等级")
                .defineInRange("牛排工具抢夺等级", 10, 1, 127);
        BUILDER.pop();

        BUILDER.push("Mekanism升级设置");
        TIME_MULTIPLIER = BUILDER
                .comment("速度升级增强倍率")
                .defineInRange("速度平均每8个升级最终效果乘以一次该倍率", 1, 1, Integer.MAX_VALUE);

        ELECTRICITY_MULTIPLIER = BUILDER
                .comment("能量升级节电增强倍率")
                .defineInRange("节电平均每8个升级最终效果乘以一次该倍率", 1, 1, Integer.MAX_VALUE);

        CAPACITY_MULTIPLIER = BUILDER
                .comment("能量升级储电增强倍率")
                .defineInRange("储电平均每8个升级最终效果乘以一次该倍率", 1, 1, Integer.MAX_VALUE);

        MAX_UPGRADE = BUILDER
                .comment("机器可接受的最大速度/能量升级数量，重启游戏生效")
                .defineInRange("最大升级数量", 16, 1, 64);
        BUILDER.pop();

        // 只在热力系列安装时添加 Thermal Parallel 配置
        if (ThermalDependencyHelper.isAnyThermalModLoaded()) {
            BUILDER.push("Thermal Parallel 设置");

            PARALLEL_INCREASE_ENERGY_CONSUMPTION = BUILDER
                    .comment("如果设置为true，则机器的能耗会随并行增多而成倍增加")
                    .define("并行增加能耗", false);

            BASE_MOD_AFFECT_PARALLEL = BUILDER
                    .comment("如果设置为true，则机器的并行数量会被整合组件带来的基础倍率增幅")
                    .define("基础倍率影响并行", false);

            ADD_EXTRA_PARALLEL_AUGMENTS_TO_TAB = BUILDER
                    .comment("如果设置为true，则更高等级的并行插件会被加进创造模式物品栏")
                    .define("添加额外并行增强到标签", false);

            PARALLEL_INCREASE_ITEM_CAPACITY = BUILDER
                    .comment("如果设置为true，则并行升级会同步提高机器的输入/输出物品存储上限")
                    .define("并行增加物品容量", true);

            PARALLEL_INCREASE_FLUID_CAPACITY = BUILDER
                    .comment("如果设置为true，则并行升级会同步提高机器的流体存储上限")
                    .define("并行增加流体容量", true);

            PARALLEL_INCREASE_ENERGY_CAPACITY = BUILDER
                    .comment("如果设置为true，则并行升级会同步提高机器的能量存储上限")
                    .define("并行增加能量容量", false);

            PARALLEL_INCREASE_ITEM_TRANSFER = BUILDER
                    .comment("如果设置为true，则并行升级会同步提高机器进行自动提取/弹出物品的速率上限")
                    .define("并行增加物品传输", true);

            PARALLEL_INCREASE_FLUID_TRANSFER = BUILDER
                    .comment("如果设置为true，则并行升级会同步提高机器进行自动提取/弹出流体的速率上限")
                    .define("并行增加流体传输", true);

            PARALLEL_INCREASE_ENERGY_TRANSFER = BUILDER
                    .comment("如果设置为true，则并行升级会同步提高机器的能量传输上限")
                    .define("并行增加能量传输", true);

            BUILDER.pop();
        } else {
            // 如果热力系列未安装，设置默认值
            PARALLEL_INCREASE_ENERGY_CONSUMPTION = null;
            BASE_MOD_AFFECT_PARALLEL = null;
            ADD_EXTRA_PARALLEL_AUGMENTS_TO_TAB = null;
            PARALLEL_INCREASE_ITEM_CAPACITY = null;
            PARALLEL_INCREASE_FLUID_CAPACITY = null;
            PARALLEL_INCREASE_ENERGY_CAPACITY = null;
            PARALLEL_INCREASE_ITEM_TRANSFER = null;
            PARALLEL_INCREASE_FLUID_TRANSFER = null;
            PARALLEL_INCREASE_ENERGY_TRANSFER = null;
        }

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

    // TODO 新的修改？
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