package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.utils.mining.MiningDispatcher;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class TabKeyPressedPacket implements CustomPacketPayload {

    public static final Type<TabKeyPressedPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "tab_key_pressed"));
    public static final StreamCodec<FriendlyByteBuf, TabKeyPressedPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> {
                buf.writeBoolean(pkt.pressed);
            },
            buf -> new TabKeyPressedPacket(buf.readBoolean())
    );
    private final boolean pressed;

    public TabKeyPressedPacket(boolean pressed) {
        this.pressed = pressed;
    }

    public static void handle(TabKeyPressedPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() != null) {
                MiningDispatcher.setTabPressed(ctx.player(), msg.pressed);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
