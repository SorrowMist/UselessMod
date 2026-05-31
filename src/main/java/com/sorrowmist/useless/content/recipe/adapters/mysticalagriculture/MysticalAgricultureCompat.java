package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mystical Agriculture 兼容性支持
 * <p>
 * 负责检测 Mystical Agriculture 是否存在，并在存在时注册相应的配方适配器
 */
public class MysticalAgricultureCompat {

    private static final Logger LOGGER = LogManager.getLogger(MysticalAgricultureCompat.class);
    private static final String MOD_ID = "mysticalagriculture";

    private static boolean isLoaded = false;

    public static boolean isMysticalAgricultureLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static void init(FMLCommonSetupEvent event) {
        if (!isMysticalAgricultureLoaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register Mystical Agriculture recipe adapters", e);
            }
        });
    }

    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        recipeManager.registerAdapter(new InfusionRecipeAdapter());
        recipeManager.registerAdapter(new AwakeningRecipeAdapter());
        recipeManager.registerAdapter(new SeedEssenceRecipeAdapter());
    }

    public static boolean isLoaded() {
        return isLoaded;
    }
}
