package com.sorrowmist.useless.compat.immersiveengineering;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.immersiveengineering.ImmersiveEngineeringRecipeAdapter;

/** Registers Immersive Engineering adapters only after the optional mod has been detected. */
public final class ImmersiveEngineeringRecipeCompatLoader {
    private ImmersiveEngineeringRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new ImmersiveEngineeringRecipeAdapter(), RecipeSourceIds.IMMERSIVE_ENGINEERING);
    }
}
