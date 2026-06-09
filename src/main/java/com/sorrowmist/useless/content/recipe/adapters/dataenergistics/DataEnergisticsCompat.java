package com.sorrowmist.useless.content.recipe.adapters.dataenergistics;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * DataEnergistics 兼容性支持
 * <p>
 * 负责检测 DataEnergistics 是否存在，并在存在时注册相应的配方适配器
 */
public class DataEnergisticsCompat {

    private static final Logger LOGGER = LogManager.getLogger(DataEnergisticsCompat.class);
    private static final String MOD_ID = "data_energistics";

    private static boolean isLoaded = false;

    /**
     * 检查 DataEnergistics 是否已加载
     */
    public static boolean isDataEnergisticsLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /**
     * 初始化 DataEnergistics 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isDataEnergisticsLoaded()) {
            return;
        }

        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register DataEnergistics recipe adapters", e);
            }
        });
    }

    /**
     * 注册 DataEnergistics 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();

        // 注册数据重组器配方适配器
        recipeManager.registerAdapter(new DataReassemblerRecipeAdapter());
    }

    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}