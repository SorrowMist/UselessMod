package com.sorrowmist.useless.compat.crabbersdelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.crabbersdelight.CrabTrapRecipeAdapter;

/** Registers Crabbers Delight's data-driven crab-trap integration when the mod is present. */
public final class CrabbersDelightRecipeCompatLoader {
    private CrabbersDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new CrabTrapRecipeAdapter(), RecipeSourceIds.CRABBERS_DELIGHT);
    }
}
