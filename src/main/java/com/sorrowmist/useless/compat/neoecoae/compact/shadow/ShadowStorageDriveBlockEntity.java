package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ShadowStorageDriveBlockEntity extends ECODriveBlockEntity {
    private boolean installing = true;

    public ShadowStorageDriveBlockEntity(BlockEntityType<ECODriveBlockEntity> type, BlockPos pos,
                                         BlockState state, ItemStack cell, ServerLevel level) {
        super(type, pos, state);
        super.setCellStack(cell);
        installing = false;
        setLevel(level);
    }

    @Override
    public void setCellStack(@Nullable ItemStack stack) {
    }

    @Override
    public ItemStack getBuiltInCellForDisplay() {
        return getCellStack();
    }

    @Override
    public boolean canExtractCell() {
        return installing;
    }

    @Override
    public Component getCellExtractionBlockReasonText() {
        return Component.translatable("tooltip.useless_mod.compact.l9.built_in_cell");
    }

    @Override
    public @Nullable IECOStorageCell getCellInventory() {
        return null;
    }

    @Override
    public void updateState(boolean updateExposed) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public void markForUpdate() {
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
    }
}
