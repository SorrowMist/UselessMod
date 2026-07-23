package com.sorrowmist.useless.content.multiblock;

import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blocks.multiblock.UselessCoilBlock;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;

/** Fixed coil properties: catalyst behavior per task plus additional independent threads. */
public record OmniversalCoilStats(
        int tier,
        CatalystType catalystType,
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

    public int singleTaskParallel() {
        return catalystType.getNormalRecipeParallel();
    }

    public ResolvedCatalystEffect resolveEffect(AdvancedAlloyFurnaceRecipe recipe) {
        int baseTime = recipe == null ? 200 : Math.max(1, recipe.processTime());
        return CatalystEffectResolver.resolveForType(recipe, catalystType, baseTime);
    }

    private static OmniversalCoilStats[] createStats() {
        OmniversalCoilStats[] stats = new OmniversalCoilStats[UselessCoilBlock.MAX_TIER];
        for (int tier = UselessCoilBlock.MIN_TIER; tier <= UselessCoilBlock.MAX_TIER; tier++) {
            CatalystType catalystType = tier == UselessCoilBlock.USEFUL_TIER
                    ? CatalystType.USEFUL_INGOT
                    : CatalystType.uselessIngotTier(tier);
            stats[tier - UselessCoilBlock.MIN_TIER] = new OmniversalCoilStats(
                    tier,
                    catalystType,
                    tier + 1,
                    AdvancedAlloyFurnaceBlockEntity.calculateEnergyCapacity(tier),
                    AdvancedAlloyFurnaceBlockEntity.calculateEnergyReceive(tier));
        }
        return stats;
    }
}
