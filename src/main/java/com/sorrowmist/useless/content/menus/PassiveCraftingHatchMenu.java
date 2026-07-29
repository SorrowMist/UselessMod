package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class PassiveCraftingHatchMenu extends AbstractContainerMenu {
    public static final int PATTERN_COLUMNS = 6;
    public static final int PATTERN_ROWS = 5;
    public static final int PATTERN_SLOTS = PATTERN_COLUMNS * PATTERN_ROWS;

    private final BlockPos blockPos;
    @Nullable
    private final PassiveCraftingHatchBlockEntity hatch;
    private final ContainerData data;
    private List<PassiveCraftingHatchBlockEntity.SlotStatus> slotStatuses;

    public PassiveCraftingHatchMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public PassiveCraftingHatchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.PASSIVE_CRAFTING_HATCH_MENU.get(), containerId);
        this.blockPos = pos.immutable();
        this.hatch = inventory.player.level().getBlockEntity(pos)
                instanceof PassiveCraftingHatchBlockEntity found ? found : null;
        boolean clientSide = inventory.player.level().isClientSide;
        ContainerData liveData = hatch == null
                ? new SimpleContainerData(PassiveCraftingHatchBlockEntity.MENU_DATA_COUNT)
                : hatch.getMenuData();
        this.data = createMenuData(clientSide, liveData);

        var patternInventory = clientSide
                ? new ItemStackHandler(PATTERN_SLOTS)
                : hatch == null ? unavailableHandler() : hatch.getPatterns();
        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int column = 0; column < PATTERN_COLUMNS; column++) {
                int slot = column + row * PATTERN_COLUMNS;
                addSlot(new SlotItemHandler(patternInventory, slot,
                        8 + column * 18, 22 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        44 + column * 18, 158 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 44 + column * 18, 218));
        }

        addDataSlots(data);
        List<PassiveCraftingHatchBlockEntity.SlotStatus> initial = new ArrayList<>(PATTERN_SLOTS);
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            initial.add(new PassiveCraftingHatchBlockEntity.SlotStatus(
                    slot, PassiveCraftingHatchBlockEntity.SlotState.EMPTY, 0, 0, ""));
        }
        slotStatuses = List.copyOf(initial);
    }

    private static RecoverableItemStackHandler unavailableHandler() {
        return new RecoverableItemStackHandler(PATTERN_SLOTS, 0, () -> 0,
                stack -> false, () -> { });
    }

    static ContainerData createMenuData(boolean clientSide, ContainerData liveData) {
        return clientSide
                ? new SimpleContainerData(PassiveCraftingHatchBlockEntity.MENU_DATA_COUNT)
                : liveData;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    @Nullable
    public PassiveCraftingHatchBlockEntity getHatch() {
        return hatch;
    }

    public boolean isFormed() {
        return data.get(0) != 0;
    }

    public int getCoilTier() {
        return data.get(1);
    }

    public int getActivePatternSlots() {
        return Math.max(0, Math.min(PATTERN_SLOTS, data.get(2)));
    }

    public int getIntervalTicks() {
        return data.get(3);
    }

    public int getCountdownTicks() {
        return data.get(4);
    }

    public long getMultiplier() {
        return join(data.get(5), data.get(6));
    }

    public long getMaxMultiplier() {
        return Math.max(1L, join(data.get(7), data.get(8)));
    }

    public boolean isSlotBusy(int slot) {
        return slot >= 0 && slot < PATTERN_SLOTS && (data.get(9) & (1 << slot)) != 0;
    }

    static long join(int low, int high) {
        return Integer.toUnsignedLong(low) | (long) high << 32;
    }

    public PassiveCraftingHatchBlockEntity.SlotStatus getSlotStatus(int slot) {
        if (slot < 0 || slot >= slotStatuses.size()) {
            return new PassiveCraftingHatchBlockEntity.SlotStatus(
                    slot, PassiveCraftingHatchBlockEntity.SlotState.EMPTY, 0, 0, "");
        }
        return slotStatuses.get(slot);
    }

    public void updateSlotStatuses(List<PassiveCraftingHatchBlockEntity.SlotStatus> statuses) {
        PassiveCraftingHatchBlockEntity.SlotStatus[] updated =
                slotStatuses.toArray(PassiveCraftingHatchBlockEntity.SlotStatus[]::new);
        for (PassiveCraftingHatchBlockEntity.SlotStatus status : statuses) {
            if (status.slot() >= 0 && status.slot() < PATTERN_SLOTS) {
                updated[status.slot()] = status;
            }
        }
        slotStatuses = List.of(updated);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < PATTERN_SLOTS) {
            if (!moveItemStackTo(source, PATTERN_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(source, 0, PATTERN_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return hatch != null && player.level().getBlockEntity(blockPos) == hatch
                && player.distanceToSqr(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D,
                blockPos.getZ() + 0.5D) <= 64.0D;
    }
}
