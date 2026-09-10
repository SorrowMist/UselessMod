package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.data.BeefToolLayoutManager;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BeefToolLayoutUpdatePacket(String json) implements CustomPacketPayload {
    public static final Type<BeefToolLayoutUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "beef_tool_layout_update"));
    public static final StreamCodec<FriendlyByteBuf, BeefToolLayoutUpdatePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeUtf(packet.json, BeefToolLayout.MAX_TEXT_LENGTH),
                    buffer -> new BeefToolLayoutUpdatePacket(buffer.readUtf(BeefToolLayout.MAX_TEXT_LENGTH)));

    public static void handle(BeefToolLayoutUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (UselessItemUtils.findTargetToolInHands(player).isEmpty()) {
                sendError(player, BeefToolLayout.Error.INVALID_STRUCTURE);
                return;
            }

            try {
                BeefToolLayout layout = BeefToolLayout.fromJson(packet.json);
                BeefToolLayoutManager.validateForSave(layout);
                BeefToolLayoutManager.normalizeForPlayer(player, layout);
                BeefToolLayoutManager.validateForSave(layout);
                BeefToolLayoutManager.save(player, layout);
                PacketDistributor.sendToPlayer(player, new BeefToolLayoutSyncPacket(layout.toJson()));
            } catch (BeefToolLayout.LayoutException exception) {
                sendError(player, exception.error());
            }
        });
    }

    private static void sendError(ServerPlayer player, BeefToolLayout.Error error) {
        PacketDistributor.sendToPlayer(player, new BeefToolLayoutResultPacket(error));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
