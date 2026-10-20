package com.sorrowmist.useless.utils.mining.shape;

import com.sorrowmist.useless.core.config.ChainEquivalence;
import com.sorrowmist.useless.utils.mining.MiningUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 形状计算所需的上下文。
 *
 * <p>由连锁扫描在构造形状之前一次性装配：等价组、范围半径、数量上限与工具语义均在此固化，
 * 形状实现只需按布局枚举坐标并调用 {@link #check(BlockPos)} 过滤。
 *
 * @param level         世界
 * @param originPos     连锁原点方块位置
 * @param originState   原点方块状态
 * @param face          玩家点击的面；无有效命中时退化为玩家朝向
 * @param player        触发连锁的玩家
 * @param tool          主手工具
 * @param equivalence   等价组判定，与后续破坏阶段的二次校验必须为同一实例
 * @param forceMining   是否为强制挖掘语义
 * @param requireMineable 是否需要「工具可采集」门槛（挖矿语义为真，右键语义为假）
 * @param maxBlocks     本次连锁的方块数量上限
 * @param rangeX        X 轴范围半径
 * @param rangeY        Y 轴范围半径
 * @param rangeZ        Z 轴范围半径
 */
public record ChainMiningShapeContext(Level level, BlockPos originPos, BlockState originState, Direction face,
                                      Player player, ItemStack tool, ChainEquivalence equivalence,
                                      boolean forceMining, boolean requireMineable, int maxBlocks,
                                      int rangeX, int rangeY, int rangeZ) {

    /**
     * 判断坐标是否位于配置的连锁范围包围盒内。
     *
     * <p>形状自身不重复实现边界检查，越界一律视为不可用，避免长隧道形状在
     * 极端配置下挖穿到数量上限。
     *
     * @param pos 待判定坐标
     * @return 位于范围内返回 true
     */
    public boolean withinRange(BlockPos pos) {
        return Math.abs(pos.getX() - this.originPos.getX()) <= this.rangeX
                && Math.abs(pos.getY() - this.originPos.getY()) <= this.rangeY
                && Math.abs(pos.getZ() - this.originPos.getZ()) <= this.rangeZ;
    }

    /**
     * 判断坐标是否可作为本次连锁的目标。
     *
     * <p>依次校验范围、等价组、工具可采集与强制挖掘黑名单。该判定必须与破坏阶段的二次校验
     * 使用同一等价组实例，否则等价组扩出的方块会在破坏阶段被整体跳过。
     *
     * @param pos 待判定坐标
     * @return 可作为目标返回 true
     */
    public boolean check(BlockPos pos) {
        if (!withinRange(pos)) {
            return false;
        }
        BlockState state = this.level.getBlockState(pos);
        if (!this.equivalence.matches(state)) {
            return false;
        }
        if (this.requireMineable && !MiningUtils.canMineBlock(state, this.tool, this.forceMining)) {
            return false;
        }
        return !(this.forceMining && MiningUtils.isForceMiningBlacklisted(state));
    }
}
