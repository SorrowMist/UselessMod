package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.CrusherRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.EnrichmentChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.FluidToFluidRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.HeavyWaterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.NutritionalLiquifierRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.PrecisionSawmillRecipeAdapter;
import net.neoforged.fml.ModList;

/** Registers Mekanism adapters that do not require an AppMek chemical key. */
public final class MekanismRecipeCompatLoader {
    private MekanismRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new EnrichmentChamberRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new CrusherRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new PrecisionSawmillRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new NutritionalLiquifierRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new FluidToFluidRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new HeavyWaterRecipeAdapter(), RecipeSourceIds.MEKANISM);

        if (ModList.get().isLoaded("appmek")) {
            invokeAppMekRecipes();
        }
    }

    private static void invokeAppMekRecipes() {
        try {
            Class<?> loader = Class.forName(
                    "com.sorrowmist.useless.compat.appmek.AppMekRecipeCompatLoader", true,
                    MekanismRecipeCompatLoader.class.getClassLoader());
            loader.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            UselessMod.LOGGER.error("Failed to register AppMek-backed Mekanism recipe adapters", exception);
        }
    }
}
