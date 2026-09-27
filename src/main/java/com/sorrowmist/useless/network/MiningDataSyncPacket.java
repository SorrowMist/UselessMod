package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import com.sorrowmist.useless.utils.mining.shape.ChainMiningShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record MiningDataSyncPacket(BlockPos cachedPos, List<BlockPos> cachedBlocks,
                                   ChainMiningShapes shape, int maxBlocks) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, MiningDataSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.cachedPos != null);
                if (pkt.cachedPos != null) {
                    buf.writeBlockPos(pkt.cachedPos);
                }
                buf.writeBoolean(pkt.cachedBlocks != null);
                if (pkt.cachedBlocks != null) {
                    buf.writeCollection(pkt.cachedBlocks,
                                        (friendlyBuf, blockPos) -> friendlyBuf.writeBlockPos(blockPos)
                    );
                }
                // 形状随缓存一并下发：客户端需要它来预测整片高亮与 HUD 显示，
                // 按标识字符串传输而非枚举序号，便于后续增删形状而不破坏兼容。
                buf.writeUtf(pkt.shape.getId().toString());
                // 数量上限来自服务端配置，客户端读不到，随包下发仅供界面显示
                buf.writeVarInt(pkt.maxBlocks);
            },
            buf -> {
                BlockPos pos = null;
                if (buf.readBoolean()) {
                    pos = buf.readBlockPos();
                }
                List<BlockPos> blocks = null;
                if (buf.readBoolean()) {
                    blocks = buf.readCollection(ArrayList::new, friendlyByteBuf -> friendlyByteBuf.readBlockPos());
                }
                ChainMiningShapes shape = ChainMiningShapes.byId(buf.readUtf());
                int maxBlocks = buf.readVarInt();
                return new MiningDataSyncPacket(pos, blocks, shape, maxBlocks);
            }
    );

    public static final Type<MiningDataSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "mining_data_sync")
    );

    public MiningDataSyncPacket(PlayerMiningData data) {
        this(data.getCachedPos(), data.getCachedBlocks(), data.getShape(), data.getMaxBlocks());
    }

    public static void handle(MiningDataSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() != null) {
                PlayerMiningData data = new PlayerMiningData(ctx.player().getUUID());
                data.setCachedPos(msg.cachedPos);
                data.setCachedBlocks(msg.cachedBlocks);
                data.setShape(msg.shape);
                data.setMaxBlocks(msg.maxBlocks);
                MiningDispatcher.setClientPlayerData(data);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
