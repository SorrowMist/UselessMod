package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.CombinerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.CrusherRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.EnrichmentChamberRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.FluidToFluidRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.HeavyWaterRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MetallurgicItemRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.NutritionalLiquifierRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.PrecisionSawmillRecipeAdapter;
import com.sorrowmist.useless.core.config.ConfigManager;
import net.minecraftforge.fml.ModList;

/** Registers Mekanism adapters that do not require an AppMek chemical key. */
public final class MekanismRecipeCompatLoader {
    private MekanismRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new CombinerRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new EnrichmentChamberRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new CrusherRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new PrecisionSawmillRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new NutritionalLiquifierRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new FluidToFluidRecipeAdapter(), RecipeSourceIds.MEKANISM);
        manager.registerAdapter(new HeavyWaterRecipeAdapter(), RecipeSourceIds.MEKANISM);
        if (ConfigManager.isRecipeConversionEnabled(RecipeSourceIds.LEGACY_METALLURGIC)) {
            manager.registerAdapter(new MetallurgicItemRecipeAdapter(),
                    RecipeSourceIds.LEGACY_METALLURGIC);
        }

        if (ModList.get().isLoaded("appmek")
                && ConfigManager.isRecipeConversionEnabled(RecipeSourceIds.APP_MEK)) {
            invokeAppMekRecipes();
        }

        if (ModList.get().isLoaded("mekmm")) {
            invokeMoreMachineRecipes();
        }
    }

    private static void invokeMoreMachineRecipes() {
        try {
            Class<?> loader = Class.forName(
                    "com.sorrowmist.useless.compat.mekanismmoremachine.MekanismMoreMachineRecipeCompatLoader",
                    true, MekanismRecipeCompatLoader.class.getClassLoader());
            loader.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            RateLimitedLogger.error(UselessMod.LOGGER, "Failed to register Mekanism More Machine recipe adapters", exception);
        }
    }

    private static void invokeAppMekRecipes() {
        try {
            Class<?> loader = Class.forName(
                    "com.sorrowmist.useless.compat.appmek.AppMekRecipeCompatLoader", true,
                    MekanismRecipeCompatLoader.class.getClassLoader());
            loader.getMethod("register").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            RateLimitedLogger.error(UselessMod.LOGGER, "Failed to register AppMek-backed Mekanism recipe adapters", exception);
        }
    }
}
