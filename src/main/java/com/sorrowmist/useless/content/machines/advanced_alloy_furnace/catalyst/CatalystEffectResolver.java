package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst;

import com.sorrowmist.useless.api.enums.CatalystType;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.ItemStack;

/**
 * 高级合金炉催化剂效果解析器。
 * 负责把催化剂类型、配方输出和基础处理时间转换为运行时效果对象。
 */
public final class CatalystEffectResolver {
    private CatalystEffectResolver() {
    }

    /**
     * 根据配方、催化剂物品和基础处理时间解析最终催化剂效果。
     */
    public static ResolvedCatalystEffect resolve(AdvancedAlloyFurnaceRecipe recipe, ItemStack catalystStack, int baseProcessTime) {
        CatalystType catalystType = CatalystType.fromStack(catalystStack);
        int targetTier = findTargetUselessIngotTier(recipe);
        boolean uselessIngotRecipe = targetTier > 0;
        int catalystParallel = catalystType.getNormalRecipeParallel();
        int recipeParallel = uselessIngotRecipe ? Math.max(CatalystParallelManager.calculateParallelForUselessIngotRecipe(targetTier), catalystParallel) : catalystParallel;
        return new ResolvedCatalystEffect(
                catalystType,
                Math.max(1, catalystParallel),
                Math.max(1, recipeParallel),
                catalystType.calculateProcessTime(baseProcessTime),
                !catalystType.isUsefulIngot(),
                uselessIngotRecipe,
                targetTier
        );
    }

    /**
     * 从配方输出中识别目标无用锭等级。
     */
    public static int findTargetUselessIngotTier(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null) {
            return 0;
        }
        for (ItemStack output : recipe.outputs()) {
            CatalystType outputType = CatalystType.fromStack(output);
            if (outputType.isUselessIngotTier()) {
                return outputType.getTier();
            }
        }
        return 0;
    }
}
