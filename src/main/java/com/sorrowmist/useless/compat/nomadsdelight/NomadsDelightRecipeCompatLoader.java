package com.sorrowmist.useless.compat.nomadsdelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.nomadsdelight.ButterChurnRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.nomadsdelight.CurdBagRecipeAdapter;

/** Registers Nomad's Delight's butter churn and curd bag adapters. */
public final class NomadsDelightRecipeCompatLoader {
    private NomadsDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new ButterChurnRecipeAdapter(), RecipeSourceIds.NOMADS_DELIGHT);
        manager.registerAdapter(new CurdBagRecipeAdapter(), RecipeSourceIds.NOMADS_DELIGHT);
    }
}
