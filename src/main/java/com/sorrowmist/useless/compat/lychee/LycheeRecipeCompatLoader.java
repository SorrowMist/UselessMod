package com.sorrowmist.useless.compat.lychee;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.lychee.LycheeBlockCrushingRecipeAdapter;

/** 在 Lychee 加载完成后注册其铁砧下落压碎转换。 */
public final class LycheeRecipeCompatLoader {
    private LycheeRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new LycheeBlockCrushingRecipeAdapter(), RecipeSourceIds.LYCHEE);
    }
}
