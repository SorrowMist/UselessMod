package com.sorrowmist.useless.utils.mining;

import net.minecraft.core.BlockPos;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PlayerMiningData {
    private final UUID playerId;
    private boolean tabPressed = false;
    private BlockPos cachedPos = null;
    private List<BlockPos> cachedBlocks = Collections.emptyList();

    public PlayerMiningData(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public boolean isTabPressed() {
        return this.tabPressed;
    }

    public void setTabPressed(boolean tabPressed) {
        this.tabPressed = tabPressed;
    }

    public BlockPos getCachedPos() {
        return this.cachedPos;
    }

    public void setCachedPos(BlockPos cachedPos) {
        this.cachedPos = cachedPos;
    }

    public List<BlockPos> getCachedBlocks() {
        return this.cachedBlocks;
    }

    public void setCachedBlocks(List<BlockPos> cachedBlocks) {
        this.cachedBlocks = cachedBlocks != null ? cachedBlocks : Collections.emptyList();
    }

    public boolean hasCachedBlocks() {
        return !this.cachedBlocks.isEmpty();
    }

    public void clearCache() {
        this.cachedPos = null;
        this.cachedBlocks = Collections.emptyList();
    }
}
