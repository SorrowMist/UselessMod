package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.PagedMenuPageMemory;
import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import com.sorrowmist.useless.content.blockentities.multiblock.PassiveCraftingHatchBlockEntity;
import com.sorrowmist.useless.init.ModMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/** Paginated passive-pattern inventory with the hatch's interval controls. */
public final class PassiveCraftingHatchMenu extends PagedRecoverableMenu {
    private final @Nullable PassiveCraftingHatchBlockEntity hatch;
    private final ContainerData data;
    private final PassiveCraftingHatchBlockEntity.SlotStatus[] slotStatuses =
            new PassiveCraftingHatchBlockEntity.SlotStatus[PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS];

    public PassiveCraftingHatchMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    public PassiveCraftingHatchMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuType.PASSIVE_CRAFTING_HATCH_MENU.get(), containerId, inventory,
                handler(inventory, pos), pos, 8, 22, 44, 158, 44, 218,
                pageMemory(inventory, pos));
        hatch = inventory.player.level().getBlockEntity(pos)
                instanceof PassiveCraftingHatchBlockEntity found ? found : null;
        ContainerData liveData = hatch == null
                ? new SimpleContainerData(PassiveCraftingHatchBlockEntity.MENU_DATA_COUNT)
                : hatch.getMenuData();
        data = createMenuData(inventory.player.level().isClientSide, liveData);
        addDataSlots(data);
        for (int slot = 0; slot < slotStatuses.length; slot++) {
            slotStatuses[slot] = emptyStatus(slot);
        }
    }

    private static RecoverableItemStackHandler handler(Inventory inventory, BlockPos pos) {
        if (inventory.player.level().getBlockEntity(pos) instanceof PassiveCraftingHatchBlockEntity hatch) {
            return hatch.getPatterns();
        }
        return new RecoverableItemStackHandler(PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS, 0,
                () -> 0, stack -> false, () -> { });
    }

    @Nullable
    private static PagedMenuPageMemory pageMemory(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof PassiveCraftingHatchBlockEntity hatch
                ? hatch.getPageMemory() : null;
    }

    static ContainerData createMenuData(boolean clientSide, ContainerData liveData) {
        return clientSide
                ? new SimpleContainerData(PassiveCraftingHatchBlockEntity.MENU_DATA_COUNT)
                : liveData;
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
        return Math.max(0, Math.min(PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS, data.get(2)));
    }

    public int getConfiguredPatternSlots() {
        return Math.max(1, Math.min(PassiveCraftingHatchBlockEntity.MAX_PATTERN_SLOTS, data.get(9)));
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

    static long join(int low, int high) {
        return Integer.toUnsignedLong(low) | (long) high << 32;
    }

    public int getPatternSlotIndex(Slot slot) {
        int menuSlot = slots.indexOf(slot);
        return menuSlot >= 0 && menuSlot < SLOTS_PER_PAGE
                ? getPage() * SLOTS_PER_PAGE + menuSlot : -1;
    }

    public PassiveCraftingHatchBlockEntity.SlotStatus getSlotStatus(int slot) {
        return slot >= 0 && slot < slotStatuses.length ? slotStatuses[slot] : emptyStatus(slot);
    }

    public void updateSlotStatuses(Iterable<PassiveCraftingHatchBlockEntity.SlotStatus> statuses) {
        for (PassiveCraftingHatchBlockEntity.SlotStatus status : statuses) {
            if (status.slot() >= 0 && status.slot() < slotStatuses.length) {
                slotStatuses[status.slot()] = status;
            }
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        boolean changed = super.clickMenuButton(player, id);
        if (changed && hatch != null) {
            hatch.requestStatusSync();
        }
        return changed;
    }

    @Override
    public boolean stillValid(Player player) {
        return hatch != null && player.level().getBlockEntity(getBlockPos()) == hatch
                && player.distanceToSqr(getBlockPos().getX() + 0.5D, getBlockPos().getY() + 0.5D,
                getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    private static PassiveCraftingHatchBlockEntity.SlotStatus emptyStatus(int slot) {
        return new PassiveCraftingHatchBlockEntity.SlotStatus(
                slot, PassiveCraftingHatchBlockEntity.SlotState.EMPTY, 0, 0, "");
    }
}
