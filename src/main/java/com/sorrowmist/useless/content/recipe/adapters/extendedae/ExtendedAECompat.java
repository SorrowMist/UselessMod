package com.sorrowmist.useless.content.recipe.adapters.extendedae;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ExtendedAE 兼容性支持
 * <p>
 * 负责检测 ExtendedAE 是否存在，并在存在时注册相应的配方适配器
 */
public class ExtendedAECompat {
    
    private static final Logger LOGGER = LogManager.getLogger(ExtendedAECompat.class);
    private static final String MOD_ID = "extendedae";
    
    private static boolean isLoaded = false;
    
    /**
     * 检查 ExtendedAE 是否已加载
     */
    public static boolean isExtendedAELoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }
    
    /**
     * 初始化 ExtendedAE 兼容性支持
     * 应在 FMLCommonSetupEvent 中调用
     */
    public static void init(FMLCommonSetupEvent event) {
        if (!isExtendedAELoaded()) {
            return;
        }
        
        isLoaded = true;

        event.enqueueWork(() -> {
            try {
                registerAdapters();
            } catch (Exception e) {
                LOGGER.error("Failed to register ExtendedAE recipe adapters", e);
            }
        });
    }
    
    /**
     * 注册 ExtendedAE 配方适配器
     */
    private static void registerAdapters() {
        AlloyFurnaceRecipeManager recipeManager = AlloyFurnaceRecipeManager.getInstance();
        
        // 注册电路切片器配方适配器
        recipeManager.registerAdapter(new CircuitCutterRecipeAdapter());
        // 注册水晶装配器配方适配器
        recipeManager.registerAdapter(new CrystalAssemblerRecipeAdapter());
    }
    
    /**
     * 获取加载状态
     */
    public static boolean isLoaded() {
        return isLoaded;
    }
}
