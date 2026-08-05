package com.sorrowmist.useless.compat.mekanismgenerators;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.generators.FissionRecipeAdapter;

/** Keeps Mekanism Generators classes behind their optional mod boundary. */
public final class MekanismGeneratorsCompatLoader {
    private MekanismGeneratorsCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(new FissionRecipeAdapter());
    }
}
