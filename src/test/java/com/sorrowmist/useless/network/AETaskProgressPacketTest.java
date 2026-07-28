package com.sorrowmist.useless.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AETaskProgressPacketTest {
    @Test
    void roundTripsCountsAboveTheIntegerRange() {
        long craftCount = (long) Integer.MAX_VALUE + 123L;
        long totalOutputCount = craftCount * 7L;
        var expectedTask = new AETaskProgressPacket.TaskProgressData(
                "structure", 40, 200, craftCount, totalOutputCount,
                "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        var expected = new AETaskProgressPacket(BlockPos.ZERO, List.of(expectedTask));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            AETaskProgressPacket.STREAM_CODEC.encode(buffer, expected);
            AETaskProgressPacket decoded = AETaskProgressPacket.STREAM_CODEC.decode(buffer);

            assertEquals(expected.pos(), decoded.pos());
            assertEquals(1, decoded.tasks().size());
            assertEquals(craftCount, decoded.tasks().getFirst().craftCount);
            assertEquals(totalOutputCount, decoded.tasks().getFirst().totalOutputCount);
        } finally {
            buffer.release();
        }
    }
}
