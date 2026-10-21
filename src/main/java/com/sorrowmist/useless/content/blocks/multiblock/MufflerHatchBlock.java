package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.MufflerHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 消声仓方块。
 *
 * <p>消声仓只能位于结构最上层中心，排烟口固定朝上，因此放置时忽略玩家视线方向。
 * 冒烟由核心在工作状态下驱动，方块自身不注册 ticker。</p>
 */
public final class MufflerHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public MufflerHatchBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.UP));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, Direction.UP);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MufflerHatchBlockEntity(pos, state);
    }
}
