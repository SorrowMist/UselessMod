package com.sorrowmist.useless.compat.extremereactors;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.extremereactors.ExtremeReactorsRecipeAdapter;

/** Registers Extreme Reactors adapters only after the optional mod has been detected. */
public final class ExtremeReactorsRecipeCompatLoader {
    private ExtremeReactorsRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ExtremeReactorsRecipeAdapter(), RecipeSourceIds.BIG_REACTORS);
    }
}
