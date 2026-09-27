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
 * 连锁形状切换包。
 *
 * <p>客户端在按住连锁键时以 Shift + 滚轮触发，只上报滚动方向；真正的取模循环与服务端
 * 状态更新由 {@link MiningDispatcher#cycleShape} 完成，避免两端各自维护下标而错位。
 */
public class ShapeSwitchPacket implements CustomPacketPayload {

    public static final Type<ShapeSwitchPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "shape_switch"));

    public static final StreamCodec<FriendlyByteBuf, ShapeSwitchPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeVarInt(pkt.delta),
            buf -> new ShapeSwitchPacket(buf.readVarInt())
    );

    /** 滚动步数：正数为向后一个形状，负数为向前一个。 */
    private final int delta;

    public ShapeSwitchPacket(int delta) {
        this.delta = delta;
    }

    public static void handle(ShapeSwitchPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() != null) {
                MiningDispatcher.cycleShape(ctx.player(), msg.delta);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
