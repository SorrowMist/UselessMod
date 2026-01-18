package com.sorrowmist.useless;

import appeng.api.features.GridLinkables;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.blocks.GlowPlasticBlock;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.dimension.UselessDimensions;
import com.sorrowmist.useless.init.*;
import com.sorrowmist.useless.items.EndlessBeafItem;
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
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);

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
        event.enqueueWork(() -> {
            GridLinkables.register(ModItems.ENDLESS_BEAF_ITEM.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(ModItems.ENDLESS_BEAF_WRENCH.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(ModItems.ENDLESS_BEAF_SCREWDRIVER.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(ModItems.ENDLESS_BEAF_MALLET.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(ModItems.ENDLESS_BEAF_CROWBAR.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(ModItems.ENDLESS_BEAF_HAMMER.get(), EndlessBeafItem.LINKABLE_HANDLER);
        });
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {}
}

