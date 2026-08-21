package com.sorrowmist.useless.compat.ufo;

import com.raishxn.ufo.recipe.UniversalMultiblockMachineKind;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.ufo.DimensionalMatterAssemblerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ufo.QMFRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ufo.StellarSimulationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ufo.UniversalMultiblockRecipeAdapter;

/** Registers UFO adapters only after the optional UFO mod has been detected. */
public final class UfoRecipeCompatLoader {
    private UfoRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new QMFRecipeAdapter(), RecipeSourceIds.UFO);
        manager.registerAdapter(new UniversalMultiblockRecipeAdapter(
                UniversalMultiblockMachineKind.QMF), RecipeSourceIds.UFO);
        manager.registerAdapter(new UniversalMultiblockRecipeAdapter(
                UniversalMultiblockMachineKind.QUANTUM_CRYOFORGE), RecipeSourceIds.UFO);
        manager.registerAdapter(new StellarSimulationRecipeAdapter(), RecipeSourceIds.UFO);
        manager.registerAdapter(new DimensionalMatterAssemblerRecipeAdapter(), RecipeSourceIds.UFO);
    }
}
