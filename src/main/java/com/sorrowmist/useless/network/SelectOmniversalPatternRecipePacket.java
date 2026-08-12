package com.sorrowmist.useless.network;

import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Tells the server which Omniversal Alloy Furnace recipe the player just transferred from JEI into a
 * pattern encoding terminal, so the pattern that terminal encodes next becomes an Omniversal Pattern
 * bound to that exact recipe — mold included.
 *
 * <p>Sent before the {@code SET_FILTER} packets that fill the terminal slots, so the pick is on
 * record no matter how quickly encoding follows.
 */
public record SelectOmniversalPatternRecipePacket(
        int containerId, ResourceLocation recipeId, String fingerprint, String sourceId) implements CustomPacketPayload {
    public SelectOmniversalPatternRecipePacket {
        sourceId = RecipeSourceIds.normalize(sourceId);
    }

    public static final Type<SelectOmniversalPatternRecipePacket> TYPE =
            new Type<>(UselessMod.id("select_omniversal_pattern_recipe"));
    public static final StreamCodec<FriendlyByteBuf, SelectOmniversalPatternRecipePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, packet) -> {
                        buffer.writeVarInt(packet.containerId);
                        ResourceLocation.STREAM_CODEC.encode(buffer, packet.recipeId);
                        buffer.writeUtf(packet.fingerprint);
                        buffer.writeUtf(packet.sourceId);
                    },
                    buffer -> new SelectOmniversalPatternRecipePacket(
                            buffer.readVarInt(), ResourceLocation.STREAM_CODEC.decode(buffer),
                            buffer.readUtf(), buffer.readUtf()));

    public static void handle(SelectOmniversalPatternRecipePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof PatternEncodingTermMenu menu)
                    || menu.containerId != packet.containerId) return;
            // getTarget() is the IPatternTerminalMenuHost the menu was opened against, which owns the
            // PatternEncodingLogic the encoded pattern will pass through: the terminal part for the
            // wired block, or ae2wtlib's WETMenuHost for the wireless one.
            if (!(menu.getTarget() instanceof IPatternTerminalMenuHost host)) return;
            if (!(host.getLogic() instanceof PendingOmniversalPatternHolder holder)) return;

            AlloyFurnaceRecipeIdentity identity;
            try {
                identity = new AlloyFurnaceRecipeIdentity(packet.recipeId, packet.fingerprint);
            } catch (RuntimeException ignored) {
                // A blank fingerprint can only come from a malformed packet; drop it rather than
                // leaving a half-valid pick behind.
                holder.uselessMod$setPendingOmniversalRecipe(null);
                holder.uselessMod$setPendingOmniversalSourceId(null);
                return;
            }
            holder.uselessMod$setPendingOmniversalRecipe(identity);
            holder.uselessMod$setPendingOmniversalSourceId(packet.sourceId());
            // The encoded pattern can arrive before this custom selection packet. Re-run the same
            // conversion check now so the terminal does not remain with a plain pattern forever.
            holder.uselessMod$tryConvertPendingOmniversalPattern();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
