package com.sorrowmist.useless.content.multiblock;

import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;

/** Fixed coil properties: catalyst behavior apart from processing time plus independent threads. */
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

    public int processTime(int baseTime) {
        if (tier == UselessCoilBlock.USEFUL_TIER) {
            return 1;
        }
        long normalizedBaseTime = Math.max(1, baseTime);
        long divisor = 1L << tier;
        return (int) Math.max(1L, (normalizedBaseTime + divisor - 1L) / divisor);
    }

    public ResolvedCatalystEffect resolveEffect(AdvancedAlloyFurnaceRecipe recipe) {
        int baseTime = recipe == null ? 200 : Math.max(1, recipe.processTime());
        ResolvedCatalystEffect catalystEffect =
                CatalystEffectResolver.resolveForType(recipe, catalystType, baseTime);
        return new ResolvedCatalystEffect(
                catalystEffect.catalystType(),
                singleTaskParallel,
                singleTaskParallel,
                processTime(baseTime),
                catalystEffect.energyMultipliesWithParallel(),
                energyDivisor,
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
