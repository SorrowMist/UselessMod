package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子工作核心」：不落世界的 F9 工作核心（FX 执行通道）。
 *
 * <p>它的网格节点只用于让 ECO 线程访问宿主网格，实际执行由紧凑 F9 主机在每个服务器 tick
 * 手动驱动 ECO 原生的 {@code tickingRequest(...)}，避免网格 ticker 重复推进。</p>
 */
public class ShadowCraftingWorkerBlockEntity extends ECOCraftingWorkerBlockEntity {

    public ShadowCraftingWorkerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        getMainNode().setFlags();
        getMainNode().setInWorldNode(false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        // The host drives shadow workers explicitly after their nodes join the host grid.
        return node == null ? super.tickingRequest(null, ticksSinceLastCall) : TickRateModulation.IDLE;
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
