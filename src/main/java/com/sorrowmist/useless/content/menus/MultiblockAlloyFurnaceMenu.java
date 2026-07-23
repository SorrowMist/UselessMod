package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class MultiblockAlloyFurnaceMenu extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private final ContainerData data;
    @Nullable
    private final MultiblockAlloyFurnaceCoreBlockEntity core;

    public MultiblockAlloyFurnaceMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public MultiblockAlloyFurnaceMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.MULTIBLOCK_ALLOY_FURNACE_MENU.get(), containerId);
        blockPos = pos.immutable();
        core = inventory.player.level().getBlockEntity(pos) instanceof MultiblockAlloyFurnaceCoreBlockEntity value
                ? value : null;
        // The server's live ContainerData is read-only (its set method is a
        // no-op). A client menu must always expose a writable buffer so the
        // container sync packets can populate it, even when the client-side
        // block entity is already present.
        boolean clientSide = inventory.player.level().isClientSide;
        data = createMenuData(clientSide,
                !clientSide && core != null ? core.getMenuData() : null);
        addDataSlots(data);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 140 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 198));
        }
    }

    public BlockPos getBlockPos() { return blockPos; }
    public boolean isFormed() { return data.get(0) != 0; }
    public int getCoilTier() { return data.get(1); }
    public long getEnergy() { return join(data.get(2), data.get(3)); }
    public long getCapacity() { return join(data.get(4), data.get(5)); }
    public int getActiveTasks() { return data.get(6); }
    public int getRedstoneMode() { return data.get(7); }
    public int getMaxTasks() { return data.get(8); }
    @Nullable public MultiblockAlloyFurnaceCoreBlockEntity getCore() { return core; }

    static ContainerData createMenuData(boolean clientSide, @Nullable ContainerData serverData) {
        return !clientSide && serverData != null
                ? serverData
                : new SimpleContainerData(MultiblockAlloyFurnaceCoreBlockEntity.MENU_DATA_COUNT);
    }

    static long join(int low, int high) {
        return Integer.toUnsignedLong(low) | (long) high << 32;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(blockPos) instanceof MultiblockAlloyFurnaceCoreBlockEntity
                && player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D) <= 64.0D;
    }
}
