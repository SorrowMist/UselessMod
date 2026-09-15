package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子主机」：一个不落在世界里的 C9 计算主机，只用来让 ECO 的
 * {@code NEComputationNetworkCluster#isEndgameEligible()} 认满 8 台满配主机。
 *
 * <p>它把等级、建造长度、网络交换模块三项固定成满配值，其余全部沿用 ECO 的原逻辑。</p>
 */
public class ShadowComputationHostBlockEntity extends ECOComputationSystemBlockEntity {

    public ShadowComputationHostBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public boolean hasNormalNetworkSwitch() {
        return false;
    }

    @Override
    public boolean hasHighEnergyNetworkSwitch() {
        return true;
    }

    @Override
    public int getSelectedBuildLength() {
        // 紧凑主机永远是「满长」建造：极限模式判定要求 selected == max。
        return getMaxBuildLength();
    }

    @Override
    public void setSelectedBuildLength(int length) {
        // 固定配置，忽略外部改写。
    }

    @Override
    public void updateState(boolean updateExposed) {
        // 影子组件不存在于世界，屏蔽一切方块状态写入。
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
