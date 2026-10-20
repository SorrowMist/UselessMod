package com.sorrowmist.useless.compat.neovitae;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.neovitae.AraVitaeRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.neovitae.AthanorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.neovitae.HellfireForgeRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.neovitae.TabulaVitaeRecipeAdapter;

/** Optional Neo Vitae entrypoint. This class is only loaded when Neo Vitae is present. */
public final class NeoVitaeRecipeCompatLoader {
    private NeoVitaeRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new HellfireForgeRecipeAdapter(), RecipeSourceIds.NEOVITAE);
        manager.registerAdapter(new TabulaVitaeRecipeAdapter(), RecipeSourceIds.NEOVITAE);
        manager.registerAdapter(new AthanorRecipeAdapter(), RecipeSourceIds.NEOVITAE);
        manager.registerAdapter(new AraVitaeRecipeAdapter(), RecipeSourceIds.NEOVITAE);
    }
}
