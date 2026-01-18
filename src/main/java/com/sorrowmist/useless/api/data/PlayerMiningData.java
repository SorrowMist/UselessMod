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
        return playerId;
    }

    public boolean isTabPressed() {
        return tabPressed;
    }

    public void setTabPressed(boolean tabPressed) {
        this.tabPressed = tabPressed;
    }

    public BlockPos getCachedPos() {
        return cachedPos;
    }

    public void setCachedPos(BlockPos cachedPos) {
        this.cachedPos = cachedPos;
    }

    public BlockState getCachedState() {
        return cachedState;
    }

    public void setCachedState(BlockState cachedState) {
        this.cachedState = cachedState;
    }

    public List<BlockPos> getCachedBlocks() {
        return cachedBlocks;
    }

    public void setCachedBlocks(List<BlockPos> cachedBlocks) {
        this.cachedBlocks = cachedBlocks;
    }

    public boolean hasCachedBlocks() {
        return cachedBlocks != null && !cachedBlocks.isEmpty();
    }

    public void clearCache() {
        cachedPos = null;
        cachedState = null;
        cachedBlocks = null;
    }
}
