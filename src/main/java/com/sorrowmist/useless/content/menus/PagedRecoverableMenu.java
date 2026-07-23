package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.IntSupplier;

public class PagedRecoverableMenu extends AbstractContainerMenu {
    public static final int SLOTS_PER_PAGE = 27;
    public static final int PREVIOUS_PAGE = 0;
    public static final int NEXT_PAGE = 1;

    private final RecoverableItemStackHandler inventory;
    private final BlockPos blockPos;
    private final boolean clientSide;
    private int page;
    private int syncedPageCount = 1;
    private int syncedActivePageCount = 1;
    private final ContainerData pageData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> page;
                case 1 -> clientSide ? syncedPageCount : calculatePageCount();
                case 2 -> clientSide ? syncedActivePageCount : calculateActivePageCount();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) page = Math.max(0, value);
            else if (index == 1) syncedPageCount = Math.max(1, value);
            else if (index == 2) syncedActivePageCount = Math.max(1, value);
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    protected PagedRecoverableMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                                   RecoverableItemStackHandler inventory, BlockPos blockPos) {
        super(type, containerId);
        this.inventory = inventory;
        this.blockPos = blockPos.immutable();
        this.clientSide = playerInventory.player.level().isClientSide;
        IItemHandler pageView = new PageView(inventory, () -> page, clientSide);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new SlotItemHandler(pageView, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 85 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 143));
        }
        addDataSlots(pageData);
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return clientSide ? syncedPageCount : calculatePageCount();
    }

    private int calculatePageCount() {
        int highest = inventory.getActiveSlots() - 1;
        for (int slot = inventory.getSlots() - 1; slot >= inventory.getActiveSlots(); slot--) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                highest = slot;
                break;
            }
        }
        return Math.max(1, highest / SLOTS_PER_PAGE + 1);
    }

    public int getActivePageCount() {
        return clientSide ? syncedActivePageCount : calculateActivePageCount();
    }

    private int calculateActivePageCount() {
        return Math.max(1, inventory.getActiveSlots() / SLOTS_PER_PAGE);
    }

    public boolean isRecoveryPage() {
        return page >= getActivePageCount();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != PREVIOUS_PAGE && id != NEXT_PAGE) return false;
        int count = getPageCount();
        page = Math.floorMod(page + (id == NEXT_PAGE ? 1 : -1), count);
        broadcastChanges();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < SLOTS_PER_PAGE) {
            if (!moveItemStackTo(source, SLOTS_PER_PAGE, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!moveItemStackTo(source, 0, SLOTS_PER_PAGE, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockEntity blockEntity = player.level().getBlockEntity(blockPos);
        return blockEntity != null && blockEntity.getBlockPos().equals(blockPos)
                && player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D) <= 64.0D;
    }

    /**
     * SlotItemHandler writes initial/container updates through
     * IItemHandlerModifiable. On the server this view follows the selected
     * backing page; on the client it is a fixed 27-slot packet buffer. The
     * latter is deliberately independent of {@link #page}, because page-data
     * and slot-data packets are allowed to arrive in either order.
     */
    static final class PageView implements IItemHandlerModifiable {
        private final RecoverableItemStackHandler inventory;
        private final IntSupplier page;
        @Nullable
        private final ItemStackHandler clientPage;

        PageView(RecoverableItemStackHandler inventory, IntSupplier page, boolean clientSide) {
            this.inventory = Objects.requireNonNull(inventory, "inventory");
            this.page = Objects.requireNonNull(page, "page");
            this.clientPage = clientSide ? new ItemStackHandler(SLOTS_PER_PAGE) : null;
        }

        private int actual(int slot) {
            return page.getAsInt() * SLOTS_PER_PAGE + slot;
        }

        @Override
        public int getSlots() { return SLOTS_PER_PAGE; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (clientPage != null) {
                return clientPage.getStackInSlot(slot);
            }
            int actual = actual(slot);
            return actual < inventory.getSlots() ? inventory.getStackInSlot(actual) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (clientPage != null) {
                return clientPage.insertItem(slot, stack, simulate);
            }
            int actual = actual(slot);
            return actual < inventory.getSlots() ? inventory.insertItem(actual, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (clientPage != null) {
                return clientPage.extractItem(slot, amount, simulate);
            }
            int actual = actual(slot);
            return actual < inventory.getSlots() ? inventory.extractItem(actual, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            int actual = actual(slot);
            return actual < inventory.getSlots() ? inventory.getSlotLimit(actual) : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            int actual = actual(slot);
            return actual < inventory.getSlots() && inventory.isItemValid(actual, stack);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (clientPage != null) {
                clientPage.setStackInSlot(slot, stack);
                return;
            }
            int actual = actual(slot);
            if (actual < inventory.getSlots()) {
                inventory.setStackInSlot(actual, stack);
            }
        }
    }
}
