package com.sorrowmist.useless.init;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRecipeCatalogStateTest {
    @Test
    void dimensionChangesDoNotInvalidateTheReadyCatalog() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();

        assertEquals(ClientRecipeCatalogState.WorldTransition.ENTERED, state.observeWorld(true));
        state.onClientTagsReceived();
        assertTrue(state.beginRebuildWhenReady());
        state.finishCatalogRefresh();

        for (int i = 0; i < 3; i++) {
            assertEquals(ClientRecipeCatalogState.WorldTransition.NONE, state.observeWorld(true));
            assertFalse(state.beginRebuildWhenReady());
        }
        assertFalse(state.isAwaitingCatalogRefresh());

        state.markDirty();
        assertTrue(state.beginRebuildWhenReady());
    }

    @Test
    void leavingAndRejoiningWaitsForTheNewSessionTagPacket() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();
        state.observeWorld(true);
        state.onClientTagsReceived();
        assertTrue(state.beginRebuildWhenReady());
        state.finishCatalogRefresh();

        assertEquals(ClientRecipeCatalogState.WorldTransition.LEFT, state.observeWorld(false));
        assertEquals(ClientRecipeCatalogState.WorldTransition.ENTERED, state.observeWorld(true));
        assertFalse(state.beginRebuildWhenReady());

        state.onClientTagsReceived();
        assertTrue(state.beginRebuildWhenReady());
    }

    @Test
    void missingTagsUseTheBoundedWaitBeforeBuilding() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();
        state.observeWorld(true);

        for (int tick = 1; tick < ClientRecipeCatalogState.TAG_WAIT_TICKS; tick++) {
            assertFalse(state.beginRebuildWhenReady());
        }
        assertTrue(state.beginRebuildWhenReady());
        assertFalse(state.beginRebuildWhenReady());
    }
}
