package com.sorrowmist.useless.utils.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;

/**
 * ExtendedAE扩展样板供应器处理器
 */
public class ExPatternProviderHandler implements PatternProviderHandler {
    @Override
    public String getProviderType() {
        return "ExPatternProvider";
    }

    @Override
    public boolean isCompatible(BlockEntity blockEntity) {
        return blockEntity != null && blockEntity.getClass().getName().equals("com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider");
    }

    @Override
    public void clearPatterns(LevelAccessor levelAccessor, BlockPos pos) {
        // 这个方法在PatternProviderManager中已经实现，这里不需要重复实现
    }

    @Override
    public void syncPatterns(BlockPos masterPos, BlockPos slavePos, Direction direction, LevelAccessor levelAccessor) {
        // 这个方法在PatternProviderManager中已经实现，这里不需要重复实现
    }
}