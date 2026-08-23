package com.sorrowmist.useless.compat.pneumaticcraft;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft.PneumaticCraftRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.pneumaticcraft.PneumaticCraftSyntheticRecipeAdapter;

/** Registers PneumaticCraft adapters after the optional mod has been detected. */
public final class PneumaticCraftRecipeCompatLoader {
    private PneumaticCraftRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(PneumaticCraftRecipeAdapter.assembly(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftRecipeAdapter.fluidMixer(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftRecipeAdapter.heatFrameCooling(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftRecipeAdapter.pressureChamber(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftRecipeAdapter.refinery(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftRecipeAdapter.thermoPlant(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftRecipeAdapter.amadron(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftSyntheticRecipeAdapter.etching(), RecipeSourceIds.PNEUMATICCRAFT);
        manager.registerAdapter(PneumaticCraftSyntheticRecipeAdapter.uvLightBox(), RecipeSourceIds.PNEUMATICCRAFT);
    }
}
