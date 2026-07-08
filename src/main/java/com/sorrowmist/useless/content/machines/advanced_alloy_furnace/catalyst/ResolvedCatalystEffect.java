package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst;

import com.sorrowmist.useless.api.enums.CatalystType;

/**
 * 催化剂在某个具体配方上下文中的运行结果。
 * 该对象用于把催化剂静态信息和配方目标无用锭等级整合成统一执行参数。
 */
public record ResolvedCatalystEffect(
        CatalystType catalystType,
        int catalystParallel,
        int recipeParallel,
        int processTime,
        boolean energyMultipliesWithParallel,
        boolean uselessIngotRecipe,
        int targetUselessIngotTier
) {
}
