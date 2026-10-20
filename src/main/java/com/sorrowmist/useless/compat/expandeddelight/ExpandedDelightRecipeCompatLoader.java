package com.sorrowmist.useless.compat.expandeddelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.expandeddelight.JuicerRecipeAdapter;

/** Registers Expanded Delight's juicer recipe adapter when the optional mod is present. */
public final class ExpandedDelightRecipeCompatLoader {
    private ExpandedDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new JuicerRecipeAdapter(), RecipeSourceIds.EXPANDED_DELIGHT);
    }
}
