package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端请求「我手上这把造化杖绑定的访问点，已经连了哪些机器」。
 *
 * <p>不带参数：服务端自己从玩家手里找工具、读绑定坐标，客户端无从伪造目标。</p>
 */
public record AeLinkPreviewRequestPacket() implements CustomPacketPayload {
    public static final Type<AeLinkPreviewRequestPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "ae_link_preview_request"));
    public static final StreamCodec<FriendlyByteBuf, AeLinkPreviewRequestPacket> STREAM_CODEC =
            StreamCodec.unit(new AeLinkPreviewRequestPacket());

    public static void handle(AeLinkPreviewRequestPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                AeLinkPreviewPacket.answer(player);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
