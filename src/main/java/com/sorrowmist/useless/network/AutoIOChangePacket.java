package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端→服务器：切换自动输入或自动输出开关。
 */
public class AutoIOChangePacket implements CustomPacketPayload {

    public static final Type<AutoIOChangePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "auto_io_change"));
    public static final StreamCodec<FriendlyByteBuf, AutoIOChangePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeBoolean(pkt.isOutput);
            },
            buf -> new AutoIOChangePacket(buf.readBlockPos(), buf.readBoolean())
    );
    private final BlockPos pos;
    private final boolean isOutput;

    /**
     * @param pos     方块位置
     * @param isOutput true=自动输出, false=自动输入
     */
    public AutoIOChangePacket(BlockPos pos, boolean isOutput) {
        this.pos = pos;
        this.isOutput = isOutput;
    }

    public static void handle(AutoIOChangePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                if (msg.isOutput) {
                    furnace.toggleAutoOutput();
                } else {
                    furnace.toggleAutoInput();
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
