package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.ae.AeEntropyTransmutationHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端请求「我现在瞄的这个 ME 箱子能被熵变机械臂转化成什么方块」。
 *
 * <p>只读查询：服务端仅校验配方是否满足，<b>不扣除磁盘内容、不替换方块</b>，
 * 用于在准星指向时提前把「即将变成的方块」渲染成幽灵预览。</p>
 */
public record EntropyTransmutationPreviewRequestPacket(int requestId, BlockPos pos)
        implements CustomPacketPayload {
    public static final Type<EntropyTransmutationPreviewRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "entropy_transmutation_preview_request"));
    public static final StreamCodec<FriendlyByteBuf, EntropyTransmutationPreviewRequestPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeVarInt(packet.requestId);
                        buf.writeBlockPos(packet.pos);
                    },
                    buf -> new EntropyTransmutationPreviewRequestPacket(
                            buf.readVarInt(), buf.readBlockPos())
            );

    public static void handle(EntropyTransmutationPreviewRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            BlockState output = AeEntropyTransmutationHandler.previewOutput(level, packet.pos());
            int stateId = output == null ? -1 : Block.getId(output);
            PacketDistributor.sendToPlayer(player, new EntropyTransmutationPreviewPacket(
                    packet.requestId(), packet.pos(), stateId));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
