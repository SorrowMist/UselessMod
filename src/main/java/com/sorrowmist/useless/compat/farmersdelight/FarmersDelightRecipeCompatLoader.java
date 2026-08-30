package com.sorrowmist.useless.compat.farmersdelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.CookingPotRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.CuttingBoardRecipeAdapter;

/** Registers Farmer's Delight's shared recipe types after the optional mod is detected. */
public final class FarmersDelightRecipeCompatLoader {
    private FarmersDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new CookingPotRecipeAdapter(),
                RecipeSourceIds.FARMERS_DELIGHT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new CuttingBoardRecipeAdapter(),
                RecipeSourceIds.FARMERS_DELIGHT);
    }
}
