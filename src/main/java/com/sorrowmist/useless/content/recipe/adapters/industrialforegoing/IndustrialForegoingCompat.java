package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Industrial Foregoing 兼容性支持
 * <p>
 * 负责检测 Industrial Foregoing 是否存在，并在存在时注册相应的配方适配器
 */
public class IndustrialForegoingCompat {

    private static final Logger LOGGER = LogManager.getLogger(IndustrialForegoingCompat.class);
    private static final String MOD_ID = "industrialforegoing";

    private static boolean isLoaded = false;

    /**
     * 检查 Industrial Foregoing 是否已加载
     */
    public static boolean isIndustrialForegoingLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 Industrial Foregoing 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isIndustrialForegoingLoaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register Industrial Foregoing recipe adapters", e);
            }
        });
    }

    /**
     * 注册 Industrial Foregoing 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册溶解成型机配方适配器
        recipeManager.registerAdapter(new DissolutionChamberRecipeAdapter());
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
