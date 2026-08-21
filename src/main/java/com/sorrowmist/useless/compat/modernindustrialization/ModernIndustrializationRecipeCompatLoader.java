package com.sorrowmist.useless.compat.modernindustrialization;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.modernindustrialization.ModernIndustrializationRecipeAdapter;

/** Optional Modern Industrialization entrypoint. */
public final class ModernIndustrializationRecipeCompatLoader {
    private ModernIndustrializationRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ModernIndustrializationRecipeAdapter(), RecipeSourceIds.MODERN_INDUSTRIALIZATION);
    }
}
