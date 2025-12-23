package com.sorrowmist.useless.client;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.networking.ChainMiningTogglePacket;
import com.sorrowmist.useless.networking.EnchantmentSwitchPacket;
import com.sorrowmist.useless.networking.ModMessages;
import com.sorrowmist.useless.networking.TriggerForceMiningPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UselessMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEventBusSubscriber {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        // 检查玩家是否打开了GUI或正在使用物品
        if (minecraft.screen != null) {
            return;
        }
        
        if (KeyBindings.SWITCH_SILK_TOUCH_KEY.consumeClick()) {
            if (minecraft.player != null) {
                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    ModMessages.sendToServer(new EnchantmentSwitchPacket(true));
                }
            }
        }

        if (KeyBindings.SWITCH_FORTUNE_KEY.consumeClick()) {
            if (minecraft.player != null) {
                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    ModMessages.sendToServer(new EnchantmentSwitchPacket(false));
                }
            }
        }

        // 处理连锁挖掘按键的按下和松开事件
        if (KeyBindings.SWITCH_CHAIN_MINING_KEY.isDown() != KeyBindings.SWITCH_CHAIN_MINING_KEY_WAS_DOWN) {
            KeyBindings.SWITCH_CHAIN_MINING_KEY_WAS_DOWN = KeyBindings.SWITCH_CHAIN_MINING_KEY.isDown();
            if (minecraft.player != null) {
                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    UselessMod.NETWORK.sendToServer(new ChainMiningTogglePacket(KeyBindings.SWITCH_CHAIN_MINING_KEY.isDown()));
                }
            }
        }
        
        // 处理强制挖掘触发按键
        if (KeyBindings.TRIGGER_FORCE_MINING_KEY.consumeClick()) {
            if (minecraft.player != null) {
                ItemStack mainHandItem = minecraft.player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    ModMessages.sendToServer(new TriggerForceMiningPacket());
                }
            }
        }
    }
}

@Mod.EventBusSubscriber(modid = UselessMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
class ClientModBusSubscriber {
    @SubscribeEvent
    public static void onKeyRegister(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.SWITCH_SILK_TOUCH_KEY);
        event.register(KeyBindings.SWITCH_FORTUNE_KEY);
        event.register(KeyBindings.SWITCH_CHAIN_MINING_KEY);
        event.register(KeyBindings.SWITCH_ENHANCED_CHAIN_MINING_KEY);
        event.register(KeyBindings.SWITCH_MODE_WHEEL_KEY);
        event.register(KeyBindings.SWITCH_FORCE_MINING_KEY);
        event.register(KeyBindings.TRIGGER_FORCE_MINING_KEY);
        event.register(KeyBindings.SET_MASTER_PATTERN_KEY);
        event.register(KeyBindings.SET_SLAVE_PATTERN_KEY);
    }
}