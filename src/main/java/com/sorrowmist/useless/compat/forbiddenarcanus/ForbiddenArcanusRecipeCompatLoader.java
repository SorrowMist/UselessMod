package com.sorrowmist.useless.compat.forbiddenarcanus;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.forbiddenarcanus.HephaestusForgeRecipeAdapter;

/** Registers Forbidden Arcanus integrations after the optional mod has loaded. */
public final class ForbiddenArcanusRecipeCompatLoader {
    private ForbiddenArcanusRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(new HephaestusForgeRecipeAdapter());
    }
}
