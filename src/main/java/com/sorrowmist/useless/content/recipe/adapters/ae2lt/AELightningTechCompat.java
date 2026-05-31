package com.sorrowmist.useless.content.recipe.adapters.ae2lt;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * AE2 Lightning Tech 兼容性支持
 * <p>
 * 负责检测 AE2 Lightning Tech 是否存在，并在存在时注册相应的配方适配器
 */
public class AELightningTechCompat {

    private static final Logger LOGGER = LogManager.getLogger(AELightningTechCompat.class);
    private static final String MOD_ID = "ae2lt";

    private static boolean isLoaded = false;

    /**
     * 检查 AE2 Lightning Tech 是否已加载
     */
    public static boolean isAELightningTechLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 AE2 Lightning Tech 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isAELightningTechLoaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register AE2 Lightning Tech recipe adapters", e);
            }
        });
    }

    /**
     * 注册 AE2 Lightning Tech 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        recipeManager.registerAdapter(new LightningSimulationRecipeAdapter());
        recipeManager.registerAdapter(new LightningAssemblyRecipeAdapter());
        recipeManager.registerAdapter(new OverloadProcessingRecipeAdapter());
        recipeManager.registerAdapter(new CrystalCatalyzerRecipeAdapter());
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
