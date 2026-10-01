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
    /**
     * 最近一次已向玩家提示过的「无法识别形状」标识。
     *
     * <p>第三方经 FTB 的 RegisterShapeEvent 注册的形状映射结果恒为默认形状，无法用形状值
     * 本身判断是否需要提示：不加去重会每次同步都重发 actionbar，只按形状值去重又会漏掉
     * 「连续切到两个不同未知形状」的第二次提示。故按标识记录，随玩家数据一起回收。
     *
     * <p>仅服务端使用，不参与客户端同步：判定与提示都发生在服务端，客户端该字段恒为 null。
     */
    private String notifiedUnsupportedShapeId = null;

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

    public String getNotifiedUnsupportedShapeId() {
        return this.notifiedUnsupportedShapeId;
    }

    public void setNotifiedUnsupportedShapeId(String shapeId) {
        this.notifiedUnsupportedShapeId = shapeId;
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
