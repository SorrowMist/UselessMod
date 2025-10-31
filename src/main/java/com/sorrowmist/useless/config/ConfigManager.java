package com.sorrowmist.useless.config;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class ConfigManager {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // 维度生成配置
    public static final ForgeConfigSpec.ConfigValue<String> BORDER_BLOCK;
    public static final ForgeConfigSpec.ConfigValue<String> FILL_BLOCK;
    public static final ForgeConfigSpec.ConfigValue<String> CENTER_BLOCK;

    // 植物盆生长速度配置
    public static final ForgeConfigSpec.DoubleValue BOTANY_POT_GROWTH_MULTIPLIER;

    // 矩阵样板数量配置
    public static final ForgeConfigSpec.IntValue MATRIX_PATTERN_COUNT;

    // Mekanism 升级配置
    public static final ForgeConfigSpec.IntValue TIME_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue ELECTRICITY_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue CAPACITY_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue MAX_UPGRADE;

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

        BUILDER.pop();

        BUILDER.push("游戏机制设置");

        BOTANY_POT_GROWTH_MULTIPLIER = BUILDER
                .comment("植物盆生长倍率 - 1.0为原版速度，2.0为2倍速度，0.5为半速")
                .defineInRange("植物盆生长倍率", 1.0, 0.1, Double.MAX_VALUE);

        MATRIX_PATTERN_COUNT = BUILDER
                .comment("矩阵样板槽位倍数 - 减少数量时请保持槽位空！否则可能会造成样板丢失")
                .defineInRange("矩阵样板槽位倍数", 1, 1, 100);

        BUILDER.pop();

        BUILDER.push("Mekanism升级设置");

        TIME_MULTIPLIER = BUILDER
                .comment("机器运行时间的基础倍率因子")
                .defineInRange("时间倍率", 1, 1, Integer.MAX_VALUE);

        ELECTRICITY_MULTIPLIER = BUILDER
                .comment("机器电力消耗的基础倍率因子")
                .defineInRange("电力倍率", 1, 1, Integer.MAX_VALUE);

        CAPACITY_MULTIPLIER = BUILDER
                .comment("机器容量的基础倍率因子")
                .defineInRange("容量倍率", 1, 1, Integer.MAX_VALUE);

        MAX_UPGRADE = BUILDER
                .comment("机器可接受的最大速度/能量升级数量，重启游戏生效")
                .defineInRange("最大升级数量", 16, 1, 64);

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
    public static float getBotanyPotGrowthMultiplier() {
        return BOTANY_POT_GROWTH_MULTIPLIER.get().floatValue();
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

    public static int getCapacityMultiplier() {
        return CAPACITY_MULTIPLIER.get();
    }

    public static int getMaxUpgrade() {
        return MAX_UPGRADE.get();
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
}