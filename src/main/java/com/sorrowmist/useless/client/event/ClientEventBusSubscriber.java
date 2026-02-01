package com.sorrowmist.useless.client.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.client.gui.MiningStatusGui;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
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

    private static final ResourceLocation MY_ULTIMINE_LAYER = ResourceLocation.fromNamespaceAndPath("mymod",
                                                                                                    "ultimine_status"
    );

    // 跟踪Tab键的前一状态
    private static boolean lastTabPressed = false;

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.SWITCH_SILK_TOUCH_KEY.get());
        event.register(KeyBindings.SWITCH_FORTUNE_KEY.get());
        event.register(KeyBindings.TOGGLE_CHAIN_MODE_KEY.get());
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY.get());
        event.register(KeyBindings.SWITCH_FORCE_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_CHAIN_MINING_KEY.get());
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
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                MY_ULTIMINE_LAYER,
                MiningStatusGui::render
        );
    }
}
