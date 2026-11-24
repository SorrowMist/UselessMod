package com.sorrowmist.useless.init;

final class ClientRecipeCatalogState {
    static final int TAG_WAIT_TICKS = 100;

    enum WorldTransition {
        NONE,
        ENTERED,
        LEFT
    }

    private boolean worldPresent;
    private boolean recipeCatalogDirty;
    private boolean clientRecipesReceived;
    private boolean clientTagsReceived;
    private int dirtyTicks;
    private boolean awaitingCatalogRefresh;

    synchronized WorldTransition observeWorld(boolean present) {
        if (!present) {
            if (!worldPresent) {
                dirtyTicks = 0;
                return WorldTransition.NONE;
            }
            resetConnection();
            return WorldTransition.LEFT;
        }

        if (worldPresent) return WorldTransition.NONE;
        worldPresent = true;
        recipeCatalogDirty = true;
        return WorldTransition.ENTERED;
    }

    synchronized void resetConnection() {
        worldPresent = false;
        recipeCatalogDirty = false;
        clientRecipesReceived = false;
        clientTagsReceived = false;
        dirtyTicks = 0;
        awaitingCatalogRefresh = false;
    }

    synchronized void onClientRecipesReceived() {
        clientRecipesReceived = true;
        recipeCatalogDirty = true;
    }

    synchronized void onClientTagsReceived() {
        clientTagsReceived = true;
        recipeCatalogDirty = true;
    }

    synchronized boolean beginRebuildWhenReady() {
        if (!worldPresent || !clientRecipesReceived || !recipeCatalogDirty) return false;
        if (!clientTagsReceived && ++dirtyTicks < TAG_WAIT_TICKS) return false;

        recipeCatalogDirty = false;
        dirtyTicks = 0;
        awaitingCatalogRefresh = true;
        return true;
    }

    synchronized boolean isAwaitingCatalogRefresh() {
        return awaitingCatalogRefresh;
    }

    synchronized void finishCatalogRefresh() {
        awaitingCatalogRefresh = false;
    }
}
