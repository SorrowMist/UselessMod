package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.common.KeyBindings;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import com.sorrowmist.useless.network.FunctionModeTogglePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientEventBusSubscriber {

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.SWITCH_SILK_TOUCH_KEY.get());
        event.register(KeyBindings.SWITCH_FORTUNE_KEY.get());
        event.register(KeyBindings.SWITCH_CHAIN_MINING_KEY.get());
        event.register(KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY.get());
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY.get());
        event.register(KeyBindings.SWITCH_FORCE_MINING_KEY.get());
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY.get());
    }

    @SubscribeEvent
    public static void onKeyInput(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.player == null || mc.screen != null) return;

        if (KeyBindings.SWITCH_FORTUNE_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantmentSwitchPacket.EnchantmentMode.FORTUNE));
            }
        }

        if (KeyBindings.SWITCH_SILK_TOUCH_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(
                        new EnchantmentSwitchPacket(EnchantmentSwitchPacket.EnchantmentMode.SILK_TOUCH));
            }
        }

        if (KeyBindings.SWITCH_CHAIN_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(new FunctionModeTogglePacket(FunctionMode.CHAIN_MINING));
            }
        }

        if (KeyBindings.SWITCH_CHAIN_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(new FunctionModeTogglePacket(FunctionMode.CHAIN_MINING));
            }
        }

        if (KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(new FunctionModeTogglePacket(FunctionMode.ENHANCED_CHAIN_MINING));
            }
        }

        if (KeyBindings.SWITCH_FORCE_MINING_KEY.get().consumeClick()) {
            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                PacketDistributor.sendToServer(new FunctionModeTogglePacket(FunctionMode.FORCE_MINING));
            }
        }
    }
}