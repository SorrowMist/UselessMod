package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.energy.IEnergyManager;

import java.math.BigInteger;

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
            return divideRoundUp(Math.max(0L, baseEnergyPerTick),
                    resolvedCatalystEffect.energyDivisor());
        }
        return multiplyThenDivideRoundUp(
                Math.max(0L, baseEnergyPerTick), Math.max(0, parallel),
                resolvedCatalystEffect.energyDivisor());
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

    public static long calculateTargetTotalEnergy(long recipeEnergy, long targetParallel,
                                                  ResolvedCatalystEffect resolvedCatalystEffect) {
        long normalizedEnergy = Math.max(0L, recipeEnergy);
        return resolvedCatalystEffect.energyMultipliesWithParallel()
                ? multiplyThenDivideRoundUp(
                        normalizedEnergy, Math.max(0, targetParallel),
                        resolvedCatalystEffect.energyDivisor())
                : divideRoundUp(normalizedEnergy, resolvedCatalystEffect.energyDivisor());
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
            long parallelLong = maximumAffordableParallel(
                    totalAvailableEnergy, recipeEnergy, resolvedCatalystEffect.energyDivisor());
            actualParallel = (int) Math.min(Integer.MAX_VALUE, parallelLong);
            actualParallel = Math.max(0, Math.min(actualParallel, targetParallel));
        } else {
            actualParallel = totalAvailableEnergy >= targetTotalEnergy ? targetParallel : 0;
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

    private static long multiplyThenDivideRoundUp(long amount, long multiplier, int divisor) {
        if (amount <= 0L || multiplier <= 0L) {
            return 0L;
        }
        long normalizedDivisor = Math.max(1, divisor);
        long quotient = amount / normalizedDivisor;
        long remainder = amount % normalizedDivisor;
        long whole = saturatingMultiply(quotient, multiplier);
        if (whole == Long.MAX_VALUE || remainder == 0L) {
            return whole;
        }
        long fractional = multiplyThenDivideRoundUpExact(
                remainder, multiplier, normalizedDivisor);
        return saturatingAdd(whole, fractional);
    }

    private static long multiplyThenDivideRoundUpExact(long amount, long multiplier, long divisor) {
        if (amount <= Long.MAX_VALUE / multiplier) {
            return divideRoundUp(amount * multiplier, divisor);
        }
        BigInteger result = BigInteger.valueOf(amount)
                .multiply(BigInteger.valueOf(multiplier))
                .add(BigInteger.valueOf(divisor - 1L))
                .divide(BigInteger.valueOf(divisor));
        return result.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE
                : result.longValue();
    }

    private static long divideRoundUp(long amount, long divisor) {
        if (amount <= 0L) {
            return 0L;
        }
        return 1L + (amount - 1L) / Math.max(1L, divisor);
    }

    private static long maximumAffordableParallel(long availableEnergy, long recipeEnergy, int divisor) {
        if (availableEnergy <= 0L || recipeEnergy <= 0L) {
            return 0L;
        }
        BigInteger affordable = BigInteger.valueOf(availableEnergy)
                .multiply(BigInteger.valueOf(Math.max(1, divisor)))
                .divide(BigInteger.valueOf(recipeEnergy));
        return affordable.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0
                ? Long.MAX_VALUE
                : affordable.longValue();
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
