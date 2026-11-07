package com.sorrowmist.useless.network;

import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** A negative state ID clears the hint. Dimension and request ID reject stale replies. */
public record EntropyTransmutationPreviewPacket(int requestId, ResourceLocation dimension, BlockPos pos, int stateId)
        implements CustomPacketPayload {
    public static final Type<EntropyTransmutationPreviewPacket> TYPE = new Type<>(
            new ResourceLocation("useless_mod", "entropy_transmutation_preview"));
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationPreviewPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.requestId());
                buf.writeResourceLocation(packet.dimension());
                buf.writeBlockPos(packet.pos());
                buf.writeVarInt(packet.stateId());
            }, buf -> new EntropyTransmutationPreviewPacket(
                    buf.readVarInt(), buf.readResourceLocation(), buf.readBlockPos(), buf.readVarInt()));

    public EntropyTransmutationPreviewPacket { pos = pos.immutable(); }

    public static void handle(EntropyTransmutationPreviewPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.accept(packet)));
    }

    /** Deliberately not in the shared ClientPacketHandlers. Only resolved on the client distribution. */
    private static final class Client {
        static void accept(EntropyTransmutationPreviewPacket packet) {
            com.sorrowmist.useless.client.render.EntropyTransmutationPreviewRenderer.setPreview(
                    packet.requestId(), packet.dimension(), packet.pos(),
                    packet.stateId() >= 0 ? Block.stateById(packet.stateId()) : null);
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
