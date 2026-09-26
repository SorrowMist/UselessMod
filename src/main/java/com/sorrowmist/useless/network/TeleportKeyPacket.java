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
 * <p>造化杖的短距闪现由组合键触发：手持造化杖并按住 Shift 右键，且准星未命中需要独占
 * 该组合键的方块时，由客户端上报本包。该组合键虽注册为 KeyMapping 供玩家改键，
 * 但其冲突上下文恒为非激活，不参与运行时分发，触发判定由
 * {@code InputEvent.MouseButton.Pre} 按鼠标按下边沿完成；
 * 若上下文处于激活态，同键位的原版 keyUse 会被修饰键桶遮蔽，方块交互将一并失效。
 * 客户端只负责上报按键，目的地计算、碰撞检查与传送结算全部在服务端完成，
 * 避免客户端决定落点。</p>
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