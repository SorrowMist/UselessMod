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

public class TankClearPacket implements CustomPacketPayload {

    public static final Type<TankClearPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "tank_clear"));
    public static final StreamCodec<FriendlyByteBuf, TankClearPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBlockPos(pkt.pos);
                buf.writeInt(pkt.tankIndex);
                buf.writeBoolean(pkt.isInput);
            },
            buf -> new TankClearPacket(buf.readBlockPos(), buf.readInt(), buf.readBoolean())
    );
    private final BlockPos pos;
    private final int tankIndex;
    private final boolean isInput;

    public TankClearPacket(BlockPos pos, int tankIndex, boolean isInput) {
        this.pos = pos;
        this.tankIndex = tankIndex;
        this.isInput = isInput;
    }

    public static void handle(TankClearPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                furnace.clearFluidTank(msg.tankIndex, msg.isInput);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
