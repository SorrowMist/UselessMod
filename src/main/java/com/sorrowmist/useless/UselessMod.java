package com.sorrowmist.useless;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.blocks.ModMenuTypes;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceScreen;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.networking.ClearFluidPacket;
import com.sorrowmist.useless.networking.FluidInteractionPacket;
import com.sorrowmist.useless.networking.ModMessages;
import com.sorrowmist.useless.registry.RegistryHandler;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
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

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);
//        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigManager.SPEC);
        // 注册 Mixin 配置
        Mixins.addConfiguration("useless_mod.mixins.json");
    }

    /**
     * 注册配置
     */
    private void registerConfig() {
        // 注册通用配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ConfigManager.SPEC, "useless_mod-common.toml");
        LOGGER.info("已注册 TOML 配置文件");
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
        });
    }
    /**
     * 通用设置
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 在通用设置中记录配置信息
        LOGGER.info("植物盆生长倍率: {}", ConfigManager.getBotanyPotGrowthMultiplier());
        LOGGER.info("边框方块: {}", ConfigManager.getBorderBlock());
        LOGGER.info("填充方块: {}", ConfigManager.getFillBlock());
        LOGGER.info("中心方块: {}", ConfigManager.getCenterBlock());
        // 添加矩阵样板数量到日志
        LOGGER.info("矩阵样板数量: {}", ConfigManager.getMatrixPatternCount());
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
        LOGGER.info("HELLO from server starting");
        LOGGER.info("服务器配置 - 植物盆生长倍率: {}", ConfigManager.getBotanyPotGrowthMultiplier());
        // 添加矩阵样板数量到日志
        LOGGER.info("服务器配置 - 矩阵样板数量: {}", ConfigManager.getMatrixPatternCount());
    }

    /**
     * 客户端事件订阅器
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            // 客户端设置
            LOGGER.info("客户端配置 - 植物盆生长倍率: {}", ConfigManager.getBotanyPotGrowthMultiplier());
            // 添加矩阵样板数量到日志
            LOGGER.info("客户端配置 - 矩阵样板数量: {}", ConfigManager.getMatrixPatternCount());
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
            // 注册 EndlessBeafItem 的模型属性
            ItemProperties.register(EndlessBeafItem.ENDLESS_BEAF_ITEM.get(),
                    ResourceLocation.fromNamespaceAndPath(UselessMod.MOD_ID, "silk_touch_mode"),
                    (ItemStack stack, net.minecraft.client.multiplayer.ClientLevel level,
                     net.minecraft.world.entity.LivingEntity entity, int seed) -> {
                        if (stack.getItem() instanceof EndlessBeafItem item) {
                            return item.isSilkTouchMode(stack) ? 1.0F : 0.0F;
                        }
                        return 0.0F;
                    });

            LOGGER.info("已注册物品模型属性");
        }
    }
    // 调试模式
    public static final boolean DEBUG = true;



    public static void logDebug(String message) {
        if (DEBUG) {
            LOGGER.debug("[USELESS DEBUG] " + message);
        }
    }
}
