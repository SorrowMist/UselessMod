package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.AutoMaintenanceHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Replaces the ordinary hatch; the controller performs automatic maintenance without a separate ticker. */
public final class AutoMaintenanceHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public AutoMaintenanceHatchBlock(Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoMaintenanceHatchBlockEntity(pos, state);
    }
}
