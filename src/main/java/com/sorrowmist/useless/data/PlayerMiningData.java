package com.sorrowmist.useless.data;

import com.sorrowmist.useless.utils.mining.shape.ChainMiningShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PlayerMiningData {
    private final UUID playerId;
    private boolean tabPressed = false;
    /** 当前连锁形状。属于玩家选择而非扫描结果，因此不随缓存清理而重置。 */
    private ChainMiningShapes shape = ChainMiningShapes.SHAPELESS;
    private BlockPos cachedPos = null;
    private BlockState cachedState = null;
    private List<BlockPos> cachedBlocks = Collections.emptyList();
    /**
     * 本次连锁的方块数量上限。
     *
     * <p>该值来自服务端配置，客户端无法自行读取，因此随同步包下发；仅用于界面显示，
     * 不参与任何判定，取值 0 表示尚未同步。
     */
    private int maxBlocks = 0;

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

    public ChainMiningShapes getShape() {
        return this.shape;
    }

    public void setShape(ChainMiningShapes shape) {
        this.shape = shape != null ? shape : ChainMiningShapes.SHAPELESS;
    }

    public int getMaxBlocks() {
        return this.maxBlocks;
    }

    public void setMaxBlocks(int maxBlocks) {
        this.maxBlocks = maxBlocks;
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
        this.cachedBlocks = cachedBlocks != null ? cachedBlocks : Collections.emptyList();
    }

    public boolean hasCachedBlocks() {
        return !this.cachedBlocks.isEmpty();
    }

    public void clearCache() {
        this.cachedPos = null;
        this.cachedState = null;
        this.cachedBlocks = Collections.emptyList();
    }
}
