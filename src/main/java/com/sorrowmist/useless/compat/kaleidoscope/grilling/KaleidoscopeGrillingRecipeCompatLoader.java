package com.sorrowmist.useless.compat.kaleidoscope.grilling;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.grilling.GrillingRecipeAdapter;

/** Registers Kaleidoscope Grilling's runtime cooking recipes. */
public final class KaleidoscopeGrillingRecipeCompatLoader {
    private KaleidoscopeGrillingRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new GrillingRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_GRILLING);
    }
}
