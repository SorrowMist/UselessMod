package com.sorrowmist.useless.core.component;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MultiblockRecoveryDataTest {
    @Test
    void legacyDataDefaultsToUnlimitedAutomaticCharging() {
        MultiblockRecoveryData decoded = MultiblockRecoveryData.CODEC
                .parse(JsonOps.INSTANCE, JsonOps.INSTANCE.emptyMap())
                .getOrThrow();

        assertEquals(1, decoded.version());
        assertEquals(Long.MAX_VALUE, decoded.automaticEnergyLimit());
    }

    @Test
    void configuredAutomaticEnergyLimitRoundTrips() {
        MultiblockRecoveryData expected = new MultiblockRecoveryData(
                MultiblockRecoveryData.CURRENT_VERSION, 99L, List.of(), 12_345L);

        var encoded = MultiblockRecoveryData.CODEC.encodeStart(JsonOps.INSTANCE, expected).getOrThrow();
        MultiblockRecoveryData decoded = MultiblockRecoveryData.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(expected, decoded);
        assertFalse(decoded.isEmpty());
    }

    @Test
    void networkRoundTripKeepsLongAutomaticEnergyLimit() {
        MultiblockRecoveryData expected = new MultiblockRecoveryData(
                MultiblockRecoveryData.CURRENT_VERSION, 99L, List.of(), Long.MAX_VALUE - 42L);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY),
                ConnectionType.OTHER);

        try {
            MultiblockRecoveryData.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, MultiblockRecoveryData.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
