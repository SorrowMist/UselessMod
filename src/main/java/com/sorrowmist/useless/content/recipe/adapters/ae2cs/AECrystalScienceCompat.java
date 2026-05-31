package com.sorrowmist.useless.content.recipe.adapters.ae2cs;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AECrystalScienceCompat {

    private static final Logger LOGGER = LogManager.getLogger(AECrystalScienceCompat.class);
    private static final String MOD_ID = "ae2cs";

    private static boolean isLoaded = false;

    public static boolean isAECSLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void init(FMLCommonSetupEvent event) {
        if (!isAECSLoaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register AE2 Crystal Science recipe adapters", e);
            }
        });
    }

    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        recipeManager.registerAdapter(new CircuitEtcherRecipeAdapter());
        recipeManager.registerAdapter(new CrystalAggregatorRecipeAdapter());
        recipeManager.registerAdapter(new CrystalPulverizerRecipeAdapter());
        recipeManager.registerAdapter(new CrystalGrowthRecipeAdapter());
    }

    public static boolean isLoaded() {
        return isLoaded;
    }
}
