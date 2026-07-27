package com.sorrowmist.useless.network;

import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PassiveCraftingPacketsTest {
    @Test
    void settingsPayloadRoundTripsAllValidationFields() {
        var expected = new PassiveCraftingSettingsPacket(
                42, new BlockPos(4, 70, -8), 72_000, Integer.MAX_VALUE);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        PassiveCraftingSettingsPacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, PassiveCraftingSettingsPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void statusPayloadKeepsEverySlotIndexAligned() {
        List<PassiveCraftingHatchBlockEntity.SlotStatus> statuses = new ArrayList<>();
        var states = PassiveCraftingHatchBlockEntity.SlotState.values();
        for (int slot = 0; slot < PassiveCraftingHatchBlockEntity.PATTERN_SLOTS; slot++) {
            statuses.add(new PassiveCraftingHatchBlockEntity.SlotStatus(
                    slot, states[slot % states.length], slot * 3, 100, "detail-" + slot));
        }
        var expected = new PassiveCraftingStatusPacket(
                7, new BlockPos(-2, 64, 11), statuses);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        PassiveCraftingStatusPacket.STREAM_CODEC.encode(buffer, expected);
        PassiveCraftingStatusPacket decoded =
                PassiveCraftingStatusPacket.STREAM_CODEC.decode(buffer);

        assertEquals(expected.containerId(), decoded.containerId());
        assertEquals(expected.pos(), decoded.pos());
        assertEquals(expected.statuses(), decoded.statuses());
    }

    @Test
    void statusPayloadRejectsMoreThanThirtySlots() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(PassiveCraftingHatchBlockEntity.PATTERN_SLOTS + 1);

        assertThrows(IllegalArgumentException.class,
                () -> PassiveCraftingStatusPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void statusPayloadRejectsOutOfRangeSlotIndex() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(1);
        buffer.writeByte(PassiveCraftingHatchBlockEntity.PATTERN_SLOTS);
        buffer.writeByte(PassiveCraftingHatchBlockEntity.SlotState.EMPTY.ordinal());
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeUtf("");

        assertThrows(IllegalArgumentException.class,
                () -> PassiveCraftingStatusPacket.STREAM_CODEC.decode(buffer));
    }
}
