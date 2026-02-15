package com.sorrowmist.useless;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.content.recipe.adapters.advancedae.AdvancedAECompat;
import com.sorrowmist.useless.content.recipe.adapters.ae2.AE2Compat;
import com.sorrowmist.useless.content.recipe.adapters.extendedae.ExtendedAECompat;
import com.sorrowmist.useless.content.recipe.adapters.industrialforegoing.IndustrialForegoingCompat;
import com.sorrowmist.useless.content.recipe.adapters.mekanism.MekanismCompat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.*;
import com.sorrowmist.useless.world.dimension.UselessDimensions;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(UselessMod.MODID)
public class UselessMod {
    public static final String MODID = "useless_mod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UselessMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        UComponents.init(modEventBus);

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuType.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);

        ModCreativeTabs.CREATIVE_TAB.register(modEventBus);

        modEventBus.addListener(ModNetwork::registerPayloadHandlers);

        GlowPlasticBlock.BLOCKS.register(modEventBus);
        GlowPlasticBlock.ITEMS.register(modEventBus);

        // 纬度注册
        UselessDimensions.init(modEventBus);

        NeoForge.EVENT_BUS.register(this);

        // 注册配置：接入自定义的 ModConfigs
        modContainer.registerConfig(ModConfig.Type.COMMON, ConfigManager.SPEC, "useless_mod-common.toml");
//        modContainer.registerConfig(ModConfig.Type.CLIENT, ConfigManager.CLIENT_SPEC, "useless_mod-client.toml");
//        modContainer.registerConfig(ModConfig.Type.SERVER, ConfigManager.SERVER_SPEC, "useless_mod-server.toml");
    }

    // 便捷 ResourceLocation 工具
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 初始化 ExtendedAE 兼容性支持（如果EAE已加载）
        ExtendedAECompat.init(event);
        // 初始化 AdvancedAE 兼容性支持（如果AAE已加载）
        AdvancedAECompat.init(event);
        // 初始化 Mekanism 兼容性支持（如果Mek已加载）
        MekanismCompat.init(event);
        // 初始化 AE2 兼容性支持（如果AE2已加载）
        AE2Compat.init(event);
        // 初始化 Industrial Foregoing 兼容性支持（如果IF已加载）
        IndustrialForegoingCompat.init(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}
}
