package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.wintercogs.appliedpneumatics.common.me.keys.AirKey;
import gripe._90.arseng.me.key.SourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AeResourceBridgeMigrationTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void appliedPneumaticsUsesTheNativeAirKeyAndLongAmountsWithoutTruncation() {
        var level = mock(Level.class);
        var storage = mock(MEStorage.class);
        var stacks = mock(KeyCounter.class);
        long amount = 4_000_000_000L;
        when(storage.getAvailableStacks()).thenReturn(stacks);
        when(stacks.get(AirKey.INSTANCE)).thenReturn(amount);
        when(storage.extract(eq(AirKey.INSTANCE), eq(amount), eq(Actionable.SIMULATE), any())).thenReturn(amount);
        when(storage.insert(eq(AirKey.INSTANCE), eq(amount), eq(Actionable.MODULATE), any())).thenReturn(amount);
        try (var network = mockStatic(AeNetworks.class)) {
            network.when(() -> AeNetworks.isEndpoint(level, BlockPos.ZERO)).thenReturn(true);
            network.when(() -> AeNetworks.storage(level, BlockPos.ZERO)).thenReturn(storage);
            var endpoint = new AePressureCompat().pressureEndpoint(level, BlockPos.ZERO);
            assertNotNull(endpoint);
            assertEquals(amount, endpoint.stored());
            assertEquals(amount, endpoint.extract(amount, true));
            assertEquals(amount, endpoint.receive(amount, false));
            assertEquals(0L, endpoint.volume());
            assertEquals(Long.MAX_VALUE, endpoint.capacity());
            verify(storage).extract(eq(AirKey.INSTANCE), eq(amount), eq(Actionable.SIMULATE), any());
            verify(storage).insert(eq(AirKey.INSTANCE), eq(amount), eq(Actionable.MODULATE), any());
        }
    }

    @Test void arsEnergistiqueUsesTheNativeSingletonAndBoundsOnlyItsIntFacingView() {
        var level = mock(Level.class);
        var storage = mock(MEStorage.class);
        var stacks = mock(KeyCounter.class);
        when(storage.getAvailableStacks()).thenReturn(stacks);
        when(stacks.get(SourceKey.KEY)).thenReturn(4_000_000_000L);
        when(storage.extract(eq(SourceKey.KEY), eq(123L), eq(Actionable.MODULATE), any())).thenReturn(123L);
        when(storage.insert(eq(SourceKey.KEY), eq(456L), eq(Actionable.SIMULATE), any())).thenReturn(456L);
        try (var network = mockStatic(AeNetworks.class)) {
            network.when(() -> AeNetworks.isEndpoint(level, BlockPos.ZERO)).thenReturn(true);
            network.when(() -> AeNetworks.storage(level, BlockPos.ZERO)).thenReturn(storage);
            var endpoint = new AeSourceCompat().sourceEndpoint(level, BlockPos.ZERO);
            assertNotNull(endpoint);
            assertEquals(Integer.MAX_VALUE, endpoint.amount());
            assertEquals(Integer.MAX_VALUE, endpoint.capacity());
            assertEquals(123, endpoint.extract(123, false));
            assertEquals(456, endpoint.receive(456, true));
            assertEquals(0, endpoint.extract(-1, false));
            verify(storage).extract(eq(SourceKey.KEY), eq(123L), eq(Actionable.MODULATE), any());
            verify(storage).insert(eq(SourceKey.KEY), eq(456L), eq(Actionable.SIMULATE), any());
        }
    }

    @Test void disconnectedEndpointsMoveNothingWithoutCachingAStaleNetwork() {
        var level = mock(Level.class);
        try (var network = mockStatic(AeNetworks.class)) {
            assertNull(new AeSourceCompat().sourceEndpoint(level, BlockPos.ZERO));
            assertNull(new AePressureCompat().pressureEndpoint(level, BlockPos.ZERO));
            network.when(() -> AeNetworks.isEndpoint(level, BlockPos.ZERO)).thenReturn(true);
            var source = new AeSourceCompat().sourceEndpoint(level, BlockPos.ZERO);
            var pressure = new AePressureCompat().pressureEndpoint(level, BlockPos.ZERO);
            assertNotNull(source); assertNotNull(pressure);
            assertEquals(0, source.amount());
            assertEquals(0, source.extract(100, false));
            assertEquals(0, source.receive(100, false));
            assertEquals(0L, pressure.stored());
            assertEquals(0L, pressure.extract(100, false));
            assertEquals(0L, pressure.receive(100, false));
        }
    }
}
