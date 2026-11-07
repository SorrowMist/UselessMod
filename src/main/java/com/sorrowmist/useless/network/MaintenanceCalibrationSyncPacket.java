package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** Relative elapsed time avoids assuming that the server and client world clocks have the same origin. */
public record MaintenanceCalibrationSyncPacket(int containerId, BlockPos pos, byte phase, int roundsCleared,
                                               float targetCenter, long roundId, long elapsedTicks) implements CustomPacketPayload {
    public static final Type<MaintenanceCalibrationSyncPacket> TYPE = new Type<>(UselessMod.id("maintenance_calibration_sync"));
    public static final StreamCodec<FriendlyByteBuf, MaintenanceCalibrationSyncPacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> {
                buf.writeVarInt(packet.containerId);
                buf.writeBlockPos(packet.pos);
                buf.writeByte(packet.phase);
                buf.writeVarInt(packet.roundsCleared);
                buf.writeFloat(packet.targetCenter);
                buf.writeVarLong(packet.roundId);
                buf.writeVarLong(packet.elapsedTicks);
            }, buf -> new MaintenanceCalibrationSyncPacket(buf.readVarInt(), buf.readBlockPos(), buf.readByte(),
                    buf.readVarInt(), buf.readFloat(), buf.readVarLong(), buf.readVarLong()));

    public MaintenanceCalibrationSyncPacket { pos = pos.immutable(); }

    public static void sendTo(ServerPlayer player, MaintenanceHatchBlockEntity hatch) {
        if (!(player.containerMenu instanceof MaintenanceHatchMenu menu)
                || !menu.getBlockPos().equals(hatch.getBlockPos())) return;
        PacketDistributor.sendToPlayer(player, new MaintenanceCalibrationSyncPacket(menu.containerId, hatch.getBlockPos(),
                hatch.calibrationPhase(), hatch.roundsCleared(), hatch.targetCenter(), hatch.roundId(), hatch.elapsedTicks()));
    }

    public static void handle(MaintenanceCalibrationSyncPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist == Dist.CLIENT) context.enqueueWork(() -> ClientHandler.accept(packet));
    }

    /** Isolated client linkage: this outer payload is also loaded during dedicated-server registration. */
    private static final class ClientHandler {
        private static void accept(MaintenanceCalibrationSyncPacket packet) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft.player != null && minecraft.level != null
                    && minecraft.player.containerMenu instanceof MaintenanceHatchMenu menu
                    && menu.containerId == packet.containerId && menu.getBlockPos().equals(packet.pos)) {
                menu.receiveCalibration(packet, minecraft.level.getGameTime());
            }
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
