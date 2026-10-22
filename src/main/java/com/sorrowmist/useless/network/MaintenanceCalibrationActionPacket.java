package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import com.sorrowmist.useless.content.menus.MaintenanceHatchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端提交维护仓校准小游戏的两种操作：开始（或重开）一局、或报告一次点击。
 *
 * <p>点击上报的 {@code reportedElapsed} 是客户端视角下「本轮已流逝的 tick」。服务端只把它
 * 当作候选值，会先与自己观测到的流逝值比对容差，再由服务端推导指针位置并判定，
 * 因此客户端无法直接指定命中。</p>
 */
public record MaintenanceCalibrationActionPacket(int containerId,
                                                 BlockPos pos,
                                                 boolean restart,
                                                 long reportedElapsed) implements CustomPacketPayload {

    public static final Type<MaintenanceCalibrationActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "maintenance_calibration_action"));

    public static final StreamCodec<FriendlyByteBuf, MaintenanceCalibrationActionPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeVarInt(packet.containerId);
                        buffer.writeBlockPos(packet.pos);
                        buffer.writeBoolean(packet.restart);
                        buffer.writeVarLong(packet.reportedElapsed);
                    },
                    buffer -> new MaintenanceCalibrationActionPacket(
                            buffer.readVarInt(),
                            buffer.readBlockPos(),
                            buffer.readBoolean(),
                            buffer.readVarLong()));

    public MaintenanceCalibrationActionPacket {
        pos = pos.immutable();
    }

    public static void handle(MaintenanceCalibrationActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof MaintenanceHatchMenu menu)
                    || menu.containerId != packet.containerId
                    || !menu.getBlockPos().equals(packet.pos)
                    || !menu.stillValid(player)) {
                return;
            }
            if (!(player.level().getBlockEntity(packet.pos) instanceof MaintenanceHatchBlockEntity hatch)) {
                return;
            }

            if (packet.restart) {
                hatch.startCalibration();
            } else {
                hatch.submitClick(packet.reportedElapsed);
            }
            // 无论命中与否都回发权威局面，客户端据此重置计时基准与目标区。
            MaintenanceCalibrationSyncPacket.sendTo(player, hatch);
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
