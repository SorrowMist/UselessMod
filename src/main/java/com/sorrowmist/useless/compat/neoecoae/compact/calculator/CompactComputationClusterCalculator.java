package com.sorrowmist.useless.compat.neoecoae.compact.calculator;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Prevents ECO's delayed multi-block scan from invalidating compact C9. */
public final class CompactComputationClusterCalculator extends NEComputationClusterCalculator {
    public CompactComputationClusterCalculator(NEBlockEntity<NEComputationCluster, ?> target) {
        super(target);
    }

    @Override
    public void calculateMultiblock(ServerLevel level, BlockPos pos) {
    }

    @Override
    public void updateMultiblockAfterNeighborUpdate(ServerLevel level, BlockPos pos, BlockPos changedPos) {
    }

    @Override
    public boolean checkMultiblockScale(BlockPos min, BlockPos max) {
        return min.equals(max);
    }
}
