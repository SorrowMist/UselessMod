package com.sorrowmist.useless.compat.ars;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArsSourceMigrationTest {
    private static ISourceTile tile(AtomicInteger stored, int capacity, int rate, boolean accepts) {
        var tile = mock(ISourceTile.class);
        when(tile.getSource()).thenAnswer(call -> stored.get());
        when(tile.getMaxSource()).thenReturn(capacity);
        when(tile.getTransferRate()).thenReturn(rate);
        when(tile.canAcceptSource()).thenReturn(accepts);
        // Ars 4.x add/removeSource return the new total, not the moved amount.
        when(tile.addSource(anyInt())).thenAnswer(call -> stored.addAndGet(call.getArgument(0)));
        when(tile.removeSource(anyInt())).thenAnswer(call -> stored.addAndGet(-(int) call.getArgument(0)));
        return tile;
    }

    @Test void nativeIntRateAndSimulationAreRespectedInBothDirections() {
        var stored = new AtomicInteger(120);
        var endpoint = new ArsSourceCompat.TileEndpoint(tile(stored, 200, 40, true));
        assertEquals(40, endpoint.extract(500, true));
        assertEquals(120, stored.get());
        assertEquals(40, endpoint.extract(500, false));
        assertEquals(80, stored.get());
        assertEquals(40, endpoint.receive(500, true));
        assertEquals(80, stored.get());
        assertEquals(40, endpoint.receive(500, false));
        assertEquals(120, stored.get());
        assertEquals(200, endpoint.capacity());
    }

    @Test void zeroTransferRateDoesNotBecomeUnlimitedAndInputFlagDoesNotBlockExtraction() {
        var stored = new AtomicInteger(120);
        var nativeTile = tile(stored, 200, 0, true);
        var endpoint = new ArsSourceCompat.TileEndpoint(nativeTile);
        assertEquals(0, endpoint.transferRate());
        assertEquals(0, endpoint.extract(100, false));
        assertEquals(0, endpoint.receive(100, false));
        verify(nativeTile, never()).removeSource(anyInt());
        verify(nativeTile, never()).addSource(anyInt());
        var outputOnly = new ArsSourceCompat.TileEndpoint(tile(stored, 200, 50, false));
        assertEquals(0, outputOnly.receive(100, false));
        assertEquals(50, outputOnly.extract(100, false));
        assertEquals(70, stored.get());
    }

    @Test void maximumNativeQuantityCannotWrapAroundAndNegativeRequestsDoNothing() {
        var stored = new AtomicInteger(Integer.MAX_VALUE - 2);
        var endpoint = new ArsSourceCompat.TileEndpoint(tile(stored, Integer.MAX_VALUE, Integer.MAX_VALUE, true));
        assertEquals(2, endpoint.receive(Integer.MAX_VALUE, true));
        assertEquals(2, endpoint.receive(Integer.MAX_VALUE, false));
        assertEquals(Integer.MAX_VALUE, stored.get());
        assertEquals(0, endpoint.extract(-1, false));
        assertEquals(0, endpoint.receive(-1, false));
        assertEquals(Integer.MAX_VALUE, endpoint.extract(Integer.MAX_VALUE, false));
        assertEquals(0, stored.get());
    }
}
