package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子主机」：一个不落在世界里的 F9 合成主机，只用来让 ECO 的
 * {@code CraftingCapabilitySnapshot#isVirtualTopologyEligible()} 认满 8 台满长高能主机。
 *
 * <p>它把等级、建造长度、网络交换模块固定成满配值，其余全部沿用 ECO 原逻辑。</p>
 */
public class ShadowCraftingHostBlockEntity extends ECOCraftingSystemBlockEntity {

    public ShadowCraftingHostBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
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
        // 紧凑主机永远是「满长」建造：虚拟合成模式判定要求实际 FX 通道数 == 满长值。
        return getMaxBuildLength();
    }

    @Override
    public void setSelectedBuildLength(int length) {
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
