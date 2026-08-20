package com.sorrowmist.useless.network;

import com.sorrowmist.useless.world.dimension.DimensionGenerationConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DimensionConfigPacketsTest {
    @Test
    void submitPayloadRoundTripsConfigurationAndAction() {
        DimensionGenerationConfig config = new DimensionGenerationConfig(
                ResourceLocation.withDefaultNamespace("stone"),
                ResourceLocation.withDefaultNamespace("dirt"),
                ResourceLocation.withDefaultNamespace("glass"),
                12, -10, true, false);
        DimensionConfigSubmitPacket expected = new DimensionConfigSubmitPacket(9, config, true);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        DimensionConfigSubmitPacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, DimensionConfigSubmitPacket.STREAM_CODEC.decode(buffer));
    }

    @Test
    void ghostSlotPayloadRoundTripsOnlyTheServerValidatedSlotIdentity() {
        DimensionConfigGhostSlotPacket expected = new DimensionConfigGhostSlotPacket(
                4, 2, ResourceLocation.withDefaultNamespace("stone"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        DimensionConfigGhostSlotPacket.STREAM_CODEC.encode(buffer, expected);

        assertEquals(expected, DimensionConfigGhostSlotPacket.STREAM_CODEC.decode(buffer));
    }
}
