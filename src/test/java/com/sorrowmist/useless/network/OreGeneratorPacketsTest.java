package com.sorrowmist.useless.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OreGeneratorPacketsTest {
    @Test
    void settingsPayloadRoundTripsLongMaximumRate() {
        var expected = new OreGeneratorSettingsPacket(
                13, new BlockPos(-4, 70, 18), Long.MAX_VALUE);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        OreGeneratorSettingsPacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, OreGeneratorSettingsPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void outputTogglePayloadRoundTripsPositionAndContainer() {
        var expected = new OreGeneratorOutputTogglePacket(
                27, new BlockPos(8, -12, 31));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        OreGeneratorOutputTogglePacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, OreGeneratorOutputTogglePacket.STREAM_CODEC.decode(buffer));
    }
}
