package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.OreGeneratorBlockEntity;
import com.sorrowmist.useless.content.menus.OreGeneratorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OreGeneratorOutputTogglePacket(int containerId, BlockPos pos)
        implements CustomPacketPayload {
    public static final Type<OreGeneratorOutputTogglePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "ore_generator_output_toggle"));
    public static final StreamCodec<FriendlyByteBuf, OreGeneratorOutputTogglePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.containerId);
                buf.writeBlockPos(packet.pos);
            }, buf -> new OreGeneratorOutputTogglePacket(buf.readVarInt(), buf.readBlockPos()));

    public static void handle(OreGeneratorOutputTogglePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof OreGeneratorMenu menu)
                    || menu.containerId != packet.containerId
                    || !menu.getBlockPos().equals(packet.pos)
                    || !menu.stillValid(player)) {
                return;
            }
            OreGeneratorBlockEntity generator = menu.getGenerator();
            if (generator == null) return;
            generator.setOutputToAe(!generator.isOutputToAe());
            menu.broadcastChanges();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
