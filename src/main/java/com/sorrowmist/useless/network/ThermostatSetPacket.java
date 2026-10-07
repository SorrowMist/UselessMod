package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.ThermostatBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端提交「温度调节器」方块的温度 / 开关。
 *
 * <p>服务端是唯一校验点：方块必须是温度调节器、玩家必须在触及距离内。温度再由
 * {@link ThermostatBlockEntity#setTemperature(long)} 夹一次。</p>
 */
public record ThermostatSetPacket(BlockPos pos, long temperature, boolean enabled)
        implements CustomPacketPayload {

    public static final Type<ThermostatSetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "thermostat_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThermostatSetPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeBlockPos(packet.pos());
                        buffer.writeVarLong(packet.temperature());
                        buffer.writeBoolean(packet.enabled());
                    },
                    buffer -> new ThermostatSetPacket(
                            buffer.readBlockPos(), buffer.readVarLong(), buffer.readBoolean()));

    public static void handle(ThermostatSetPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            BlockPos pos = packet.pos();
            if (!(player.level().getBlockEntity(pos) instanceof ThermostatBlockEntity thermostat)) {
                return;
            }
            // 触及距离校验（与服务端菜单的 stillValid 同一把尺子）。
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
                return;
            }
            thermostat.setTemperature(packet.temperature());
            thermostat.setEnabled(packet.enabled());
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
