package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * Updates the batch size of a single passive pattern slot.
 *
 * <p>{@code slot < 0} clears every override so all slots follow the global multiplier again;
 * a non-negative slot with {@code multiplier == 0} clears just that slot. The value is validated
 * against the coil-tier parallel limit before it reaches the block entity, because a client that
 * ignores the limit must not be able to persist an unreachable batch size.
 */
public record PassiveCraftingSlotMultiplierPacket(
        int containerId, BlockPos pos, int slot, long multiplier)
        implements CustomPacketPayload {
    public static final Type<PassiveCraftingSlotMultiplierPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "passive_crafting_slot_multiplier"));
    public static final StreamCodec<FriendlyByteBuf, PassiveCraftingSlotMultiplierPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> {
                buf.writeVarInt(packet.containerId);
                buf.writeBlockPos(packet.pos);
                buf.writeVarInt(packet.slot);
                buf.writeVarLong(packet.multiplier);
            }, buf -> new PassiveCraftingSlotMultiplierPacket(
                    buf.readVarInt(), buf.readBlockPos(), buf.readVarInt(), buf.readVarLong()));

    public static void handle(PassiveCraftingSlotMultiplierPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof PassiveCraftingHatchMenu menu)
                    || menu.containerId != packet.containerId
                    || !menu.getBlockPos().equals(packet.pos)
                    || !menu.stillValid(player)) {
                return;
            }
            PassiveCraftingHatchBlockEntity hatch = menu.getHatch();
            if (hatch == null || hatch.getBlockPos().distSqr(packet.pos) != 0.0D) {
                return;
            }
            if (packet.slot < 0) {
                hatch.clearSlotMultipliers();
                menu.broadcastChanges();
                hatch.requestStatusSync();
                return;
            }
            if (packet.slot >= PassiveCraftingHatchBlockEntity.PATTERN_SLOTS || packet.multiplier < 0L) {
                return;
            }
            if (packet.multiplier > 0L && packet.multiplier > hatch.getCurrentMaxParallel()) {
                return;
            }
            hatch.setSlotMultiplier(packet.slot, packet.multiplier);
            menu.broadcastChanges();
            hatch.requestStatusSync();
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
