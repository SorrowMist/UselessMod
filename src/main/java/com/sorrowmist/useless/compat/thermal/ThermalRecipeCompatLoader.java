package com.sorrowmist.useless.compat.thermal;

import cofh.thermal.core.util.recipes.machine.InsolatorRecipe;
import cofh.thermal.core.util.recipes.machine.PressRecipe;
import cofh.thermal.core.util.recipes.machine.SmelterRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.adapters.thermal.ThermalRecipeAdapter;

public final class ThermalRecipeCompatLoader {
    private ThermalRecipeCompatLoader() {
    }

    public static void register() {
        var manager = AlloyFurnaceRecipeManager.getInstance();
        manager.registerAdapter(new ThermalRecipeAdapter<>(InsolatorRecipe.class, "machine_insolator",
                "thermal_insolator_", AlloyFurnaceMode.INSOLATOR));
        manager.registerAdapter(new ThermalRecipeAdapter<>(SmelterRecipe.class, "machine_smelter",
                "thermal_smelter_", AlloyFurnaceMode.NORMAL));
        manager.registerAdapter(new ThermalRecipeAdapter<>(PressRecipe.class, "machine_press",
                "thermal_press_", AlloyFurnaceMode.PRESS));
    }
}
