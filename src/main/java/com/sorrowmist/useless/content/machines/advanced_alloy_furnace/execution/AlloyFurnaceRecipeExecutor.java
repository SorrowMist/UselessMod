package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.energy.IEnergyManager;

/** Shared long-energy accounting for local and AE alloy-furnace execution. */
public final class AlloyFurnaceRecipeExecutor {
    private AlloyFurnaceRecipeExecutor() {
    }

    public record TickResult(boolean progressAdvanced, long energyConsumed) {
    }

    public record CompletionEnergyResult(int actualParallel, long additionalEnergyConsumed) {
    }

    public static long calculateBaseEnergyPerTick(AdvancedAlloyFurnaceRecipe recipe) {
        long energy = Math.max(0L, recipe.energy());
        return energy == 0L ? 0L : Math.max(1L, energy / Math.max(1, recipe.processTime()));
    }

    public static long calculateTickEnergy(long baseEnergyPerTick, int parallel,
                                           ResolvedCatalystEffect resolvedCatalystEffect) {
        if (!resolvedCatalystEffect.energyMultipliesWithParallel()) {
            return Math.max(0L, baseEnergyPerTick);
        }
        return saturatingMultiply(Math.max(0L, baseEnergyPerTick), Math.max(0, parallel));
    }

    public static TickResult consumeTickEnergy(IEnergyManager energyManager, long baseEnergyPerTick, int parallel,
                                               ResolvedCatalystEffect resolvedCatalystEffect) {
        long energyRequired = calculateTickEnergy(baseEnergyPerTick, parallel, resolvedCatalystEffect);
        if (energyRequired == 0L) {
            return new TickResult(true, 0L);
        }
        if (!energyManager.tryConsumeEnergy(energyRequired)) {
            return new TickResult(false, 0L);
        }
        return new TickResult(true, energyRequired);
    }

    /**
     * Charges the next progress step. Partial payments are retained so a step can cost more than the buffer capacity.
     */
    public static TickResult consumeProgressEnergy(IEnergyManager energyManager, long targetTotalEnergy,
                                                   int currentProgress, int maxProgress,
                                                   long accumulatedEnergy) {
        long normalizedTarget = Math.max(0L, targetTotalEnergy);
        if (normalizedTarget == 0L) {
            return new TickResult(true, 0L);
        }

        long threshold = energyAtProgress(
                normalizedTarget, Math.min(Math.max(0, currentProgress) + 1, Math.max(1, maxProgress)), maxProgress);
        long required = threshold - Math.max(0L, accumulatedEnergy);
        if (required <= 0L) {
            return new TickResult(true, 0L);
        }

        long consumable = Math.min(required, energyManager.getEnergyStoredLong());
        if (consumable <= 0L || !energyManager.tryConsumeEnergy(consumable)) {
            return new TickResult(false, 0L);
        }
        return new TickResult(accumulatedEnergy + consumable >= threshold, consumable);
    }

    /** Returns the cumulative energy that must be paid at a given progress position. */
    static long energyAtProgress(long totalEnergy, int progress, int maxProgress) {
        if (totalEnergy <= 0L || progress <= 0) {
            return 0L;
        }
        int steps = Math.max(1, maxProgress);
        if (progress >= steps) {
            return totalEnergy;
        }

        long quotient = totalEnergy / steps;
        long remainder = totalEnergy % steps;
        long whole = quotient * progress;
        long fractional = (remainder * progress + steps - 1L) / steps;
        return whole + fractional;
    }

    public static long calculateTargetTotalEnergy(long recipeEnergy, int targetParallel,
                                                  ResolvedCatalystEffect resolvedCatalystEffect) {
        long normalizedEnergy = Math.max(0L, recipeEnergy);
        return resolvedCatalystEffect.energyMultipliesWithParallel()
                ? saturatingMultiply(normalizedEnergy, Math.max(0, targetParallel))
                : normalizedEnergy;
    }

    public static CompletionEnergyResult settleCompletionEnergy(
            IEnergyManager energyManager, long recipeEnergy, int targetParallel, long accumulatedEnergy,
            ResolvedCatalystEffect resolvedCatalystEffect) {
        long targetTotalEnergy = calculateTargetTotalEnergy(recipeEnergy, targetParallel, resolvedCatalystEffect);
        long additionalEnergyNeeded = targetTotalEnergy - Math.max(0L, accumulatedEnergy);
        if (additionalEnergyNeeded <= 0L) {
            return new CompletionEnergyResult(targetParallel, 0L);
        }

        if (energyManager.tryConsumeEnergy(additionalEnergyNeeded)) {
            return new CompletionEnergyResult(targetParallel, additionalEnergyNeeded);
        }

        if (recipeEnergy <= 0L) {
            return new CompletionEnergyResult(targetParallel, 0L);
        }

        long normalizedAccumulated = Math.max(0L, accumulatedEnergy);
        long totalAvailableEnergy = saturatingAdd(
                normalizedAccumulated, energyManager.getEnergyStoredLong());
        int actualParallel;
        if (resolvedCatalystEffect.energyMultipliesWithParallel()) {
            long parallelLong = totalAvailableEnergy / recipeEnergy;
            actualParallel = parallelLong > Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) parallelLong;
            actualParallel = Math.max(0, Math.min(actualParallel, targetParallel));
        } else {
            actualParallel = totalAvailableEnergy >= recipeEnergy ? targetParallel : 0;
        }

        long actualTargetEnergy = calculateTargetTotalEnergy(
                recipeEnergy, actualParallel, resolvedCatalystEffect);
        long energyToConsume = Math.max(0L, actualTargetEnergy - normalizedAccumulated);
        if (energyToConsume > 0L && !energyManager.tryConsumeEnergy(energyToConsume)) {
            return new CompletionEnergyResult(0, 0L);
        }
        return new CompletionEnergyResult(actualParallel, energyToConsume);
    }

    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0L || multiplier <= 0L) {
            return 0L;
        }
        return amount > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : amount * multiplier;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
