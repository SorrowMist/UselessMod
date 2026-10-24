package com.sorrowmist.useless.compat.mekanismgenerators;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.generators.FissionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.mekanismextra.NaquadahReactorRecipeAdapter;
import net.minecraftforge.fml.ModList;

/** Keeps Mekanism Generators classes behind their optional mod boundary. */
public final class MekanismGeneratorsCompatLoader {
    private MekanismGeneratorsCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new FissionRecipeAdapter(), RecipeSourceIds.MEKANISM_GENERATORS);
        if (ModList.get().isLoaded("mekanism_extras") && hasNaquadahApi()) {
            AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                    new NaquadahReactorRecipeAdapter(), RecipeSourceIds.MEKANISM_GENERATORS);
        }
    }

    /**
     * The 1.5 API moved these registries into plural packages. Keep a string-only
     * check here so a future package move does not turn an optional integration
     * into a hard class-loading failure.
     */
    private static boolean hasNaquadahApi() {
        try {
            ClassLoader loader = MekanismGeneratorsCompatLoader.class.getClassLoader();
            Class<?> gases = Class.forName(
                    "com.jerry.mekanism_extras.common.registries.ExtraGases", false, loader);
            gases.getField("RICH_NAQUADAH_FUEL");
            gases.getField("RICH_URANIUM_FUEL");
            gases.getField("NAQUADAH_URANIUM_FUEL");
            Class<?> blocks = Class.forName(
                    "com.jerry.generator_extras.common.genregistries.ExtraGenBlocks", false, loader);
            blocks.getField("NAQUADAH_REACTOR_CONTROLLER");
            Class<?> genGases = Class.forName(
                    "com.jerry.generator_extras.common.genregistries.ExtraGenGases", false, loader);
            genGases.getField("POLONIUM_CONTAINING_STEAM");
            return true;
        } catch (ClassNotFoundException | NoSuchFieldException | LinkageError ignored) {
            return false;
        }
    }
}
