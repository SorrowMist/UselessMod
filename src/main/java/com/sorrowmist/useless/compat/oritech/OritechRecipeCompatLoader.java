package com.sorrowmist.useless.compat.oritech;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.oritech.OritechRecipeAdapter;

/** Optional Oritech entrypoint. This class is only loaded when Oritech is present. */
public final class OritechRecipeCompatLoader {
    private OritechRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new OritechRecipeAdapter(), RecipeSourceIds.ORITECH);
    }
}
