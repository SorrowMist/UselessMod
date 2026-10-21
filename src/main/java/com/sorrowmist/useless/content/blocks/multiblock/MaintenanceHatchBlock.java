package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.MaintenanceHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 维护仓方块。
 *
 * <p>当前阶段仅作为结构件存在，没有任何交互与实际效果；方块实体只负责持久化归属信息，
 * 因此这里不注册 ticker，也不覆写掉落与放置逻辑。</p>
 */
public final class MaintenanceHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public MaintenanceHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MaintenanceHatchBlockEntity(pos, state);
    }
}