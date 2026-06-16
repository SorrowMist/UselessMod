package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tab键连锁挖掘切换数据包
 * 重构：使用 MiningDispatcher 管理玩家Tab键状态
 */
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
                MiningDispatcher.setTabPressed(player, packet.isPressed);
            }
        });
        context.get().setPacketHandled(true);
    }
}
