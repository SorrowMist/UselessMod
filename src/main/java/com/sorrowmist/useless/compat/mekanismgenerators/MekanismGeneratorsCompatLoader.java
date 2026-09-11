package com.sorrowmist.useless.compat.mekanismgenerators;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.generators.FissionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.mekanismextra.NaquadahReactorRecipeAdapter;
import net.neoforged.fml.ModList;

/** Keeps Mekanism Generators classes behind their optional mod boundary. */
public final class MekanismGeneratorsCompatLoader {
    private MekanismGeneratorsCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new FissionRecipeAdapter(), RecipeSourceIds.MEKANISM_GENERATORS);
        if (ModList.get().isLoaded("mekanism_extras")) {
            AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                    new NaquadahReactorRecipeAdapter(), RecipeSourceIds.MEKANISM_GENERATORS);
        }
    }
}
