package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.energy.IEnergyManager;

/**
 * 高级合金炉执行阶段的公共能量结算工具。
 * 负责统一普通炉子与 AE 任务在每 tick 扣能和完成时补能结算上的规则。
 */
public final class AlloyFurnaceRecipeExecutor {
    private AlloyFurnaceRecipeExecutor() {
    }

    /**
     * 单个 tick 扣能结果。
     */
    public record TickResult(boolean consumedEnergy, int energyConsumed) {
    }

    /**
     * 配方完成时的能量结算结果。
     */
    public record CompletionEnergyResult(int actualParallel, long additionalEnergyConsumed) {
    }

    /**
     * 计算配方的基础每 tick 能耗。
     */
    public static int calculateBaseEnergyPerTick(AdvancedAlloyFurnaceRecipe recipe) {
        return Math.max(1, recipe.energy() / Math.max(1, recipe.processTime()));
    }

    /**
     * 计算当前 tick 在给定并行数下实际需要扣除的能量。
     */
    public static int calculateTickEnergy(int baseEnergyPerTick, int parallel, ResolvedCatalystEffect resolvedCatalystEffect) {
        long energyRequired = resolvedCatalystEffect.energyMultipliesWithParallel() ? (long) baseEnergyPerTick * parallel : baseEnergyPerTick;
        return energyRequired > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) energyRequired;
    }

    /**
     * 执行单个 tick 的能量扣除。
     */
    public static TickResult consumeTickEnergy(IEnergyManager energyManager, int baseEnergyPerTick, int parallel, ResolvedCatalystEffect resolvedCatalystEffect) {
        int energyRequired = calculateTickEnergy(baseEnergyPerTick, parallel, resolvedCatalystEffect);
        if (!energyManager.tryConsumeEnergy(energyRequired)) {
            return new TickResult(false, 0);
        }
        return new TickResult(true, energyRequired);
    }

    /**
     * 计算目标并行数需要满足的总能量。
     */
    public static long calculateTargetTotalEnergy(int recipeEnergy, int targetParallel, ResolvedCatalystEffect resolvedCatalystEffect) {
        return resolvedCatalystEffect.energyMultipliesWithParallel() ? (long) recipeEnergy * targetParallel : recipeEnergy;
    }

    /**
     * 在配方完成时补扣能量，并根据剩余总能量回退最终可执行并行数。
     */
    public static CompletionEnergyResult settleCompletionEnergy(IEnergyManager energyManager, int recipeEnergy, int targetParallel, long accumulatedEnergy, ResolvedCatalystEffect resolvedCatalystEffect) {
        long targetTotalEnergy = calculateTargetTotalEnergy(recipeEnergy, targetParallel, resolvedCatalystEffect);
        long additionalEnergyNeeded = targetTotalEnergy - accumulatedEnergy;
        if (additionalEnergyNeeded <= 0) {
            return new CompletionEnergyResult(targetParallel, 0);
        }

        int consumable = (int) Math.min(additionalEnergyNeeded, Integer.MAX_VALUE);
        if (energyManager.tryConsumeEnergy(consumable)) {
            return new CompletionEnergyResult(targetParallel, consumable);
        }

        if (recipeEnergy <= 0) {
            return new CompletionEnergyResult(targetParallel, 0);
        }

        long totalAvailableEnergy = accumulatedEnergy + energyManager.getEnergyStored();
        long parallelLong = totalAvailableEnergy / recipeEnergy;
        int actualParallel = parallelLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) parallelLong;
        actualParallel = Math.max(0, Math.min(actualParallel, targetParallel));

        int remainingEnergy = energyManager.getEnergyStored();
        if (remainingEnergy > 0) {
            energyManager.tryConsumeEnergy(remainingEnergy);
        }
        return new CompletionEnergyResult(actualParallel, remainingEnergy);
    }
}
