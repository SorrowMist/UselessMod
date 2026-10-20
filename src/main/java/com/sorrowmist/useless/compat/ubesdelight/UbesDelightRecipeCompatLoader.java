package com.sorrowmist.useless.compat.ubesdelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.ubesdelight.BakingMatRecipeAdapter;

/** Registers Ube's Delight recipe adapters after the optional mod is detected. */
public final class UbesDelightRecipeCompatLoader {
    private UbesDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new BakingMatRecipeAdapter(), RecipeSourceIds.UBES_DELIGHT);
    }
}
