package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子样板总线」：不落世界的 F9 智能样板总线。
 *
 * <p>它保留 ECO 原生的样板库存、样板解析与派单逻辑；紧凑 F9 会把它的
 * {@code ICraftingProvider} / {@code IECOPatternStorage} 服务挂到主机的网格节点上，
 * 于是玩家往紧凑方块里插的样板依然由 ECO 自己的总线实现去解析与派单。</p>
 */
public class ShadowPatternBusBlockEntity extends ECOCraftingPatternBusBlockEntity {

    public ShadowPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    @Nullable
    public IGrid getGrid() {
        IGridNode node = getGridNode();
        return node == null ? null : node.getGrid();
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
