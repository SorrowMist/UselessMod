package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.AdvancedAlloyFurnaceMenu;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import com.sorrowmist.useless.compat.forge1201.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.sorrowmist.useless.compat.forge1201.network.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class PatternPageChangePacket implements CustomPacketPayload {

    public static final Type<PatternPageChangePacket> TYPE = new Type<>(
            new ResourceLocation(UselessMod.MODID, "pattern_page_change"));
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
