package com.sorrowmist.useless.network;

import com.sorrowmist.useless.InventoryTestBootstrap;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceMenuSyncPacketTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void actualPacketRoundTripPreservesIntCountsDataAndForeignNbt() {
        ItemStack huge = new ItemStack(Items.DIAMOND, Integer.MAX_VALUE);
        huge.getOrCreateTag().putLong("foreign", Long.MAX_VALUE);
        var packet = new FurnaceMenuSyncPacket(12, 32768, List.of(huge, ItemStack.EMPTY), huge.copy(),
                List.of(100000, Integer.MIN_VALUE, Integer.MAX_VALUE, -1));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FurnaceMenuSyncPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = FurnaceMenuSyncPacket.STREAM_CODEC.decode(buffer);
            assertEquals(packet.containerId(), decoded.containerId());
            assertEquals(packet.stateId(), decoded.stateId());
            assertEquals(packet.data(), decoded.data());
            assertEquals(Integer.MAX_VALUE, decoded.slots().get(0).getCount());
            assertEquals(huge.getTag(), decoded.slots().get(0).getTag());
            assertEquals(Integer.MAX_VALUE, decoded.carried().getCount());
            assertTrue(decoded.slots().get(1).isEmpty());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @Test void malformedSlotListLengthIsRejectedBeforeAllocatingEntries() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1); buffer.writeVarInt(1); buffer.writeVarInt(513);
            assertThrows(IllegalArgumentException.class, () -> FurnaceMenuSyncPacket.STREAM_CODEC.decode(buffer));
            buffer.clear();
            var packet = new FurnaceMenuSyncPacket(1, 1, Collections.nCopies(513, ItemStack.EMPTY), ItemStack.EMPTY, List.of());
            assertThrows(IllegalArgumentException.class, () -> FurnaceMenuSyncPacket.STREAM_CODEC.encode(buffer, packet));
        } finally { buffer.release(); }
    }
}
