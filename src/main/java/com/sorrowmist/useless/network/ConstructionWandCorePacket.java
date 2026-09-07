package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.tool.ConstructionWandCoreMode;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import com.sorrowmist.useless.utils.UselessItemUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record ConstructionWandCorePacket(ConstructionWandCoreMode mode) implements CustomPacketPayload {
    public static final Type<ConstructionWandCorePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "construction_wand_core"));
    public static final StreamCodec<FriendlyByteBuf, ConstructionWandCorePacket> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> buf.writeEnum(packet.mode),
            buf -> new ConstructionWandCorePacket(buf.readEnum(ConstructionWandCoreMode.class))
    );

    public static void handle(ConstructionWandCorePacket message, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!EndlessBeafItem.isConstructionWandAvailable()) return;
            ServerPlayer player = (ServerPlayer) context.player();
            var entry = UselessItemUtils.findTargetToolInHands(player);
            if (entry.isEmpty()) return;

            ItemStack stack = entry.get().getKey();
            if (!(stack.getItem() instanceof EndlessBeafItem)) return;
            stack.set(UComponents.ConstructionWandCoreComponent.get(), message.mode);
            player.containerMenu.broadcastChanges();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
