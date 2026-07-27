package com.sorrowmist.useless.network;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.menus.PassiveCraftingHatchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record PassiveCraftingStatusPacket(
        int containerId, BlockPos pos,
        List<PassiveCraftingHatchBlockEntity.SlotStatus> statuses)
        implements CustomPacketPayload {
    public static final Type<PassiveCraftingStatusPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "passive_crafting_status"));
    public static final StreamCodec<FriendlyByteBuf, PassiveCraftingStatusPacket> STREAM_CODEC =
            StreamCodec.of(PassiveCraftingStatusPacket::encode, PassiveCraftingStatusPacket::decode);

    public PassiveCraftingStatusPacket {
        pos = pos.immutable();
        statuses = List.copyOf(statuses);
    }

    private static void encode(FriendlyByteBuf buf, PassiveCraftingStatusPacket packet) {
        buf.writeVarInt(packet.containerId);
        buf.writeBlockPos(packet.pos);
        buf.writeVarInt(packet.statuses.size());
        for (PassiveCraftingHatchBlockEntity.SlotStatus status : packet.statuses) {
            buf.writeByte(status.slot());
            buf.writeByte(status.state().ordinal());
            buf.writeVarInt(status.progress());
            buf.writeVarInt(status.maxProgress());
            buf.writeUtf(status.detail(), 256);
        }
    }

    private static PassiveCraftingStatusPacket decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        if (size < 0 || size > PassiveCraftingHatchBlockEntity.PATTERN_SLOTS) {
            throw new IllegalArgumentException("Invalid passive crafting status count: " + size);
        }
        var states = PassiveCraftingHatchBlockEntity.SlotState.values();
        List<PassiveCraftingHatchBlockEntity.SlotStatus> statuses = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            int slot = buf.readUnsignedByte();
            int stateIndex = buf.readUnsignedByte();
            if (slot >= PassiveCraftingHatchBlockEntity.PATTERN_SLOTS) {
                throw new IllegalArgumentException("Invalid passive crafting slot: " + slot);
            }
            if (stateIndex >= states.length) {
                throw new IllegalArgumentException("Invalid passive crafting state: " + stateIndex);
            }
            statuses.add(new PassiveCraftingHatchBlockEntity.SlotStatus(
                    slot, states[stateIndex], buf.readVarInt(), buf.readVarInt(), buf.readUtf(256)));
        }
        return new PassiveCraftingStatusPacket(containerId, pos, statuses);
    }

    public static void handle(PassiveCraftingStatusPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof PassiveCraftingHatchMenu menu
                    && menu.containerId == packet.containerId
                    && menu.getBlockPos().equals(packet.pos)) {
                menu.updateSlotStatuses(packet.statuses);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
