package com.sorrowmist.useless.content.recipe.adapters.justdirethings.jdte;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;

/** Optional JDTE entrypoint, loaded only when JDTE is present with Just Dire Things. */
public final class JDTERecipeCompatLoader {
    private JDTERecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(new InfusionRecipeAdapter());
    }
}
