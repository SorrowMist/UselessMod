package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ToolTypeModeSwitchPacket(ToolTypeMode mode) implements CustomPacketPayload {
    public static final StreamCodec<FriendlyByteBuf, ToolTypeModeSwitchPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeEnum(packet.mode),
                    buf -> new ToolTypeModeSwitchPacket(buf.readEnum(ToolTypeMode.class))
            );

    public static final Type<ToolTypeModeSwitchPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "tool_type_mode_switch"));

    public static void handle(ToolTypeModeSwitchPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) ctx.player();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;

            stack.set(UComponents.CurrentToolTypeComponent, msg.mode);
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}