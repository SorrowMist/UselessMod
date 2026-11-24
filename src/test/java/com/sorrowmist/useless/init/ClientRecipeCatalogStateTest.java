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
        state.onClientRecipesReceived();
        state.onClientTagsReceived();
        assertTrue(state.beginRebuildWhenReady());
        state.finishCatalogRefresh();

        for (int i = 0; i < 3; i++) {
            assertEquals(ClientRecipeCatalogState.WorldTransition.NONE, state.observeWorld(true));
            assertFalse(state.beginRebuildWhenReady());
        }
        assertFalse(state.isAwaitingCatalogRefresh());

        state.onClientRecipesReceived();
        assertTrue(state.beginRebuildWhenReady());
    }

    @Test
    void leavingAndRejoiningWaitsForTheNewSessionTagPacket() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();
        state.observeWorld(true);
        state.onClientRecipesReceived();
        state.onClientTagsReceived();
        assertTrue(state.beginRebuildWhenReady());
        state.finishCatalogRefresh();

        assertEquals(ClientRecipeCatalogState.WorldTransition.LEFT, state.observeWorld(false));
        assertEquals(ClientRecipeCatalogState.WorldTransition.ENTERED, state.observeWorld(true));
        state.onClientRecipesReceived();
        assertFalse(state.beginRebuildWhenReady());

        state.onClientTagsReceived();
        assertTrue(state.beginRebuildWhenReady());
    }

    @Test
    void missingTagsUseTheBoundedWaitBeforeBuilding() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();
        state.onClientRecipesReceived();
        state.observeWorld(true);

        for (int tick = 1; tick < ClientRecipeCatalogState.TAG_WAIT_TICKS; tick++) {
            assertFalse(state.beginRebuildWhenReady());
        }
        assertTrue(state.beginRebuildWhenReady());
        assertFalse(state.beginRebuildWhenReady());
    }

    @Test
    void disconnectBeforeTheFirstWorldTickDiscardsEarlyPackets() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();
        state.onClientRecipesReceived();
        state.onClientTagsReceived();
        state.resetConnection();

        state.observeWorld(true);
        state.onClientTagsReceived();
        assertFalse(state.beginRebuildWhenReady());
        state.onClientRecipesReceived();
        assertTrue(state.beginRebuildWhenReady());
    }

    @Test
    void earlyTagsCannotBuildUntilThisSessionsRecipesArrive() {
        ClientRecipeCatalogState state = new ClientRecipeCatalogState();
        for (int session = 0; session < 2; session++) {
            state.onClientTagsReceived();
            state.observeWorld(true);
            for (int tick = 0; tick <= ClientRecipeCatalogState.TAG_WAIT_TICKS; tick++) {
                assertFalse(state.beginRebuildWhenReady());
            }
            state.onClientRecipesReceived();
            assertTrue(state.beginRebuildWhenReady());
            assertFalse(state.beginRebuildWhenReady());
            state.observeWorld(false);
        }
    }
}
