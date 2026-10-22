package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 维护仓校准小游戏的服务端 → 客户端状态快照。
 *
 * <p>下发的是「权威局面」：阶段、已通过轮数、目标中心，以及本轮已流逝的 tick 数。
 * 客户端以这份快照为计时基准继续本地推进指针，直到下一次快照覆盖它。</p>
 */
public record MaintenanceCalibrationSyncPacket(BlockPos pos,
                                               byte phase,
                                               int roundsCleared,
                                               float targetCenter,
                                               long elapsedTicks) implements CustomPacketPayload {

    public static final Type<MaintenanceCalibrationSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "maintenance_calibration_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MaintenanceCalibrationSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeBlockPos(packet.pos);
                        buffer.writeByte(packet.phase);
                        buffer.writeVarInt(packet.roundsCleared);
                        buffer.writeFloat(packet.targetCenter);
                        buffer.writeVarLong(packet.elapsedTicks);
                    },
                    buffer -> new MaintenanceCalibrationSyncPacket(
                            buffer.readBlockPos(),
                            buffer.readByte(),
                            buffer.readVarInt(),
                            buffer.readFloat(),
                            buffer.readVarLong()));

    public MaintenanceCalibrationSyncPacket {
        pos = pos.immutable();
    }

    /** 把某个维护仓的当前局面发给单个玩家。 */
    public static void sendTo(ServerPlayer player, MaintenanceHatchBlockEntity hatch) {
        PacketDistributor.sendToPlayer(player, new MaintenanceCalibrationSyncPacket(
                hatch.getBlockPos(),
                hatch.calibrationPhase(),
                hatch.roundsCleared(),
                hatch.targetCenter(),
                hatch.elapsedTicks()));
    }

    public static void handle(MaintenanceCalibrationSyncPacket packet, IPayloadContext context) {
        // 客户端类型收敛到 ClientPacketHandlers：本类会在专用服务端被加载。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleMaintenanceCalibrationSync(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}