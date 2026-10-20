package com.sorrowmist.useless.compat.barbequesdelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.barbequesdelight.GrillingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.barbequesdelight.SkeweringRecipeAdapter;

/** Registers Barbeque's Delight recipe adapters after the optional mod is detected. */
public final class BarbequesDelightRecipeCompatLoader {
    private BarbequesDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new GrillingRecipeAdapter(), RecipeSourceIds.BARBEQUES_DELIGHT);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new SkeweringRecipeAdapter(), RecipeSourceIds.BARBEQUES_DELIGHT);
    }
}
