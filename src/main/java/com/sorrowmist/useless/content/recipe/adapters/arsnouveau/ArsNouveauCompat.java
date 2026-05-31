package com.sorrowmist.useless.content.recipe.adapters.arsnouveau;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Ars Nouveau 兼容性支持
 * <p>
 * 负责检测 Ars Nouveau 是否存在，并在存在时注册相应的配方适配器
 */
public class ArsNouveauCompat {

    private static final Logger LOGGER = LogManager.getLogger(ArsNouveauCompat.class);
    private static final String MOD_ID = "ars_nouveau";

    private static boolean isLoaded = false;

    /**
     * 检查 Ars Nouveau 是否已加载
     */
    public static boolean isArsNouveauLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 Ars Nouveau 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isArsNouveauLoaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register Ars Nouveau recipe adapters", e);
            }
        });
    }

    /**
     * 注册 Ars Nouveau 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册附魔装置配方适配器
        recipeManager.registerAdapter(new EnchantingApparatusRecipeAdapter());
        // 注册灌魔室配方适配器
        recipeManager.registerAdapter(new ImbuementRecipeAdapter());
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
