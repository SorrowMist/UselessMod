package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** The round token prevents duplicate or delayed clicks from completing multiple targets. */
public record MaintenanceCalibrationActionPacket(int containerId, BlockPos pos, boolean restart,
                                                  long roundId, long reportedElapsed) implements CustomPacketPayload {
    public static final Type<MaintenanceCalibrationActionPacket> TYPE = new Type<>(UselessMod.id("maintenance_calibration_action"));
    public static final StreamCodec<FriendlyByteBuf, MaintenanceCalibrationActionPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.containerId);
                buf.writeBlockPos(packet.pos);
                buf.writeBoolean(packet.restart);
                buf.writeVarLong(packet.roundId);
                buf.writeVarLong(packet.reportedElapsed);
            }, buf -> new MaintenanceCalibrationActionPacket(buf.readVarInt(), buf.readBlockPos(),
                    buf.readBoolean(), buf.readVarLong(), buf.readVarLong()));

    public MaintenanceCalibrationActionPacket { pos = pos.immutable(); }

    public static void handle(MaintenanceCalibrationActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()
                    || !(player.containerMenu instanceof MaintenanceHatchMenu menu)
                    || menu.containerId != packet.containerId || !menu.getBlockPos().equals(packet.pos)
                    || !player.level().hasChunkAt(packet.pos) || !menu.stillValid(player)
                    || !(player.level().getBlockEntity(packet.pos) instanceof MaintenanceHatchBlockEntity hatch)) return;
            if (packet.restart) hatch.startCalibration(player.getUUID());
            else hatch.submitClick(player.getUUID(), packet.roundId, packet.reportedElapsed);
            MaintenanceCalibrationSyncPacket.sendTo(player, hatch);
        });
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
