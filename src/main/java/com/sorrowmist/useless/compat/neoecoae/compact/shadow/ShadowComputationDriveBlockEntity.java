package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子晶阵驱动器」：不落世界的 C9 计算驱动器，装着满配的 CE9 闪存晶阵。
 *
 * <p>紧凑 C9 需要它满足「上下驱动器全部装满有效闪存晶阵」这一极限模式前置条件，
 * 同时为合成任务提供 ECO 原生的 CPU 存储字节。</p>
 */
public class ShadowComputationDriveBlockEntity extends ECOComputationDriveBlockEntity {

    public ShadowComputationDriveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
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
