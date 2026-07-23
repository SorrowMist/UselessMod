package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.menus.OmniversalPatternEncoderMenu;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record EncodeJeiOmniversalPatternPacket(
        int containerId, ResourceLocation recipeId, String fingerprint) implements CustomPacketPayload {
    public static final Type<EncodeJeiOmniversalPatternPacket> TYPE =
            new Type<>(UselessMod.id("encode_jei_omniversal_pattern"));
    public static final StreamCodec<FriendlyByteBuf, EncodeJeiOmniversalPatternPacket> STREAM_CODEC = StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeVarInt(packet.containerId);
                ResourceLocation.STREAM_CODEC.encode(buffer, packet.recipeId);
                buffer.writeUtf(packet.fingerprint);
            },
            buffer -> new EncodeJeiOmniversalPatternPacket(
                    buffer.readVarInt(), ResourceLocation.STREAM_CODEC.decode(buffer), buffer.readUtf()));

    public static void handle(EncodeJeiOmniversalPatternPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof OmniversalPatternEncoderMenu menu)
                    || menu.containerId != packet.containerId) return;
            AlloyFurnaceRecipeCatalog.resolve(player.level(),
                    new AlloyFurnaceRecipeIdentity(packet.recipeId, packet.fingerprint))
                    .ifPresent(entry -> menu.encodeJeiRecipe(entry, player));
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
