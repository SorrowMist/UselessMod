package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
    private static final String PAGE_MEMORY_TAG = "useless_mod:paged_menu_pages";

    private final RecoverableItemStackHandler inventory;
    private final BlockPos blockPos;
    private final Player player;
    private final boolean clientSide;
    @Nullable
    private final String pageMemoryId;
    private int page;
    private int syncedPageCount = 1;
    private int syncedActivePageCount = 1;
    private final ContainerData pageData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> getPage();
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
        this(type, containerId, playerInventory, inventory, blockPos,
                8, 18, 8, 85, 8, 143, null);
    }

    protected PagedRecoverableMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                                   RecoverableItemStackHandler inventory, BlockPos blockPos,
                                   @Nullable String pageMemoryId) {
        this(type, containerId, playerInventory, inventory, blockPos,
                8, 18, 8, 85, 8, 143, pageMemoryId);
    }

    protected PagedRecoverableMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                                   RecoverableItemStackHandler inventory, BlockPos blockPos,
                                   int storageX, int storageY,
                                   int playerInventoryX, int playerInventoryY,
                                   int hotbarX, int hotbarY) {
        this(type, containerId, playerInventory, inventory, blockPos,
                storageX, storageY, playerInventoryX, playerInventoryY, hotbarX, hotbarY, null);
    }

    protected PagedRecoverableMenu(MenuType<?> type, int containerId, Inventory playerInventory,
                                   RecoverableItemStackHandler inventory, BlockPos blockPos,
                                   int storageX, int storageY,
                                   int playerInventoryX, int playerInventoryY,
                                   int hotbarX, int hotbarY,
                                   @Nullable String pageMemoryId) {
        super(type, containerId);
        this.inventory = inventory;
        this.blockPos = blockPos.immutable();
        this.player = playerInventory.player;
        this.clientSide = player.level().isClientSide;
        this.pageMemoryId = pageMemoryId;
        int rememberedPage = pageMemoryId == null || clientSide
                ? 0
                : readRememberedPage(player.getPersistentData(), pageMemoryKey());
        this.page = rememberedPage;
        if (!clientSide) {
            this.page = getPage();
            if (this.page != rememberedPage) {
                rememberPage();
            }
        }
        IItemHandler pageView = new PageView(inventory, () -> page, clientSide);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new SlotItemHandler(pageView, column + row * 9,
                        storageX + column * 18, storageY + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        playerInventoryX + column * 18, playerInventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, hotbarX + column * 18, hotbarY));
        }
        addDataSlots(pageData);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    static String pageMemoryKey(String pageMemoryId, String dimension, BlockPos blockPos) {
        return pageMemoryId + "|" + dimension + "|" + blockPos.asLong();
    }

    static int readRememberedPage(CompoundTag persistentData, String key) {
        if (!persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) return 0;
        CompoundTag playerData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        if (!playerData.contains(PAGE_MEMORY_TAG, Tag.TAG_COMPOUND)) return 0;
        CompoundTag pages = playerData.getCompound(PAGE_MEMORY_TAG);
        return pages.contains(key, Tag.TAG_INT) ? Math.max(0, pages.getInt(key)) : 0;
    }

    static void writeRememberedPage(CompoundTag persistentData, String key, int page) {
        CompoundTag playerData;
        if (persistentData.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            playerData = persistentData.getCompound(Player.PERSISTED_NBT_TAG);
        } else {
            playerData = new CompoundTag();
        }

        CompoundTag pages = playerData.contains(PAGE_MEMORY_TAG, Tag.TAG_COMPOUND)
                ? playerData.getCompound(PAGE_MEMORY_TAG) : new CompoundTag();
        pages.putInt(key, Math.max(0, page));
        playerData.put(PAGE_MEMORY_TAG, pages);
        persistentData.put(Player.PERSISTED_NBT_TAG, playerData);
    }

    public int getPage() {
        return Math.min(page, Math.max(0, getPageCount() - 1));
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
        return Math.max(1, (inventory.getActiveSlots() + SLOTS_PER_PAGE - 1) / SLOTS_PER_PAGE);
    }

    public boolean isRecoveryPage() {
        return getPage() >= getActivePageCount();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != PREVIOUS_PAGE && id != NEXT_PAGE) return false;
        int count = getPageCount();
        page = Math.floorMod(getPage() + (id == NEXT_PAGE ? 1 : -1), count);
        rememberPage();
        broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        // A configuration or coil-tier reduction can remove pages while a
        // viewer has one selected. Keep the server-side page in range before
        // slot synchronization maps the page view onto backing storage.
        if (!clientSide) {
            int clampedPage = getPage();
            if (page != clampedPage) {
                page = clampedPage;
                rememberPage();
            }
        }
        super.broadcastChanges();
    }

    private String pageMemoryKey() {
        return pageMemoryKey(pageMemoryId, player.level().dimension().location().toString(), blockPos);
    }

    private void rememberPage() {
        if (!clientSide && pageMemoryId != null) {
            writeRememberedPage(player.getPersistentData(), pageMemoryKey(), getPage());
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < SLOTS_PER_PAGE) {
            if (!moveItemStackTo(source, SLOTS_PER_PAGE, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = clientSide
                    ? !isRecoveryPage() && moveItemStackTo(source, 0, SLOTS_PER_PAGE, false)
                    : !isRecoveryPage()
                    && insertIntoActiveSlots(inventory, source, getPage() * SLOTS_PER_PAGE);
            if (!moved) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    static boolean insertIntoActiveSlots(
            RecoverableItemStackHandler inventory, ItemStack stack, int firstSlot) {
        if (stack.isEmpty()) return false;
        int activeSlots = inventory.getActiveSlots();
        if (activeSlots <= 0) return false;

        ItemStack remaining = stack.copy();
        int first = Math.floorMod(firstSlot, activeSlots);
        // Match existing stacks before claiming empty slots, like vanilla quick-move.
        final ItemStack[] remainingHolder = {remaining};
        inventory.withChangeBatch(() -> {
            int firstPass = stack.getMaxStackSize() <= 1 ? 1 : 0;
            for (int pass = firstPass; pass < 2 && !remainingHolder[0].isEmpty(); pass++) {
                boolean emptySlots = pass == 1;
                for (int offset = 0; offset < activeSlots && !remainingHolder[0].isEmpty(); offset++) {
                    int slot = (first + offset) % activeSlots;
                    ItemStack existing = inventory.getStackInSlot(slot);
                    if (existing.isEmpty() != emptySlots) continue;
                    // An occupied slot containing another stack cannot accept this item. Avoid
                    // invoking the handler validator for every unrelated occupied slot.
                    if (!existing.isEmpty()
                            && !ItemStack.isSameItemSameComponents(existing, remainingHolder[0])) {
                        continue;
                    }
                    remainingHolder[0] = inventory.insertItem(slot, remainingHolder[0], false);
                }
            }
        });
        remaining = remainingHolder[0];

        int inserted = stack.getCount() - remaining.getCount();
        if (inserted <= 0) return false;
        stack.shrink(inserted);
        return true;
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
