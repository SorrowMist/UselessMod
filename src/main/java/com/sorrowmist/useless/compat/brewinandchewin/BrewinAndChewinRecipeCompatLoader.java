package com.sorrowmist.useless.compat.brewinandchewin;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.brewinandchewin.KegFermentingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.brewinandchewin.KegPouringRecipeAdapter;

/** Registers Brewin And Chewin's keg fermentation adapter when the optional mod is present. */
public final class BrewinAndChewinRecipeCompatLoader {
    private BrewinAndChewinRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new KegFermentingRecipeAdapter(), RecipeSourceIds.BREWIN_AND_CHEWIN);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new KegPouringRecipeAdapter(), RecipeSourceIds.BREWIN_AND_CHEWIN);
    }
}
