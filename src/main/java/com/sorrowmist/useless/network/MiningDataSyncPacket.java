package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.data.PlayerMiningData;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record MiningDataSyncPacket(BlockPos cachedPos, List<BlockPos> cachedBlocks) implements CustomPacketPayload {

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
                return new MiningDataSyncPacket(pos, blocks);
            }
    );

    public static final Type<MiningDataSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "mining_data_sync")
    );

    public MiningDataSyncPacket(PlayerMiningData data) {
        this(data.getCachedPos(), data.getCachedBlocks());
    }

    public static void handle(MiningDataSyncPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() != null) {
                PlayerMiningData data = new PlayerMiningData(ctx.player().getUUID());
                data.setCachedPos(msg.cachedPos);
                data.setCachedBlocks(msg.cachedBlocks);
                MiningDispatcher.setClientPlayerData(data);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
