package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Mekanism 兼容性支持
 * <p>
 * 负责检测 Mekanism 是否存在，并在存在时注册相应的配方适配器
 */
public class MekanismCompat {

    private static final Logger LOGGER = LogManager.getLogger(MekanismCompat.class);
    private static final String MOD_ID = "mekanism";

    private static boolean isLoaded = false;

    /**
     * 检查 Mekanism 是否已加载
     */
    public static boolean isMekanismLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 Mekanism 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isMekanismLoaded()) {
            LOGGER.info("Mekanism not detected, skipping Mekanism recipe adapter registration");
            return;
        }

        isLoaded = true;
        LOGGER.info("Mekanism detected, registering Mekanism recipe adapters");

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register Mekanism recipe adapters", e);
            }
        });
    }

    /**
     * 注册 Mekanism 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册冶金灌注机配方适配器
        recipeManager.registerAdapter(new MetallurgicInfuserRecipeAdapter());
        LOGGER.info("Registered MetallurgicInfuserRecipeAdapter");

        // 注册富集仓配方适配器
        recipeManager.registerAdapter(new EnrichmentChamberRecipeAdapter());
        LOGGER.info("Registered EnrichmentChamberRecipeAdapter");

        LOGGER.info("Mekanism recipe adapters registered successfully");
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
