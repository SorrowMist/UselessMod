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
                buf.writeBoolean(pkt.chemical);
            },
            buf -> new TankClearPacket(buf.readBlockPos(), buf.readInt(), buf.readBoolean(), buf.readBoolean())
    );
    private final BlockPos pos;
    private final int tankIndex;
    private final boolean isInput;
    private final boolean chemical;

    public TankClearPacket(BlockPos pos, int tankIndex, boolean isInput) {
        this(pos, tankIndex, isInput, false);
    }

    public TankClearPacket(BlockPos pos, int tankIndex, boolean isInput, boolean chemical) {
        this.pos = pos;
        this.tankIndex = tankIndex;
        this.isInput = isInput;
        this.chemical = chemical;
    }

    public static void handle(TankClearPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof AdvancedAlloyFurnaceBlockEntity furnace) {
                if (msg.chemical) {
                    furnace.clearChemicalTank(msg.tankIndex, msg.isInput);
                } else {
                    furnace.clearFluidTank(msg.tankIndex, msg.isInput);
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
