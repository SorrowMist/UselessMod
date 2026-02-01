package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 强制破坏按键包
 * 用于客户端通知服务端执行强制破坏
 */
public class ForceBreakKeyPacket implements CustomPacketPayload {

    public static final Type<ForceBreakKeyPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "force_break_key"));

    public static final StreamCodec<FriendlyByteBuf, ForceBreakKeyPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.tabPressed);
            },
            buf -> new ForceBreakKeyPacket(buf.readBoolean())
    );

    // 是否同时按下了Tab键（连锁模式）
    private final boolean tabPressed;

    public ForceBreakKeyPacket(boolean tabPressed) {
        this.tabPressed = tabPressed;
    }

    public static void handle(ForceBreakKeyPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() != null) {
                // 直接执行强制破坏，传入当前是否按下Tab键
                MiningDispatcher.dispatchForceBreak(ctx.player(), msg.tabPressed);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
