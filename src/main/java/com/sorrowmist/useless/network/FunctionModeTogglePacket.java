package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.FunctionMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumSet;

public record FunctionModeTogglePacket(FunctionMode mode) implements CustomPacketPayload {

    public static final StreamCodec<FriendlyByteBuf, FunctionModeTogglePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> buf.writeEnum(packet.mode),
                    buf -> new FunctionModeTogglePacket(buf.readEnum(FunctionMode.class))
            );
    public static final Type<FunctionModeTogglePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "function_mode_toggle"));

    public static void handle(FunctionModeTogglePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return;

            EnumSet<FunctionMode> current = stack.getOrDefault(
                    UComponents.FunctionModesComponent,
                    EnumSet.noneOf(FunctionMode.class)
            ).clone(); // clone 以安全修改

            ctx.player().sendSystemMessage(Component.literal(msg.mode.getName()));

            if (current.contains(msg.mode)) {
                current.remove(msg.mode);
            } else {
                current.add(msg.mode);
            }

            stack.set(UComponents.FunctionModesComponent,
                      current.isEmpty() ? null : EnumSet.copyOf(current)
            );
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}