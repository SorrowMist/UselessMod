package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

public final class ShadowStorageDriveBlockEntity extends ECODriveBlockEntity {
    private static final Method MARK_MEMBER = findMemberMarker();
    private boolean installing = true;

    public ShadowStorageDriveBlockEntity(BlockEntityType<ECODriveBlockEntity> type, BlockPos pos,
                                         BlockState state, ItemStack member, UUID domain) {
        super(type, pos, state);
        try {
            MARK_MEMBER.invoke(null, member, domain);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not mark the built-in ECO storage cell", failure);
        }
        super.setCellStack(member);
        installing = false;
    }

    private static Method findMemberMarker() {
        try {
            Class<?> storageMember;
            try {
                storageMember = Class.forName("cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember");
            } catch (ClassNotFoundException olderEco) {
                storageMember = Class.forName("cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember");
            }
            return storageMember.getMethod("markMember", ItemStack.class, UUID.class);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not locate the ECO storage member marker", failure);
        }
    }

    @Override
    public void setCellStack(@Nullable ItemStack stack) {
    }

    @Override
    public boolean canExtractCell() {
        return installing;
    }

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
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
    }
}
