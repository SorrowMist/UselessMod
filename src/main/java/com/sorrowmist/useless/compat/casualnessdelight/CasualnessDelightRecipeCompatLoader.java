package com.sorrowmist.useless.compat.casualnessdelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.casualnessdelight.DeepFryingRecipeAdapter;

/** Registers Casualness Delight's deep-frying recipes when the optional mod is present. */
public final class CasualnessDelightRecipeCompatLoader {
    private CasualnessDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new DeepFryingRecipeAdapter(), RecipeSourceIds.CASUALNESS_DELIGHT);
    }
}
