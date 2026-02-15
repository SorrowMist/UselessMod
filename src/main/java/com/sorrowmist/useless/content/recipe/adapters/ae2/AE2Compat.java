package com.sorrowmist.useless.content.recipe.adapters.ae2;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Applied Energistics 2 兼容性支持
 * <p>
 * 负责检测 AE2 是否存在，并在存在时注册相应的配方适配器
 */
public class AE2Compat {

    private static final Logger LOGGER = LogManager.getLogger(AE2Compat.class);
    private static final String MOD_ID = "ae2";

    private static boolean isLoaded = false;

    /**
     * 检查 AE2 是否已加载
     */
    public static boolean isAE2Loaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 AE2 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isAE2Loaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register AE2 recipe adapters", e);
            }
        });
    }

    /**
     * 注册 AE2 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册压印器配方适配器
        recipeManager.registerAdapter(new InscriberRecipeAdapter());
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
