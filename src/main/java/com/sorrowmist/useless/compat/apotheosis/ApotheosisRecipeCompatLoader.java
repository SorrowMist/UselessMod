package com.sorrowmist.useless.compat.apotheosis;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.apotheosis.ApotheosisAnvilSmashingRecipeAdapter;

/** 在神化加载完成后注册其铁砧宝石粉碎转换。 */
public final class ApotheosisRecipeCompatLoader {
    private ApotheosisRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ApotheosisAnvilSmashingRecipeAdapter(), RecipeSourceIds.APOTHEOSIS);
    }
}
