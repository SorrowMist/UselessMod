package com.sorrowmist.useless.networking;

import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * R键强制破坏触发数据包
 * 重构：使用 MiningDispatcher 执行强制破坏
 */
public class TriggerForceMiningPacket {
    private final boolean tabPressed;

    public TriggerForceMiningPacket() {
        this(false);
    }

    public TriggerForceMiningPacket(boolean tabPressed) {
        this.tabPressed = tabPressed;
    }

    public static void encode(TriggerForceMiningPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.tabPressed);
    }

    public static TriggerForceMiningPacket decode(FriendlyByteBuf buffer) {
        return new TriggerForceMiningPacket(buffer.readBoolean());
    }

    public static void handle(TriggerForceMiningPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                MiningDispatcher.dispatchForceBreak(player, packet.tabPressed);
            }
        });
        context.get().setPacketHandled(true);
    }
}
