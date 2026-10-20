package com.sorrowmist.useless.content.multiblock;

import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.core.config.ConfigManager;

/** Provides coil properties: catalyst behavior apart from processing time plus independent threads. */
public record OmniversalCoilStats(
        int tier,
        CatalystType catalystType,
        long singleTaskParallel,
        int energyDivisor,
        int threads,
        long energyCapacity,
        long maxReceive
) {
    private static final OmniversalCoilStats[] BY_TIER = createStats();

    public static OmniversalCoilStats forTier(int tier) {
        if (tier < UselessCoilBlock.MIN_TIER || tier > UselessCoilBlock.MAX_TIER) {
            throw new IllegalArgumentException("Unsupported omniversal coil tier: " + tier);
        }
        return BY_TIER[tier - UselessCoilBlock.MIN_TIER];
    }

    @Override
    public long singleTaskParallel() {
        return tier == UselessCoilBlock.USEFUL_TIER
                ? Long.MAX_VALUE
                : ConfigManager.getOmniversalCoilSingleTaskParallel(tier);
    }

    @Override
    public int energyDivisor() {
        return tier == UselessCoilBlock.USEFUL_TIER
                ? 1 << tier
                : ConfigManager.getOmniversalCoilEnergyDivisor(tier);
    }

    @Override
    public int threads() {
        return tier == UselessCoilBlock.USEFUL_TIER
                ? ConfigManager.getOmniversalUsefulTierThreads()
                : ConfigManager.getOmniversalCoilThreads(tier);
    }

    public int processTime(int baseTime) {
        if (tier == UselessCoilBlock.USEFUL_TIER) {
            return 1;
        }
        long normalizedBaseTime = Math.max(1, baseTime);
        double multiplier = ConfigManager.getOmniversalCoilTimeMultiplier(tier);
        return Math.max(1, (int) Math.ceil(normalizedBaseTime * multiplier));
    }

    /**
     * 解析本档次线圈在某个配方上的运行参数。
     *
     * <p><b>不变式：有用线圈（{@link UselessCoilBlock#USEFUL_TIER}）的
     * {@code energyMultipliesWithParallel} 必须保持 {@code false}。</b></p>
     *
     * <p>它来自 {@code CatalystEffectResolver} 的 {@code !isUsefulIngot()}，而本档次（tier 10）的
     * {@code catalystType} 正是 {@link CatalystType#USEFUL_INGOT}，于是整批只收一次固定能耗
     * （{@code ceil(recipeEnergy / 1024)}），<b>与合成次数无关</b>。</p>
     *
     * <p>这条性质是 bigint（原生大数）批次能在有用线圈上吃到完整规模的前提：能量不随 count 放大，
     * 容量就不会被能量闸压小。反过来，若把这里改成按并行计费，大数批次会被
     * {@code 可用能量 / 单份能耗} 卡回小规模。改本方法或改催化剂解析前请先确认这一点。</p>
     */
    public ResolvedCatalystEffect resolveEffect(AdvancedAlloyFurnaceRecipe recipe) {
        int baseTime = recipe == null ? 200 : Math.max(1, recipe.processTime());
        ResolvedCatalystEffect catalystEffect =
                CatalystEffectResolver.resolveForType(recipe, catalystType, baseTime);
        return new ResolvedCatalystEffect(
                catalystEffect.catalystType(),
                singleTaskParallel(),
                singleTaskParallel(),
                processTime(baseTime),
                catalystEffect.energyMultipliesWithParallel(),
                energyDivisor(),
                catalystEffect.uselessIngotRecipe(),
                catalystEffect.targetUselessIngotTier());
    }

    private static OmniversalCoilStats[] createStats() {
        OmniversalCoilStats[] stats = new OmniversalCoilStats[UselessCoilBlock.MAX_TIER];
        for (int tier = UselessCoilBlock.MIN_TIER; tier <= UselessCoilBlock.MAX_TIER; tier++) {
            CatalystType catalystType = tier == UselessCoilBlock.USEFUL_TIER
                    ? CatalystType.USEFUL_INGOT
                    : CatalystType.uselessIngotTier(tier);
            long singleTaskParallel = tier == UselessCoilBlock.USEFUL_TIER
                    ? Long.MAX_VALUE
                    : 1L << (tier * 2);
            stats[tier - UselessCoilBlock.MIN_TIER] = new OmniversalCoilStats(
                    tier,
                    catalystType,
                    singleTaskParallel,
                    1 << tier,
                    tier + 1,
                    AdvancedAlloyFurnaceBlockEntity.calculateEnergyCapacity(tier),
                    AdvancedAlloyFurnaceBlockEntity.calculateEnergyReceive(tier));
        }
        return stats;
    }
}
