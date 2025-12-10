package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ForceMiningTogglePacket {
    public ForceMiningTogglePacket() {
        // 这个数据包不需要任何数据，因为它只是一个切换信号
    }

    public static void encode(ForceMiningTogglePacket packet, FriendlyByteBuf buffer) {
        // 不需要编码任何数据
    }

    public static ForceMiningTogglePacket decode(FriendlyByteBuf buffer) {
        return new ForceMiningTogglePacket();
    }

    public static void handle(ForceMiningTogglePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                ItemStack mainHandItem = player.getMainHandItem();
                if (mainHandItem.getItem() instanceof EndlessBeafItem endlessBeaf) {
                    // 在服务端切换强制挖掘模式
                    boolean newMode = endlessBeaf.toggleForceMiningMode(mainHandItem);
                    
                    // 发送消息给玩家（只在服务端发送，确保一致性）
                    String messageKey = newMode ? "message.useless_mod.force_mining_on" : "message.useless_mod.force_mining_off";
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(messageKey), true);
                }
            }
        });
        context.get().setPacketHandled(true);
    }
}