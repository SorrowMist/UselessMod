package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;

/** Calculates parallel limits without requiring a full recipe energy payment in the buffer. */
public final class AlloyFurnaceParallelCalculator {
    private AlloyFurnaceParallelCalculator() {
    }

    /** Limits parallelism only when its exact long energy target would overflow. */
    public static int calculateEnergyParallel(
            AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect resolvedCatalystEffect) {
        if (!resolvedCatalystEffect.energyMultipliesWithParallel()) {
            return Integer.MAX_VALUE;
        }
        long recipeEnergy = recipe.energy();
        if (recipeEnergy <= 0L) {
            return Integer.MAX_VALUE;
        }
        long maximum = Long.MAX_VALUE / recipeEnergy;
        return maximum > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) maximum);
    }

    public static int calculateStartableParallel(
            int energyParallel, int catalystParallel, int materialParallel, int outputParallel) {
        int limitedCatalystParallel = catalystParallel == Integer.MAX_VALUE ? energyParallel : catalystParallel;
        int parallel = Math.min(
                Math.min(energyParallel, limitedCatalystParallel),
                Math.min(materialParallel, outputParallel));
        return Math.max(0, parallel);
    }

    public static int calculateCompletionTargetParallel(
            int initialParallel, int catalystParallel, int materialParallel, int outputParallel) {
        int parallel = Math.min(Math.min(materialParallel, outputParallel), catalystParallel);
        return Math.max(initialParallel, parallel);
    }

    public static int calculateAeTaskParallel(
            AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect resolvedCatalystEffect) {
        int catalystParallel = Math.max(1, resolvedCatalystEffect.recipeParallel());
        return Math.min(catalystParallel, calculateEnergyParallel(recipe, resolvedCatalystEffect));
    }
}
