package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompactPatternBusPersistenceTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private static ECOCraftingPatternBusBlockEntity bus(AppEngInternalInventory inventory) {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        when(bus.getPatternSlotInventory()).thenReturn(inventory);
        when(bus.getPatternSlotCount()).thenReturn(inventory.size());
        doAnswer(call -> {
            inventory.setItemDirect(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(bus).setPatternDirect(anyInt(), any(ItemStack.class));
        return bus;
    }

    @Test
    void nativeSnapshotPreservesSparseHighPageSlotsCountsAndNestedItemData() {
        var original = new AppEngInternalInventory(189);
        ItemStack encoded = new ItemStack(Items.PAPER);
        encoded.getOrCreateTag().putString("recipe", "minecraft:oak_planks");
        original.setItemDirect(0, encoded);
        ItemStack disk = new ItemStack(Items.WRITTEN_BOOK, 3);
        var diskData = new CompoundTag();
        diskData.putString("pattern_domain", "independent auxiliary storage");
        disk.getOrCreateTag().put("disk", diskData);
        original.setItemDirect(188, disk);
        var payload = new CompoundTag();
        // This is the managed inventory payload observed in the failing live test.
        payload.put("inventory", new CompoundTag());
        CompactPatternBusPersistence.writeSlots(bus(original), payload);

        var restored = new AppEngInternalInventory(189);
        restored.setItemDirect(42, new ItemStack(Items.DIRT));
        var target = bus(restored);
        CompactPatternBusPersistence.readSlots(target, payload.copy());

        assertTrue(ItemStack.matches(encoded, restored.getStackInSlot(0)));
        assertTrue(ItemStack.matches(disk, restored.getStackInSlot(188)));
        assertTrue(restored.getStackInSlot(42).isEmpty(), "A full snapshot must clear stale slots");
        verify(target).beginPatternBatch();
        verify(target).endPatternBatch();
        encoded.getOrCreateTag().putString("recipe", "changed after capture");
        assertEquals("minecraft:oak_planks", restored.getStackInSlot(0).getTag().getString("recipe"));
    }

    @Test
    void expandsNativePagesBeforeRestoringAHighSlotSnapshot() {
        var original = new AppEngInternalInventory(126);
        original.setItemDirect(125, new ItemStack(Items.PAPER));
        var payload = new CompoundTag();
        CompactPatternBusPersistence.writeSlots(bus(original), payload);

        var restored = new AppEngInternalInventory(126);
        var target = bus(restored);
        when(target.getPatternSlotCount()).thenReturn(63, 126, 126);

        CompactPatternBusPersistence.readSlots(target, payload);

        assertTrue(restored.getStackInSlot(125).is(Items.PAPER));
        verify(target).loadManagedPersistentData(argThat(tag -> tag.getInt("importedLegacyPages") == 2));
        verify(target).setPatternDirect(eq(125), argThat(stack -> stack.is(Items.PAPER)));
    }

    @Test
    void emptyNativeSnapshotRemovesPreviouslyLoadedPatterns() {
        var payload = new CompoundTag();
        CompactPatternBusPersistence.writeSlots(bus(new AppEngInternalInventory(2)), payload);
        var restored = new AppEngInternalInventory(3);
        restored.setItemDirect(0, new ItemStack(Items.PAPER));
        restored.setItemDirect(2, new ItemStack(Items.WRITTEN_BOOK));

        CompactPatternBusPersistence.readSlots(bus(restored), payload);

        assertTrue(restored.isEmpty());
    }

    @Test
    void legacyPayloadRetainsTheInventoryAlreadyLoadedByTheNativeBus() {
        var inventory = new AppEngInternalInventory(3);
        inventory.setItemDirect(1, new ItemStack(Items.PAPER));
        var target = bus(inventory);
        var legacy = new CompoundTag();
        legacy.put("patternInventory", new net.minecraft.nbt.ListTag());

        CompactPatternBusPersistence.readSlots(target, legacy);

        assertTrue(inventory.getStackInSlot(1).is(Items.PAPER));
        verify(target, never()).beginPatternBatch();
        verify(target, never()).setPatternDirect(anyInt(), any());
    }

    @Test
    void oversizedSnapshotIsRejectedBeforeAnyNativeInventoryMutation() {
        var payload = new CompoundTag();
        CompactPatternBusPersistence.writeSlots(bus(new AppEngInternalInventory(3)), payload);
        payload.putInt("useless_compact_native_pattern_slot_count", Integer.MAX_VALUE);
        var target = bus(new AppEngInternalInventory(3));

        assertThrows(IllegalArgumentException.class, () -> CompactPatternBusPersistence.readSlots(target, payload));
        verify(target, never()).beginPatternBatch();
        verify(target, never()).setPatternDirect(anyInt(), any());
    }
}
