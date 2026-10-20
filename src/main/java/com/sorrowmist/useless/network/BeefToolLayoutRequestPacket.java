package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.data.BeefToolLayout;
import com.sorrowmist.useless.data.BeefToolLayoutManager;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.sorrowmist.useless.compat.forge1201.network.PacketDistributor;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BeefToolLayoutRequestPacket() implements CustomPacketPayload {
    public static final Type<BeefToolLayoutRequestPacket> TYPE = new Type<>(
            new ResourceLocation(UselessMod.MODID, "beef_tool_layout_request"));
    public static final StreamCodec<FriendlyByteBuf, BeefToolLayoutRequestPacket> STREAM_CODEC =
            StreamCodec.of((buffer, packet) -> {}, buffer -> new BeefToolLayoutRequestPacket());

    public static void handle(BeefToolLayoutRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || UselessItemUtils.findTargetToolInHands(player).isEmpty()) {
                return;
            }
            BeefToolLayout layout = BeefToolLayoutManager.loadOrCreate(player);
            PacketDistributor.sendToPlayer(player, new BeefToolLayoutSyncPacket(layout.toJson()));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
