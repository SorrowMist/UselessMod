package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.client.gui.ModeWheelScreen;
import com.sorrowmist.useless.data.BeefToolLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record BeefToolLayoutResultPacket(BeefToolLayout.Error error) implements CustomPacketPayload {
    public static final Type<BeefToolLayoutResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "beef_tool_layout_result"));
    public static final StreamCodec<FriendlyByteBuf, BeefToolLayoutResultPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeEnum(packet.error),
                    buffer -> new BeefToolLayoutResultPacket(buffer.readEnum(BeefToolLayout.Error.class)));

    public static void handle(BeefToolLayoutResultPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof ModeWheelScreen screen) {
                screen.receiveLayoutError(packet.error);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
