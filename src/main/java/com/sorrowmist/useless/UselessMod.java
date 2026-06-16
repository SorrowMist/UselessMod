package com.sorrowmist.useless;

import appeng.api.features.GridLinkables;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.blocks.ModMenuTypes;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceScreen;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.networking.ChainMiningTogglePacket;
import com.sorrowmist.useless.networking.ClearFluidPacket;
import com.sorrowmist.useless.networking.FluidInteractionPacket;
import com.sorrowmist.useless.networking.ModMessages;
import com.sorrowmist.useless.registry.RegistryHandler;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.nio.file.Path;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(UselessMod.MOD_ID)
public class UselessMod {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "useless_mod";
    
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public UselessMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 初始化所有内容
        RegistryHandler.initAll(modEventBus);

        // 注册配置
        registerConfig();
        modEventBus.addListener(this::onCommonSetup);
        // 注册网络消息
        ModMessages.register();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        // Register EndlessBeafItem for its block break event listeners
        MinecraftForge.EVENT_BUS.register(EndlessBeafItem.class);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
        // 注册内置材质包
        modEventBus.addListener(this::setupBuiltinPack);
//        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigManager.SPEC);
        // 注册 Mixin 配置
        Mixins.addConfiguration("useless_mod.mixins.json");
        
        // 在mod构造阶段注册GridLinkableHandler，确保在无线访问点槽位创建之前完成注册
        // 这里使用事件队列来确保在所有物品注册完成后执行
        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            event.enqueueWork(() -> {
                try {
                        // 注册EndlessBeafItem的GridLinkableHandler，使其可以与无线访问点绑定
                        GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_ITEM.get(), EndlessBeafItem.LINKABLE_HANDLER);
                        GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_WRENCH.get(), EndlessBeafItem.LINKABLE_HANDLER);
                        GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_SCREWDRIVER.get(), EndlessBeafItem.LINKABLE_HANDLER);
                        GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_MALLET.get(), EndlessBeafItem.LINKABLE_HANDLER);
                        GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_CROWBAR.get(), EndlessBeafItem.LINKABLE_HANDLER);
                        GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_HAMMER.get(), EndlessBeafItem.LINKABLE_HANDLER);
                    } catch (Exception e) {
                        // Ignore exception if GridLinkables registration fails
                    }
            });
        });
    }

    /**
     * 注册配置
     */
    private void registerConfig() {
        // 注册通用配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigManager.SPEC, "useless_mod-common.toml");
    }

    // 在模组主类中添加网络注册
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"),
            () -> "1.0",
            "1.0"::equals,
            "1.0"::equals
    );


    public void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            int packetId = 0;
            NETWORK.registerMessage(packetId++, FluidInteractionPacket.class,
                    FluidInteractionPacket::encode, FluidInteractionPacket::decode,
                    FluidInteractionPacket::handle);
            NETWORK.registerMessage(packetId++, ClearFluidPacket.class,
                    ClearFluidPacket::encode, ClearFluidPacket::decode,
                    ClearFluidPacket::handle);
    
        NETWORK.registerMessage(packetId++, ChainMiningTogglePacket.class,
                ChainMiningTogglePacket::encode, ChainMiningTogglePacket::decode,
                ChainMiningTogglePacket::handle);
        });
    }
    /**
     * 通用设置
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 注册EndlessBeafItem的GridLinkableHandler，使其可以与无线访问点绑定
        event.enqueueWork(() -> {
            GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_ITEM.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_WRENCH.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_SCREWDRIVER.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_MALLET.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_CROWBAR.get(), EndlessBeafItem.LINKABLE_HANDLER);
            GridLinkables.register(EndlessBeafItem.ENDLESS_BEAF_HAMMER.get(), EndlessBeafItem.LINKABLE_HANDLER);
        });
    }

    /**
     * 添加创造模式标签内容
     */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 这里可以添加物品到创造模式标签
    }

    /**
     * 服务器启动事件
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // 服务器启动时执行的操作
    }

    /**
     * 客户端事件订阅器
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 客户端设置
            // 注册物品模型属性
            event.enqueueWork(() -> {
                registerItemModelProperties();
            });

            // 注册屏幕
            event.enqueueWork(() -> {
                MenuScreens.register(ModMenuTypes.ADVANCED_ALLOY_FURNACE_MENU.get(), AdvancedAlloyFurnaceScreen::new);
            });
        }
        private static void registerItemModelProperties() {
            // 为所有EndlessBeafItem注册模型属性，包括主物品和所有子物品
            ResourceLocation silkTouchMode = ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "silk_touch_mode");
            
            // 注册主物品模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_ITEM.get(),
                    silkTouchMode,
                    (stack, level, entity, seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
            
            // 注册扳手模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_WRENCH.get(),
                    silkTouchMode,
                    (stack, level, entity, seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
            
            // 注册螺丝刀模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_SCREWDRIVER.get(),
                    silkTouchMode,
                    (stack, level, entity, seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
            
            // 注册锤子模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_MALLET.get(),
                    silkTouchMode,
                    (stack, level, entity, seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
            
            // 注册撬棍模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_CROWBAR.get(),
                    silkTouchMode,
                    (stack, level, entity, seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
            
            // 注册铁锤模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_HAMMER.get(),
                    silkTouchMode,
                    (stack, level, entity, seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });
        }
    }
    // 调试模式
    public static final boolean DEBUG = true;

    private void setupBuiltinPack(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            // 注册god内置材质包
            Path resourcePath = ModList.get().getModFileById(UselessMod.MOD_ID).getFile().findResource("god");
            if (resourcePath != null) {
                PathPackResources pack = new PathPackResources(
                        ModList.get().getModFileById(UselessMod.MOD_ID).getFile().getFileName() + ":" + resourcePath,
                        resourcePath,
                        true
                );

                PackMetadataSection metadata = new PackMetadataSection(
                        Component.translatable("pack.useless_mod.god.description"),
                        SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)
                );

                event.addRepositorySource(source ->
                        source.accept(Pack.create(
                                "builtin/god",
                                Component.translatable("pack.useless_mod.god.name"),
                                false,
                                string -> pack,
                                new Pack.Info(
                                        metadata.getDescription(),
                                        metadata.getPackFormat(PackType.SERVER_DATA),
                                        metadata.getPackFormat(PackType.CLIENT_RESOURCES),
                                        FeatureFlagSet.of(),
                                        pack.isHidden()
                                ),
                                PackType.CLIENT_RESOURCES,
                                Pack.Position.TOP,
                                false,
                                PackSource.BUILT_IN
                        ))
                );
            }
        }
    }

    public static void logDebug(String message) {
        if (DEBUG) {
            LOGGER.debug("[USELESS DEBUG] " + message);
        }
    }
}
