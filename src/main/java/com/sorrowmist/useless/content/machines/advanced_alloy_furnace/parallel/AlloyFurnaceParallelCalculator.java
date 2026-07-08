package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.energy.IEnergyManager;

/**
 * 高级合金炉并行计算工具。
 * 负责统一普通炉子与 AE 任务中涉及能量、催化剂、材料和输出空间的并行上限计算。
 */
public final class AlloyFurnaceParallelCalculator {
    private AlloyFurnaceParallelCalculator() {
    }

    /**
     * 计算当前能量容量允许的最大并行数。
     */
    public static int calculateEnergyParallel(IEnergyManager energyManager, AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect resolvedCatalystEffect) {
        if (!resolvedCatalystEffect.energyMultipliesWithParallel()) {
            return Integer.MAX_VALUE;
        }
        int recipeEnergy = recipe.energy();
        if (recipeEnergy <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, energyManager.getMaxEnergyStored() / recipeEnergy);
    }

    /**
     * 综合能量、催化剂、材料和输出空间，得到当前可以启动的并行数。
     */
    public static int calculateStartableParallel(int energyParallel, int catalystParallel, int materialParallel, int outputParallel) {
        int limitedCatalystParallel = catalystParallel == Integer.MAX_VALUE ? energyParallel : catalystParallel;
        int parallel = Math.min(Math.min(energyParallel, limitedCatalystParallel), Math.min(materialParallel, outputParallel));
        return Math.max(0, parallel);
    }

    /**
     * 计算配方完成时最终尝试追赶到的目标并行数。
     */
    public static int calculateCompletionTargetParallel(int initialParallel, int catalystParallel, int materialParallel, int outputParallel) {
        int parallel = Math.min(Math.min(materialParallel, outputParallel), catalystParallel);
        return Math.max(initialParallel, parallel);
    }

    /**
     * 计算 AE 任务在当前催化剂与能量容量下允许的批次并行上限。
     */
    public static int calculateAeTaskParallel(IEnergyManager energyManager, int baseEnergyPerTick, ResolvedCatalystEffect resolvedCatalystEffect) {
        int parallel = Math.max(1, resolvedCatalystEffect.recipeParallel());
        if (baseEnergyPerTick > 0 && resolvedCatalystEffect.energyMultipliesWithParallel()) {
            int energyParallel = Math.max(1, energyManager.getMaxEnergyStored() / baseEnergyPerTick);
            parallel = Math.min(parallel, energyParallel);
        }
        return parallel;
    }
}
