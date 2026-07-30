package com.sorrowmist.useless.network;

import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.content.menus.PagedRecoverableMenu;
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
                42, new BlockPos(4, 70, -8), 72_000, Long.MAX_VALUE);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        PassiveCraftingSettingsPacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, PassiveCraftingSettingsPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void statusPayloadKeepsCurrentPageSlotIndexesAligned() {
        List<PassiveCraftingHatchBlockEntity.SlotStatus> statuses = new ArrayList<>();
        var states = PassiveCraftingHatchBlockEntity.SlotState.values();
        for (int slot = 513; slot < PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS; slot++) {
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
    void statusPayloadEncodesSlotFiveHundredThirtyNineAsVarInt() {
        var expected = new PassiveCraftingStatusPacket(7, new BlockPos(-2, 64, 11), List.of(
                new PassiveCraftingHatchBlockEntity.SlotStatus(
                        539, PassiveCraftingHatchBlockEntity.SlotState.RUNNING, 23, 100, "detail")));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        PassiveCraftingStatusPacket.STREAM_CODEC.encode(buffer, expected);
        PassiveCraftingStatusPacket decoded = PassiveCraftingStatusPacket.STREAM_CODEC.decode(buffer);

        assertEquals(539, decoded.statuses().getFirst().slot());
        assertEquals(expected, decoded);
    }

    @Test
    void statusPayloadRejectsMoreThanOnePage() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(PagedRecoverableMenu.SLOTS_PER_PAGE + 1);

        assertThrows(IllegalArgumentException.class,
                () -> PassiveCraftingStatusPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void statusPayloadRejectsOutOfRangeSlotIndex() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeBlockPos(BlockPos.ZERO);
        buffer.writeVarInt(1);
        buffer.writeVarInt(PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS);
        buffer.writeByte(PassiveCraftingHatchBlockEntity.SlotState.EMPTY.ordinal());
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeUtf("");

        assertThrows(IllegalArgumentException.class,
                () -> PassiveCraftingStatusPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void statusPayloadRejectsMixedPagesAndDuplicateSlots() {
        var empty = PassiveCraftingHatchBlockEntity.SlotState.EMPTY;
        assertThrows(IllegalArgumentException.class, () -> new PassiveCraftingStatusPacket(
                1, BlockPos.ZERO, List.of(
                        new PassiveCraftingHatchBlockEntity.SlotStatus(0, empty, 0, 0, ""),
                        new PassiveCraftingHatchBlockEntity.SlotStatus(27, empty, 0, 0, ""))));
        assertThrows(IllegalArgumentException.class, () -> new PassiveCraftingStatusPacket(
                1, BlockPos.ZERO, List.of(
                        new PassiveCraftingHatchBlockEntity.SlotStatus(539, empty, 0, 0, ""),
                        new PassiveCraftingHatchBlockEntity.SlotStatus(539, empty, 0, 0, ""))));
    }
}
