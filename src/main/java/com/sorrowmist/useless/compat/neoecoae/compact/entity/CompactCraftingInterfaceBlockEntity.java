package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/** The native ECO crafting interface, hosted inside an F9 instead of occupying a world block. */
public final class CompactCraftingInterfaceBlockEntity extends ECOMachineInterfaceBlockEntity<NECraftingCluster> {
    private CompactF9BlockEntity owner;

    public CompactCraftingInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NECraftingClusterCalculator::new);
        getMainNode().setFlags();
        getMainNode().setInWorldNode(false);
    }

    public void bindOwner(CompactF9BlockEntity owner) {
        this.owner = owner;
        setLevel(owner.getLevel());
    }

    @Override
    public boolean supportsCraftingInterfaceUi() {
        return true;
    }

    @Override
    public void rpcToServer(String methodName, Object... args) {
        // The checked world position contains the F9. Its RPC bridge forwards the native handshake here.
        owner.rpcToServer(methodName, args);
    }

    @Override
    public void onReady() {}

    @Override
    public void updateMultiBlock(BlockPos changedPos) {}

    @Override
    public void rebuildMultiblock() {}

    @Override
    public void updateState(boolean updateExposed) {}

    @Override
    public void markForUpdate() {}

    @Override
    public void saveChanges() {
        if (owner != null) owner.setChanged();
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return Set.of();
    }
}
