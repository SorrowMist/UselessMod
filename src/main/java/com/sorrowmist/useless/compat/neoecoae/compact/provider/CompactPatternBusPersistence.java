package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Native AE2 snapshots for physical slots omitted by managed persistence on shadow buses. */
public final class CompactPatternBusPersistence {
    private static final String SLOTS = "useless_compact_native_pattern_slots";
    private static final String SLOT_COUNT = "useless_compact_native_pattern_slot_count";

    private CompactPatternBusPersistence() {
    }

    public static void writeSlots(ECOCraftingPatternBusBlockEntity bus, CompoundTag data) {
        var inventory = bus.getPatternSlotInventory();
        var snapshot = new AppEngInternalInventory(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) {
            snapshot.setItemDirect(slot, inventory.getStackInSlot(slot).copy());
        }
        data.putInt(SLOT_COUNT, snapshot.size());
        snapshot.writeToNBT(data, SLOTS);
    }

    public static void readSlots(ECOCraftingPatternBusBlockEntity bus, CompoundTag data) {
        // Older compact payloads still use the bus's own current/20.4.2 migration reader.
        if (!data.contains(SLOT_COUNT, Tag.TAG_INT)) {
            return;
        }
        int count = data.getInt(SLOT_COUNT);
        if (count < 0 || count > NEConfig.getMaxCraftingPatternBusSlotCount()) {
            throw new IllegalArgumentException("Invalid compact pattern slot count: " + count);
        }
        var snapshot = new AppEngInternalInventory(count);
        snapshot.readFromNBT(data, SLOTS);
        if (count > bus.getPatternSlotCount()) {
            // Native writes reject slots outside visible pages. Restore the native retained-page
            // field first, including when the configured page count was reduced since capture.
            var pages = new CompoundTag();
            pages.putInt("importedLegacyPages", (count + ECOCraftingPatternBusBlockEntity.SLOTS_PER_PAGE - 1)
                    / ECOCraftingPatternBusBlockEntity.SLOTS_PER_PAGE);
            bus.loadManagedPersistentData(pages);
            if (count > bus.getPatternSlotCount()) {
                throw new IllegalStateException("Native pattern pages could not be restored");
            }
        }
        int slotsToRestore = Math.max(count, bus.getPatternSlotCount());
        bus.beginPatternBatch();
        try {
            for (int slot = 0; slot < slotsToRestore; slot++) {
                bus.setPatternDirect(slot, slot < count ? snapshot.getStackInSlot(slot).copy() : ItemStack.EMPTY);
            }
        } finally {
            bus.endPatternBatch();
        }
    }
}
