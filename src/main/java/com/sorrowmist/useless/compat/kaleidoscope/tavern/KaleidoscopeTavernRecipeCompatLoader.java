package com.sorrowmist.useless.compat.kaleidoscope.tavern;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern.BarrelRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern.PressingTubRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern.ShakerRecipeAdapter;

/** Registers Kaleidoscope Tavern's barrel, pressing-tub, and shaker adapters. */
public final class KaleidoscopeTavernRecipeCompatLoader {
    private KaleidoscopeTavernRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new BarrelRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_TAVERN);
        manager.registerAdapter(new PressingTubRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_TAVERN);
        manager.registerAdapter(new ShakerRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_TAVERN);
    }
}
