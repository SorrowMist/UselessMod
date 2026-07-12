package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.ForceKillMode;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public class ForceKillModeSwitchPacket implements CustomPacketPayload {
    public static final Type<ForceKillModeSwitchPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "force_kill_mode_switch"));
    public static final StreamCodec<FriendlyByteBuf, ForceKillModeSwitchPacket> STREAM_CODEC = StreamCodec.of(
            (buf, pkt) -> buf.writeEnum(pkt.mode),
            buf -> new ForceKillModeSwitchPacket(buf.readEnum(ForceKillMode.class))
    );
    private final ForceKillMode mode;

    public ForceKillModeSwitchPacket(ForceKillMode mode) {
        this.mode = mode;
    }

    public static void handle(ForceKillModeSwitchPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            var toolEntry = UselessItemUtils.findTargetToolInHands(player);
            if (toolEntry.isEmpty()) return;

            ItemStack stack = toolEntry.get().getKey();
            stack.set(UComponents.ForceKillEnabledComponent.get(), true);
            stack.set(UComponents.ForceKillModeComponent.get(), msg.mode);
            player.containerMenu.broadcastChanges();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
