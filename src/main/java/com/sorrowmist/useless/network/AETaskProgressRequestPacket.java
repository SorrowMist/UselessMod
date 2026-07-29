package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.menus.MultiblockAlloyFurnaceMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record AETaskProgressRequestPacket(int containerId, BlockPos pos)
        implements CustomPacketPayload {
    public static final Type<AETaskProgressRequestPacket> TYPE = new Type<>(
            UselessMod.id("ae_task_progress_request"));
    public static final StreamCodec<FriendlyByteBuf, AETaskProgressRequestPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {
                buffer.writeVarInt(packet.containerId);
                buffer.writeBlockPos(packet.pos);
            }, buffer -> new AETaskProgressRequestPacket(
                    buffer.readVarInt(), buffer.readBlockPos()));

    public AETaskProgressRequestPacket {
        pos = pos.immutable();
    }

    public static void handle(AETaskProgressRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof MultiblockAlloyFurnaceMenu menu)
                    || menu.containerId != packet.containerId
                    || !menu.getBlockPos().equals(packet.pos)
                    || !menu.stillValid(player)) {
                return;
            }
            MultiblockAlloyFurnaceCoreBlockEntity core = menu.getCore();
            if (core != null && core.getBlockPos().equals(packet.pos)) {
                core.sendAETaskProgressToPlayer(player);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
