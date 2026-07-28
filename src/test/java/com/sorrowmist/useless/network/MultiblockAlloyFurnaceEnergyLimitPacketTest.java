package com.sorrowmist.useless.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockAlloyFurnaceEnergyLimitPacketTest {
    @Test
    void payloadRoundTripsLongLimitAndValidationFields() {
        var expected = new MultiblockAlloyFurnaceEnergyLimitPacket(
                27, new BlockPos(-14, 80, 31), Long.MAX_VALUE - 42L);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        MultiblockAlloyFurnaceEnergyLimitPacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected,
                MultiblockAlloyFurnaceEnergyLimitPacket.STREAM_CODEC.decode(buffer));
    }
}
