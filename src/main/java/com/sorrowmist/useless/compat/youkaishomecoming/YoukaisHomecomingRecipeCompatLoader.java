package com.sorrowmist.useless.compat.youkaishomecoming;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.DryingRackRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.BasinRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.CuisineRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.KettleRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.PotCookingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.SimpleFermentationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.youkaishomecoming.SteamingRecipeAdapter;

/** Registers Youkai's Homecoming recipe adapters after the optional mod is detected. */
public final class YoukaisHomecomingRecipeCompatLoader {
    private YoukaisHomecomingRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new KettleRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
        manager.registerAdapter(new BasinRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
        manager.registerAdapter(new SteamingRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
        manager.registerAdapter(new CuisineRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
        manager.registerAdapter(new DryingRackRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
        manager.registerAdapter(new SimpleFermentationRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
        manager.registerAdapter(new PotCookingRecipeAdapter(), RecipeSourceIds.YOUKAI_HOMECOMING);
    }
}
