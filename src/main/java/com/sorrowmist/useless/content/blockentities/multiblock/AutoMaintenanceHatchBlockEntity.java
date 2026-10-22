package com.sorrowmist.useless.content.blockentities.multiblock;

import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自动维护仓方块实体。
 *
 * <p>当前不含任何状态与逻辑，仅为方块提供实体载体；后续的自动维护行为在此扩展。</p>
 */
public final class AutoMaintenanceHatchBlockEntity extends BlockEntity {
    public AutoMaintenanceHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTO_MAINTENANCE_HATCH.get(), pos, state);
    }
}