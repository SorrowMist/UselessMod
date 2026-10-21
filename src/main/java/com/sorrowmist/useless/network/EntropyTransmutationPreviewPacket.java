package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.render.EntropyTransmutationPreviewRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端回答「这个 ME 箱子能被转化成什么方块」。
 *
 * <p>{@code stateId} 为 {@code -1} 表示当前不可转化（不满足配方或输出非方块），
 * 客户端据此清除幽灵预览。{@code stateId} 用 {@link Block#getId} / {@link Block#stateById}
 * 编解码，依赖双端共享的方块注册表，无需自定义序列化。</p>
 */
public record EntropyTransmutationPreviewPacket(int requestId, BlockPos pos, int stateId)
        implements CustomPacketPayload {
    public static final Type<EntropyTransmutationPreviewPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "entropy_transmutation_preview"));
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationPreviewPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.requestId);
                        buf.writeBlockPos(packet.pos);
                        buf.writeVarInt(packet.stateId);
                    },
                    buf -> new EntropyTransmutationPreviewPacket(
                            buf.readVarInt(), buf.readBlockPos(), buf.readVarInt())
            );

    public static void handle(EntropyTransmutationPreviewPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> EntropyTransmutationPreviewRenderer.setPreview(
                    packet.requestId(),
                    packet.stateId() >= 0 ? packet.pos() : null,
                    packet.stateId() >= 0 ? Block.stateById(packet.stateId()) : null));
        }
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
