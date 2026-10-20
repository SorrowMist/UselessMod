package com.sorrowmist.useless.compat.modernindustrialization;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.modernindustrialization.ModernIndustrializationRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.modernindustrialization.ForgeHammerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.modernindustrialization.NuclearAbsorptionRecipeAdapter;

/** Optional Modern Industrialization entrypoint. */
public final class ModernIndustrializationRecipeCompatLoader {
    private ModernIndustrializationRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ModernIndustrializationRecipeAdapter(), RecipeSourceIds.MODERN_INDUSTRIALIZATION);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ForgeHammerRecipeAdapter(), RecipeSourceIds.MODERN_INDUSTRIALIZATION);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new NuclearAbsorptionRecipeAdapter(), RecipeSourceIds.MODERN_INDUSTRIALIZATION);
    }
}
