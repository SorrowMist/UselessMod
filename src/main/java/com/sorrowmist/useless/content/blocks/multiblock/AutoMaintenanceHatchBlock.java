package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.content.blockentities.multiblock.AutoMaintenanceHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 自动维护仓方块。
 *
 * <p>参与多方块结构校验，与普通维护仓共用一个名额，二者择一即可成型。归属关系记录在方块实体中，
 * 同一个自动维护仓不能被相邻的多个核心共用。当前不承担任何自动化职责，方块实体留作后续存放状态的位置。</p>
 */
public final class AutoMaintenanceHatchBlock extends DirectionalMultiblockPartBlock implements EntityBlock {
    public AutoMaintenanceHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutoMaintenanceHatchBlockEntity(pos, state);
    }
}