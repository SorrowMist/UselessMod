package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TriggerForceMiningPacket {
    public TriggerForceMiningPacket() {
        // 这个数据包不需要任何数据，因为它只是一个触发信号
    }

    public static void encode(TriggerForceMiningPacket packet, FriendlyByteBuf buffer) {
        // 不需要编码任何数据
    }

    public static TriggerForceMiningPacket decode(FriendlyByteBuf buffer) {
        return new TriggerForceMiningPacket();
    }

    public static void handle(TriggerForceMiningPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                // 检查主手和副手物品
                ItemStack mainHandItem = player.getMainHandItem();
                ItemStack offHandItem = player.getOffhandItem();
                
                ItemStack targetItem = null;
                
                // 检查主手
                if (mainHandItem.getItem() instanceof EndlessBeafItem) {
                    targetItem = mainHandItem;
                } 
                // 检查副手
                else if (offHandItem.getItem() instanceof EndlessBeafItem) {
                    targetItem = offHandItem;
                }
                
                if (targetItem != null && targetItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 在服务端触发强制破坏
                    endlessBeaf.triggerForceMining(targetItem, player);
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}