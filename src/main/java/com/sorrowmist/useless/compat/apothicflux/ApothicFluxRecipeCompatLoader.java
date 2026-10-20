package com.sorrowmist.useless.compat.apothicflux;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.apothicflux.InfusionRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.apothicflux.RitualCraftingRecipeAdapter;

/** Registers Apothic Flux recipe integrations after the optional mod has loaded. */
public final class ApothicFluxRecipeCompatLoader {
    private ApothicFluxRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new RitualCraftingRecipeAdapter(), RecipeSourceIds.APOTHIC_FLUX);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new InfusionRecipeAdapter(), RecipeSourceIds.APOTHIC_FLUX);
    }
}
