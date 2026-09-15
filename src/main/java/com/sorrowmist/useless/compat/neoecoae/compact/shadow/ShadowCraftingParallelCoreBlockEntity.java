package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingParallelCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子并行核心」：不落世界的 F9 并行核心，为紧凑 F9 贡献 ECO 原生的 FT 并行线程能力。
 */
public class ShadowCraftingParallelCoreBlockEntity extends ECOCraftingParallelCoreBlockEntity {

    public ShadowCraftingParallelCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public void updateState(boolean updateExposed) {
    }

    @Override
    public void markForUpdate() {
    }

    @Override
    public void saveChanges() {
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.noneOf(Direction.class);
    }
}
