package com.sorrowmist.useless.content.recipe.adapters.advancedae;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * AdvancedAE 兼容性支持
 * <p>
 * 负责检测 AdvancedAE 是否存在，并在存在时注册相应的配方适配器
 */
public class AdvancedAECompat {

    private static final Logger LOGGER = LogManager.getLogger(AdvancedAECompat.class);
    private static final String MOD_ID = "advanced_ae";

    private static boolean isLoaded = false;

    /**
     * 检查 AdvancedAE 是否已加载
     */
    public static boolean isAdvancedAELoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 AdvancedAE 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isAdvancedAELoaded()) {
            LOGGER.info("AdvancedAE not detected, skipping AAE recipe adapter registration");
            return;
        }

        isLoaded = true;
        LOGGER.info("AdvancedAE detected, registering AAE recipe adapters");

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register AdvancedAE recipe adapters", e);
            }
        });
    }

    /**
     * 注册 AdvancedAE 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册反应仓配方适配器
        recipeManager.registerAdapter(new ReactionChamberRecipeAdapter());
        LOGGER.info("Registered ReactionChamberRecipeAdapter");

        LOGGER.info("AdvancedAE recipe adapters registered successfully");
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
