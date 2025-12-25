package com.sorrowmist.useless.utils.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 样板供应器处理器接口，定义不同类型样板供应器的处理方法
 */
public interface PatternProviderHandler {
    /**
     * 获取供应器类型
     */
    String getProviderType();

    /**
     * 检查是否与给定的方块实体兼容
     */
    boolean isCompatible(BlockEntity blockEntity);

    /**
     * 清除指定位置的样板
     */
    void clearPatterns(LevelAccessor levelAccessor, BlockPos pos);

    /**
     * 同步主从样板供应器的样板
     */
    void syncPatterns(BlockPos masterPos, BlockPos slavePos, Direction direction, LevelAccessor levelAccessor);
}