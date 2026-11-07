package com.sorrowmist.useless.network;

import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Sent only after a completed server-side transformation, to clients tracking the target chunk. */
public record EntropyTransmutationEffectPacket(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
    public static final Type<EntropyTransmutationEffectPacket> TYPE = new Type<>(
            new ResourceLocation("useless_mod", "entropy_transmutation_effect"));
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationEffectPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeResourceLocation(packet.dimension());
                buf.writeBlockPos(packet.pos());
            }, buf -> new EntropyTransmutationEffectPacket(buf.readResourceLocation(), buf.readBlockPos()));

    public EntropyTransmutationEffectPacket { pos = pos.immutable(); }

    public static void handle(EntropyTransmutationEffectPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.accept(packet)));
    }

    private static final class Client {
        static void accept(EntropyTransmutationEffectPacket packet) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.level != null && minecraft.level.dimension().location().equals(packet.dimension())) {
                com.sorrowmist.useless.client.render.EntropyTransmutationPreviewRenderer.triggerEffect(packet.pos());
            }
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
