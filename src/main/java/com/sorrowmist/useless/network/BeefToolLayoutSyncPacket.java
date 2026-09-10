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

public record BeefToolLayoutSyncPacket(String json) implements CustomPacketPayload {
    public static final Type<BeefToolLayoutSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "beef_tool_layout_sync"));
    public static final StreamCodec<FriendlyByteBuf, BeefToolLayoutSyncPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> buffer.writeUtf(packet.json, BeefToolLayout.MAX_TEXT_LENGTH),
                    buffer -> new BeefToolLayoutSyncPacket(buffer.readUtf(BeefToolLayout.MAX_TEXT_LENGTH)));

    public static void handle(BeefToolLayoutSyncPacket packet, IPayloadContext context) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        context.enqueueWork(() -> {
            try {
                BeefToolLayout layout = BeefToolLayout.fromJson(packet.json);
                if (Minecraft.getInstance().screen instanceof ModeWheelScreen screen) {
                    screen.receiveLayout(layout);
                }
            } catch (BeefToolLayout.LayoutException ignored) {
                if (Minecraft.getInstance().screen instanceof ModeWheelScreen screen) {
                    screen.receiveLayoutError(BeefToolLayout.Error.INVALID_TEXT);
                }
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
