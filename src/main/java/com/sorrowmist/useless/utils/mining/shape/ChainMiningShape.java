package com.sorrowmist.useless.utils.mining.shape;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 连锁挖掘形状。
 *
 * <p>形状负责在给定上下文下生成本次连锁的候选方块序列，序列顺序即挖掘顺序。
 * 形状只决定布局，某个候选方块是否真正被破坏仍由
 * {@link ChainMiningShapeContext#check(BlockPos)} 中的等价组判定与工具可采集判定共同决定。
 *
 * <p>形状的标识用于物品组件持久化，因此必须保持稳定，不得随枚举顺序调整而改变。
 *
 * <p>该形状抽象层的划分参考 FTB Ultimine（FTB 连锁）的挖掘形状设计。
 */
public interface ChainMiningShape {

    /**
     * {@return 该形状的稳定标识}
     */
    ResourceLocation getId();

    /**
     * 计算本次连锁的候选方块序列。
     *
     * @param context 形状上下文
     * @return 候选方块，按预期挖掘顺序排列
     */
    List<BlockPos> getBlocks(ChainMiningShapeContext context);
}
