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

public record BeefToolLayoutResultPacket(BeefToolLayout.Error error) implements CustomPacketPayload {
    public static final Type<BeefToolLayoutResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "beef_tool_layout_result"));
    public static final StreamCodec<FriendlyByteBuf, BeefToolLayoutResultPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeEnum(packet.error),
                    buffer -> new BeefToolLayoutResultPacket(buffer.readEnum(BeefToolLayout.Error.class)));

    public static void handle(BeefToolLayoutResultPacket packet, IPayloadContext context) {
        // 客户端类型统一收敛到 ClientPacketHandlers，避免专用服务器加载类时解析到客户端类。
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> ClientPacketHandlers.handleBeefToolLayoutResult(packet));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
