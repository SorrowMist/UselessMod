package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class PatternPageChangePacket implements CustomPacketPayload {

    public static final Type<PatternPageChangePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "pattern_page_change"));
    public static final StreamCodec<FriendlyByteBuf, PatternPageChangePacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeInt(pkt.page),
            buf -> new PatternPageChangePacket(buf.readInt())
    );
    private final int page;

    public PatternPageChangePacket(int page) {
        this.page = page;
    }

    public static void handle(PatternPageChangePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player.containerMenu instanceof AdvancedAlloyFurnaceMenu menu) {
                menu.setPatternPage(msg.page);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}