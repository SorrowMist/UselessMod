package com.sorrowmist.useless.api.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public class PlayerMiningData {
    private final UUID playerId;
    private boolean tabPressed = false;
    private BlockPos cachedPos = null;
    private BlockState cachedState = null;
    private List<BlockPos> cachedBlocks = null;

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

    public boolean isCacheValid(BlockPos currentPos) {
        return this.cachedPos != null && this.cachedPos.equals(currentPos) && this.hasCachedBlocks();
    }

    public BlockPos getCachedPos() {
        return this.cachedPos;
    }

    public void setCachedPos(BlockPos cachedPos) {
        this.cachedPos = cachedPos;
    }

    public BlockState getCachedState() {
        return this.cachedState;
    }

    public void setCachedState(BlockState cachedState) {
        this.cachedState = cachedState;
    }

    public List<BlockPos> getCachedBlocks() {
        return this.cachedBlocks;
    }

    public void setCachedBlocks(List<BlockPos> cachedBlocks) {
        this.cachedBlocks = cachedBlocks;
    }

    public boolean hasCachedBlocks() {
        return this.cachedBlocks != null && !this.cachedBlocks.isEmpty();
    }

    public void clearCache() {
        this.cachedPos = null;
        this.cachedState = null;
        this.cachedBlocks = null;
    }
}
