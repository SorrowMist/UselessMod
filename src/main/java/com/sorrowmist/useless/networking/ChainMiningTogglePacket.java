package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ChainMiningTogglePacket {
    private final boolean isPressed;
    
    public ChainMiningTogglePacket(boolean isPressed) {
        this.isPressed = isPressed;
    }

    public static void encode(ChainMiningTogglePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.isPressed);
    }

    public static ChainMiningTogglePacket decode(FriendlyByteBuf buffer) {
        return new ChainMiningTogglePacket(buffer.readBoolean());
    }

    public static void handle(ChainMiningTogglePacket packet, Supplier<NetworkEvent.Context> context) {
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
                    // 保存当前的增强连锁模式状态
                    boolean enhancedChainMining = endlessBeaf.isEnhancedChainMiningMode(targetItem);
                    
                    // 在服务端设置连锁挖掘的临时状态
                    endlessBeaf.setChainMiningPressedState(targetItem, packet.isPressed);
                    
                    // 确保增强连锁模式状态被保留
                    endlessBeaf.setEnhancedChainMiningMode(targetItem, enhancedChainMining);
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}