package com.sorrowmist.useless.compat.mekanismmoremachine;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.AmbientGasCollectorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.RecyclerRecipeAdapter;

/** Registers recipe adapters backed by Mekanism More Machine. */
public final class MekanismMoreMachineRecipeCompatLoader {
    private MekanismMoreMachineRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new RecyclerRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new AmbientGasCollectorRecipeAdapter(), RecipeSourceIds.MEKANISM);
    }
}
