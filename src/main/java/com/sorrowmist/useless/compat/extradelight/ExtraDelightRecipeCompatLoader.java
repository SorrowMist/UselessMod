package com.sorrowmist.useless.compat.extradelight;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.ChillerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.DoughShapingRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.DryingRackRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.EvaporatorRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.JuicerRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.MeltingPotRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.MixingBowlRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.MortarRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.OvenRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.delight.extradelight.VatRecipeAdapter;

/** Registers Extra Delight's workstation recipes when the optional mod is present. */
public final class ExtraDelightRecipeCompatLoader {
    private ExtraDelightRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new OvenRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new DryingRackRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new DoughShapingRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new MixingBowlRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new MortarRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new VatRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new MeltingPotRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new ChillerRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new EvaporatorRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
        manager.registerAdapter(new JuicerRecipeAdapter(), RecipeSourceIds.EXTRA_DELIGHT);
    }
}
