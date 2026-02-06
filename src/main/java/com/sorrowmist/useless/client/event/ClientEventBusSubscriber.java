package com.sorrowmist.useless.client.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.client.gui.MiningStatusGui;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.core.common.KeyBindings;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.ForceBreakKeyPacket;
import com.sorrowmist.useless.network.ModeTogglePacket;
import com.sorrowmist.useless.network.TabKeyPressedPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

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

        // 触发按键
        event.register(KeyBindings.TRIGGER_CHAIN_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY.get());

        // UI
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.player == null || mc.screen != null) return;

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

        // 检测R键按下（触发强制破坏）
        if (KeyBindings.TRIGGER_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                // R键按下，发送强制破坏请求，同时传入当前Tab键状态
                boolean tabPressed = KeyBindings.TRIGGER_CHAIN_MINING_KEY.get().isDown();
                PacketDistributor.sendToServer(new ForceBreakKeyPacket(tabPressed));
            }
        }
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "ultimine_status"),
                MiningStatusGui::render
        );
    }
}
