package com.sorrowmist.useless.event;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.network.EnchantmentSwitchPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = UselessMod.MODID, value = Dist.CLIENT)
public class ClientEventBusSubscriber {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getKey() == GLFW.GLFW_KEY_PAGE_UP) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    PacketDistributor.sendToServer(new EnchantmentSwitchPacket(0));
                }
            }
        }
        if (event.getKey() == GLFW.GLFW_KEY_PAGE_DOWN) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    PacketDistributor.sendToServer(new EnchantmentSwitchPacket(1));
                }
            }
        }
    }
}