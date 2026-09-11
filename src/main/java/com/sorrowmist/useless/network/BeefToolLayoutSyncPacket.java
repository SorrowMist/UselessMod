package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.network.ClientPacketHandlers;
import com.sorrowmist.useless.data.BeefToolLayout;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BeefToolLayoutSyncPacket(String json) implements CustomPacketPayload {
    public static final Type<BeefToolLayoutSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "beef_tool_layout_sync"));
    public static final StreamCodec<FriendlyByteBuf, BeefToolLayoutSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeUtf(packet.json, BeefToolLayout.MAX_TEXT_LENGTH),
                    buffer -> new BeefToolLayoutSyncPacket(buffer.readUtf(BeefToolLayout.MAX_TEXT_LENGTH)));

    public static void handle(BeefToolLayoutSyncPacket packet, IPayloadContext context) {
        // 客户端类型统一收敛到 ClientPacketHandlers，避免专用服务器加载类时解析到客户端类。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleBeefToolLayoutSync(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
