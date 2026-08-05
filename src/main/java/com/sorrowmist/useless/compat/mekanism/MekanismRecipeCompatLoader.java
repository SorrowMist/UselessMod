package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
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
        manager.registerAdapter(new EnrichmentChamberRecipeAdapter());
        manager.registerAdapter(new CrusherRecipeAdapter());
        manager.registerAdapter(new PrecisionSawmillRecipeAdapter());
        manager.registerAdapter(new NutritionalLiquifierRecipeAdapter());
        manager.registerAdapter(new FluidToFluidRecipeAdapter());
        manager.registerAdapter(new HeavyWaterRecipeAdapter());

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
