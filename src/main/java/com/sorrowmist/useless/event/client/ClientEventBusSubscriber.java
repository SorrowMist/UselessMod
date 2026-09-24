package com.sorrowmist.useless.event.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.client.BeefAutoClicker;
import com.sorrowmist.useless.client.gui.MiningStatusGui;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.blocks.TeleportPadBlock;
import com.sorrowmist.useless.content.items.BeefTimeAcceleration;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.event.EventHandler;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.ForceBreakKeyPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.TabKeyPressedPacket;
import com.sorrowmist.useless.network.TeleportKeyPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientEventBusSubscriber {
    // 跟踪Tab键的前一状态
    private static boolean lastTabPressed = false;

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        // 附魔切换
        event.register(KeyBindings.SWITCH_SILK_TOUCH_KEY.get());
        event.register(KeyBindings.SWITCH_FORTUNE_KEY.get());

        // 模式开关
        event.register(KeyBindings.TOGGLE_CHAIN_MODE_KEY.get());
        event.register(KeyBindings.SWITCH_FORCE_MINING_KEY.get());
        event.register(KeyBindings.SWITCH_FARMLAND_MODE_KEY.get());
        event.register(KeyBindings.TOGGLE_CROP_HARVEST_KEY.get());
        // 这两个按键此前只被 onKeyInput / tooltip 使用，却漏了注册，导致按键实际不生效
        event.register(KeyBindings.TOGGLE_SHEARS_KEY.get());
        event.register(KeyBindings.TOGGLE_FLINT_AND_STEEL_KEY.get());
        // 连点模式（默认未绑定）
        event.register(KeyBindings.TOGGLE_AUTO_CLICK_KEY.get());

        // 触发按键
        event.register(KeyBindings.TRIGGER_CHAIN_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY.get());

        // 短距传送（造化杖）
        event.register(KeyBindings.SHORT_TELEPORT_KEY.get());

        // UI
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (mc.screen != null) return;

        // 检测Tab键状态变化
        boolean currentTabPressed = KeyBindings.TRIGGER_CHAIN_MINING_KEY.get().isDown();
        if (currentTabPressed != lastTabPressed) {
            PacketDistributor.sendToServer(new TabKeyPressedPacket(currentTabPressed));
            lastTabPressed = currentTabPressed;
        }

        if (KeyBindings.SWITCH_FORTUNE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantMode.FORTUNE));
            }
        }

        if (KeyBindings.SWITCH_SILK_TOUCH_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantMode.SILK_TOUCH));
            }
        }

        if (KeyBindings.TOGGLE_CHAIN_MODE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换连锁挖矿模式
                boolean currentEnabled = mainHandItem.getOrDefault(UComponents.EnhancedChainMiningComponent.get(),
                                                                   false
                );
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.CHAIN_MINING, !currentEnabled));
            }
        }

        if (KeyBindings.SWITCH_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换强制挖掘模式
                boolean currentEnabled = mainHandItem.getOrDefault(UComponents.ForceMiningComponent.get(), false);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.FORCE_MINING, !currentEnabled));
            }
        }

        if (KeyBindings.SWITCH_FARMLAND_MODE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换右键泥土的结果：草径(铲子) <-> 耕地(锄头)
                boolean currentFarmland = EndlessBeafItem.isFarmlandMode(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_FARMLAND_MODE, !currentFarmland));
            }
        }

        if (KeyBindings.TOGGLE_CROP_HARVEST_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换「顺手收菜」（右键成熟作物：收获并保留种子于耕地）
                boolean currentHarvest = EndlessBeafItem.isCropHarvestEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_CROP_HARVEST, !currentHarvest));
            }
        }

        if (KeyBindings.TOGGLE_SHEARS_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换剪刀功能（剪羊毛 / 剪掉落，并对外声明剪刀能力）
                boolean currentShears = EndlessBeafItem.isShearsEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_SHEARS, !currentShears));
            }
        }

        if (KeyBindings.TOGGLE_FLINT_AND_STEEL_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换打火石功能（点亮营火/蜡烛，或在点击面点火）
                boolean currentFlintAndSteel = EndlessBeafItem.isFlintAndSteelEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_FLINT_AND_STEEL,
                                !currentFlintAndSteel));
            }
        }

        if (KeyBindings.TOGGLE_AUTO_CLICK_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // 切换连点模式（开启后客户端以最快速度重复触发右键，再按一次关闭）
                boolean currentAutoClick = EndlessBeafItem.isAutoClickEnabled(mainHandItem);
                PacketDistributor.sendToServer(
                        new ModeTogglePacket(ModeTogglePacket.ModeType.BEEF_AUTO_CLICK, !currentAutoClick));
            }
        }

        // 检测R键按下（触发强制破坏）
        if (KeyBindings.TRIGGER_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem
                    && mainHandItem.getOrDefault(UComponents.ForceMiningComponent.get(), false)) {
                // R键按下，发送强制破坏请求，同时传入当前Tab键状态
                boolean tabPressed = KeyBindings.TRIGGER_CHAIN_MINING_KEY.get().isDown();
                PacketDistributor.sendToServer(new ForceBreakKeyPacket(tabPressed));
            }
        }

        // 连点模式：手持造化杖时按配置速率重复触发右键（开关本身走 ModeTogglePacket）
        BeefAutoClicker.tick(mc);
    }

    /**
     * 短距传送的鼠标触发入口。
     *
     * <p>该绑定挂在恒为非激活的冲突上下文上，不参与按键分发，因此按下时原版 keyUse
     * 仍能取得点击，方块交互不受影响；此处只上报传送请求，不取消该事件。
     * 是否触发取决于绑定自身配置的主键与修饰键，玩家在按键设置中的改动即时生效。</p>
     */
    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;

        KeyMapping mapping = KeyBindings.SHORT_TELEPORT_KEY.get();
        InputConstants.Key pressedKey = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (!pressedKey.equals(mapping.getKey())) return;
        if (!mapping.getKeyModifier().isActive(mapping.getKeyConflictContext())) return;

        ItemStack mainHandItem = player.getMainHandItem();
        if (!(mainHandItem.getItem() instanceof EndlessBeafItem)) return;
        if (!EndlessBeafItem.isTeleportEnabled(mainHandItem)) return;
        // 时间加速同样绑定 Shift + 右键，两者不可同时触发：启用时交由方块交互处理。
        if (BeefTimeAcceleration.shouldBlockOtherRightClick(mainHandItem, player)) return;
        // 仅当准星命中的方块自身要独占该组合键时让位，普通方块不拦截闪现。
        if (isBlockInteractionPriority(mc)) return;

        PacketDistributor.sendToServer(new TeleportKeyPacket());
    }

    /**
     * 判断准星命中的方块是否要独占该组合键。
     *
     * <p>仅三类既有交互需要让位：维度传送方块（潜行右键编辑配置）、合金炉核心
     * （潜行右键自动搭建）与无线接入点（潜行右键绑定目标）。其余方块不消费该组合键，
     * 闪现照常执行。未命中方块时按未命中处理。</p>
     */
    private static boolean isBlockInteractionPriority(Minecraft mc) {
        Level level = mc.level;
        if (level == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        if (level.getBlockState(pos).getBlock() instanceof TeleportPadBlock) {
            return true;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MultiblockAlloyFurnaceCoreBlockEntity) {
            return true;
        }
        return blockEntity != null
                && blockEntity.getClass().getName().contains("WirelessAccessPoint");
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "ultimine_status"),
                MiningStatusGui::render
        );
    }

    @SubscribeEvent
    public static void onClientLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        lastTabPressed = false;
    }

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearClientBeefProtectionState();
    }

    @SubscribeEvent
    public static void onClientPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        lastTabPressed = false;
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            EventHandler.setClientBeefAdvancedStealthState(player.getId(), false);
        }
    }

    private static void clearClientBeefProtectionState() {
        EventHandler.clearClientBeefAdvancedStealthStates();
        lastTabPressed = false;
    }
}
