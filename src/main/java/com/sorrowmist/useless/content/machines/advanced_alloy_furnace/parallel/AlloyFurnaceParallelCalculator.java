package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;

import java.math.BigInteger;

/** Calculates parallel limits without requiring a full recipe energy payment in the buffer. */
public final class AlloyFurnaceParallelCalculator {
    private AlloyFurnaceParallelCalculator() {
    }

    /** Limits parallelism only when its exact long energy target would overflow. */
    public static long calculateEnergyParallel(
            AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect resolvedCatalystEffect) {
        if (!resolvedCatalystEffect.energyMultipliesWithParallel()) {
            return Long.MAX_VALUE;
        }
        long recipeEnergy = recipe.energy();
        if (recipeEnergy <= 0L) {
            return Long.MAX_VALUE;
        }
        BigInteger maximum = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(Math.max(1, resolvedCatalystEffect.energyDivisor())))
                .divide(BigInteger.valueOf(recipeEnergy));
        return maximum.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE
                : Math.max(1L, maximum.longValue());
    }

    public static int calculateStartableParallel(
            long energyParallel, long catalystParallel, int materialParallel, int outputParallel) {
        long limitedCatalystParallel = catalystParallel == Long.MAX_VALUE ? energyParallel : catalystParallel;
        long parallel = Math.min(
                Math.min(energyParallel, limitedCatalystParallel),
                (long) Math.min(materialParallel, outputParallel));
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, parallel));
    }

    public static int calculateCompletionTargetParallel(
            int initialParallel, long catalystParallel, int materialParallel, int outputParallel) {
        long parallel = Math.min((long) Math.min(materialParallel, outputParallel), catalystParallel);
        return (int) Math.max(initialParallel, Math.min(Integer.MAX_VALUE, parallel));
    }

    public static long calculateAeTaskParallel(
            AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect resolvedCatalystEffect) {
        long catalystParallel = Math.max(1L, resolvedCatalystEffect.recipeParallel());
        return Math.min(catalystParallel, calculateEnergyParallel(recipe, resolvedCatalystEffect));
    }
}
