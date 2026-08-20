package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.DimensionConfigMenu;
import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record DimensionConfigSubmitPacket(int containerId,
                                          DimensionGenerationConfig config,
                                          boolean teleportAfterSave)
        implements CustomPacketPayload {
    public static final Type<DimensionConfigSubmitPacket> TYPE = new Type<>(
            UselessMod.id("dimension_config_submit"));
    public static final StreamCodec<FriendlyByteBuf, DimensionConfigSubmitPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.containerId);
                packet.config.write(buffer);
                buffer.writeBoolean(packet.teleportAfterSave);
            }, buffer -> new DimensionConfigSubmitPacket(
                    buffer.readVarInt(), DimensionGenerationConfig.read(buffer), buffer.readBoolean()));

    public static void handle(DimensionConfigSubmitPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof DimensionConfigMenu menu)
                    || menu.containerId != packet.containerId
                    || !menu.stillValid(player)) {
                return;
            }
            menu.submit(player, packet.config, packet.teleportAfterSave());
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
