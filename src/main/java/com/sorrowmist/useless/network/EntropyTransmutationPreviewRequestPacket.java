package com.sorrowmist.useless.network;

import com.sorrowmist.useless.compat.ae.AeEntropyTransmutationHandler;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.WeakHashMap;

/** Only a read-only hint request; the client never supplies a recipe, result, or inventory. */
public record EntropyTransmutationPreviewRequestPacket(int requestId, ResourceLocation dimension, BlockPos pos)
        implements CustomPacketPayload {
    private static final Map<ServerPlayer, Long> LAST_QUERY_TICK = new WeakHashMap<>();
    public static final Type<EntropyTransmutationPreviewRequestPacket> TYPE = new Type<>(
            new ResourceLocation("useless_mod", "entropy_transmutation_preview_request"));
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationPreviewRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.requestId());
                buf.writeResourceLocation(packet.dimension());
                buf.writeBlockPos(packet.pos());
            }, buf -> new EntropyTransmutationPreviewRequestPacket(
                    buf.readVarInt(), buf.readResourceLocation(), buf.readBlockPos()));

    public EntropyTransmutationPreviewRequestPacket { pos = pos.immutable(); }

    public static void handle(EntropyTransmutationPreviewRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            var level = player.serverLevel();
            long tick = level.getGameTime();
            Long last = LAST_QUERY_TICK.get(player);
            if (last != null && tick >= last && tick - last < 4) return;
            LAST_QUERY_TICK.put(player, tick);
            var output = packet.dimension().equals(level.dimension().location())
                    ? AeEntropyTransmutationHandler.previewOutput(level, packet.pos(), player) : null;
            PacketDistributor.sendToPlayer(player, new EntropyTransmutationPreviewPacket(
                    packet.requestId(), packet.dimension(), packet.pos(), output == null ? -1 : Block.getId(output)));
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
