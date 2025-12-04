package com.sorrowmist.useless;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.blocks.ModMenuTypes;
import com.sorrowmist.useless.blocks.advancedalloyfurnace.AdvancedAlloyFurnaceScreen;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.networking.ClearFluidPacket;
import com.sorrowmist.useless.networking.FluidInteractionPacket;

import com.sorrowmist.useless.networking.ChainMiningTogglePacket;
import com.sorrowmist.useless.networking.ModMessages;
import com.sorrowmist.useless.registry.RegistryHandler;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.TickEvent;
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

import java.util.*;
import java.util.concurrent.PriorityBlockingQueue;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(UselessMod.MOD_ID)
public class UselessMod {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "useless_mod";
    
    // 存储待挖掘的方块任务
    public static class MiningTask implements Comparable<MiningTask> {
        public ServerLevel level;
        public Player player;
        public ItemStack stack;
        public BlockPos pos;
        public BlockState state;
        public List<ItemStack> drops;
        public double distanceToPlayer; // 缓存到玩家的距离，避免重复计算
        
        public MiningTask(ServerLevel level, Player player, ItemStack stack, BlockPos pos, BlockState state, List<ItemStack> drops) {
            this.level = level;
            this.player = player;
            this.stack = stack;
            this.pos = pos;
            this.state = state;
            this.drops = drops;
            // 计算并缓存到玩家的距离平方（避免开方运算，提高性能）
            this.distanceToPlayer = pos.distSqr(player.blockPosition());
        }
        
        @Override
        public int compareTo(MiningTask other) {
            // 按距离玩家的远近排序，近的在前
            return Double.compare(this.distanceToPlayer, other.distanceToPlayer);
        }
    }
    
    // 待挖掘的方块队列 - 使用 PriorityBlockingQueue 实现线程安全的优先队列
    public static Queue<MiningTask> pendingMiningTasks = new PriorityBlockingQueue<>();
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
    
        NETWORK.registerMessage(packetId++, ChainMiningTogglePacket.class,
                ChainMiningTogglePacket::encode, ChainMiningTogglePacket::decode,
                ChainMiningTogglePacket::handle);
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
     * 服务器Tick事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        // 处理待挖掘的方块任务
        if (event.phase == TickEvent.Phase.END) {
            MiningTask task;
            while ((task = pendingMiningTasks.poll()) != null) {
                // 处理挖掘任务 - 使用setBlockToAir而不是destroyBlock，避免重复处理掉落物
                task.level.setBlock(task.pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                // 给予玩家掉落物
                for (ItemStack drop : task.drops) {
                    if (!task.player.addItem(drop)) {
                        // 如果玩家物品栏满了，将物品掉落
                        task.level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(task.level, task.player.getX(), task.player.getY(), task.player.getZ(), drop));
                    }
                }
            }
        }
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
