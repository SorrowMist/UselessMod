package com.sorrowmist.useless.registry;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.blocks.CreatorDollBlock;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.blocks.ModBlockEntities;
import com.sorrowmist.useless.blocks.ModMenuTypes;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.blocks.oregenerator.OreGeneratorBlock;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock2;
import com.sorrowmist.useless.blocks.teleport.TeleportBlock3;
import com.sorrowmist.useless.inventories.UselessTab;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.recipes.ModRecipeSerializers;
import com.sorrowmist.useless.recipes.ModRecipeTypes;
import com.sorrowmist.useless.utils.ThermalDependencyHelper;
import com.sorrowmist.useless.worldgen.dimension.UselessDimension;
import com.sorrowmist.useless.worldgen.dimension.UselessDimension2;
import com.sorrowmist.useless.worldgen.dimension.UselessDimension3;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;

public class RegistryHandler {
    // 存储所有需要注册的DeferredRegister
    private static final List<DeferredRegister<?>> REGISTRIES = new ArrayList<>();

    // 存储其他类型的注册器
    private static final List<Runnable> OTHER_REGISTRIES = new ArrayList<>();

    /**
     * 初始化所有注册
     */
    public static void initAll(IEventBus modEventBus) {
        // 收集所有需要注册的DeferredRegister
        collectAllRegistries();

        // 注册所有DeferredRegister
        for (DeferredRegister<?> registry : REGISTRIES) {
            registry.register(modEventBus);
        }

        // 注册其他类型的注册器
        for (Runnable registry : OTHER_REGISTRIES) {
            registry.run();
        }

        UselessMod.LOGGER.info("已注册 {} 个标准注册器和 {} 个其他注册器", REGISTRIES.size(), OTHER_REGISTRIES.size());
    }

    /**
     * 收集所有需要注册的DeferredRegister
     */
    // 在 RegistryHandler.java 的 collectAllRegistries() 方法中添加
    private static void collectAllRegistries() {
        // 物品注册
        addRegistry(EndlessBeafItem.ITEMS);

        // 创造模式标签注册
        addRegistry(UselessTab.CREATIVE_TAB);

        // 方块和物品注册
        addRegistry(TeleportBlock.BLOCKS);
        addRegistry(TeleportBlock.ITEMS);
        addRegistry(TeleportBlock2.BLOCKS);
        addRegistry(TeleportBlock2.ITEMS);
        addRegistry(TeleportBlock3.BLOCKS);
        addRegistry(TeleportBlock3.ITEMS);
        addRegistry(OreGeneratorBlock.BLOCKS);
        addRegistry(OreGeneratorBlock.ITEMS);
        addRegistry(GlowPlasticBlock.BLOCKS);
        addRegistry(GlowPlasticBlock.ITEMS);


        addRegistry(AdvancedAlloyFurnaceBlock.BLOCKS);
        addRegistry(AdvancedAlloyFurnaceBlock.ITEMS);
        addRegistry(CreatorDollBlock.BLOCKS);
        addRegistry(CreatorDollBlock.ITEMS);
        // 方块实体注册
        addRegistry(ModBlockEntities.BLOCK_ENTITIES);

        // 维度注册
        addRegistry(UselessDimension.CHUNK_GENERATORS);
        addRegistry(UselessDimension2.CHUNK_GENERATORS);
        addRegistry(UselessDimension3.CHUNK_GENERATORS);

        // 菜单注册
        addRegistry(ModMenuTypes.MENUS);

        // 添加锭的注册
        addRegistry(ModIngots.ITEMS);
        // 添加金属模具的注册
        addRegistry(ModMolds.ITEMS);
        // 添加齿轮和玻璃组件的注册
        addRegistry(ModComponents.ITEMS);



        addRegistry(ModRecipeTypes.RECIPE_TYPES);
        addRegistry(ModRecipeSerializers.RECIPE_SERIALIZERS);
        // 添加其他注册器
        if (ThermalDependencyHelper.isAnyThermalModLoaded()) {
            addOtherRegistry(() -> ThermalMoreItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()));
            addOtherRegistry(() -> ThermalParallelItems.ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus()));


            UselessMod.LOGGER.info("检测到热力系列模组，已注册 ThermalMore、ThermalParallel 和高级机器");
        } else {
            UselessMod.LOGGER.info("未检测到热力系列模组，跳过 Thermal 相关物品注册");
        }
    }

    /**
     * 安全地添加注册器到列表
     */
    private static <T> void addRegistry(DeferredRegister<T> registry) {
        if (registry != null) {
            REGISTRIES.add(registry);
        }
    }

    /**
     * 添加其他类型的注册器
     */
    private static void addOtherRegistry(Runnable registryTask) {
        if (registryTask != null) {
            OTHER_REGISTRIES.add(registryTask);
        }
    }

    /**
     * 获取注册器数量（用于调试）
     */
    public static int getRegistryCount() {
        return REGISTRIES.size() + OTHER_REGISTRIES.size();
    }
}