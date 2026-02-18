package com.sorrowmist.useless.content.recipe.adapters.actuallyadditions;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Actually Additions 兼容性支持
 * <p>
 * 负责检测 Actually Additions 是否存在，并在存在时注册相应的配方适配器
 */
public class ActuallyAdditionsCompat {

    private static final Logger LOGGER = LogManager.getLogger(ActuallyAdditionsCompat.class);
    private static final String MOD_ID = "actuallyadditions";

    private static boolean isLoaded = false;

    /**
     * 检查 Actually Additions 是否已加载
     */
    public static boolean isActuallyAdditionsLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 Actually Additions 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isActuallyAdditionsLoaded()) {
            LOGGER.info("Actually Additions not detected, skipping recipe adapter registration");
            return;
        }

        isLoaded = true;
        LOGGER.info("Actually Additions detected, registering recipe adapters");

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register Actually Additions recipe adapters", e);
            }
        });
    }

    /**
     * 注册 Actually Additions 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册原子再构机配方适配器
        recipeManager.registerAdapter(new LaserRecipeAdapter());
        LOGGER.info("Registered LaserRecipeAdapter");

        // 注册充能台配方适配器
        recipeManager.registerAdapter(new EmpowererRecipeAdapter());
        LOGGER.info("Registered EmpowererRecipeAdapter");

        LOGGER.info("Actually Additions recipe adapters registered successfully");
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
