package com.sorrowmist.useless.compat.kaleidoscope.cookery;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.ChoppingBoardRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.FlexPotRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.FlexStockpotRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.MillstoneRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.PotRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.SteamerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.StockpotRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery.TeapotRecipeAdapter;

/** Registers Kaleidoscope Cookery's independent kitchen recipe types. */
public final class KaleidoscopeCookeryRecipeCompatLoader {
    private KaleidoscopeCookeryRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new PotRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new FlexPotRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new StockpotRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new FlexStockpotRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new ChoppingBoardRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new SteamerRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new MillstoneRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
        manager.registerAdapter(new TeapotRecipeAdapter(), RecipeSourceIds.KALEIDOSCOPE_COOKERY);
    }
}
