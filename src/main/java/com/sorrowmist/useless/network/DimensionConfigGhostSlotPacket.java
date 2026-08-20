package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DimensionConfigGhostSlotPacket(int containerId, int slot, ResourceLocation blockId)
        implements CustomPacketPayload {
    public static final Type<DimensionConfigGhostSlotPacket> TYPE = new Type<>(
            UselessMod.id("dimension_config_ghost_slot"));
    public static final StreamCodec<FriendlyByteBuf, DimensionConfigGhostSlotPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.containerId);
                buffer.writeVarInt(packet.slot);
                buffer.writeResourceLocation(packet.blockId);
            }, buffer -> new DimensionConfigGhostSlotPacket(
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readResourceLocation()));

    public static void handle(DimensionConfigGhostSlotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DimensionConfigMenu menu)
                    || menu.containerId != packet.containerId
                    || !menu.stillValid(player)) {
                return;
            }
            if (menu.setGhostBlockId(packet.slot, packet.blockId)) {
                menu.broadcastChanges();
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
