package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 短距传送（闪现）按键包。
 *
 * <p>造化杖的短距闪现原本硬编码在「潜行 + 右键」上，现改由独立快捷键触发
 * （默认 Shift + 鼠标右键，可在按键设置中更改）。客户端只负责上报按键，
 * 目的地计算、碰撞检查与传送结算全部在服务端完成，避免客户端决定落点。</p>
 */
public class TeleportKeyPacket implements CustomPacketPayload {

    public static final Type<TeleportKeyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "teleport_key"));

    public static final StreamCodec<FriendlyByteBuf, TeleportKeyPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> { }, buf -> new TeleportKeyPacket());

    public TeleportKeyPacket() {
    }

    public static void handle(TeleportKeyPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer serverPlayer) {
                EndlessBeafItem.performShortTeleport(serverPlayer);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}