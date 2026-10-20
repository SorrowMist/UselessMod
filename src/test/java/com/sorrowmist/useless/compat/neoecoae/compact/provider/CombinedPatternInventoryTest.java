package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.inventories.InternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CombinedPatternInventoryTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void managementReadsAndWritesThePhysicalPatternSlots() throws ReflectiveOperationException {
        var bus = createBus();
        ItemStack disk = new ItemStack(Items.PAPER);
        bus.itemHandler.setStackInSlot(0, disk);
        var combined = new CombinedPatternInventory();
        combined.bind(List.of(bus));

        assertTrue(ItemStack.isSameItemSameTags(disk, combined.getStackInSlot(0)));
        assertEquals(1, combined.getSlotLimit(0));
        combined.setItemDirect(1, disk);
        assertTrue(ItemStack.isSameItemSameTags(disk, combined.getStackInSlot(1)));
    }

    @Test
    void terminalIncludesAppendedRowsAndDelegatesTheirTakeAndInsertProtocol() {
        var first = mock(ECOCraftingPatternBusBlockEntity.class);
        var second = mock(ECOCraftingPatternBusBlockEntity.class);
        var firstView = mock(InternalInventory.class);
        var secondView = mock(InternalInventory.class);
        var guardedSlot = mock(InternalInventory.class);
        when(first.getPatternSlotCount()).thenReturn(2);
        when(second.getPatternSlotCount()).thenReturn(2);
        when(first.getTerminalPatternInventory()).thenReturn(firstView);
        when(second.getTerminalPatternInventory()).thenReturn(secondView);
        when(firstView.size()).thenReturn(3); // Two physical slots plus one disk recipe.
        when(secondView.size()).thenReturn(2);
        when(firstView.getSlotInv(2)).thenReturn(guardedSlot);
        when(firstView.getSlotInv(0)).thenReturn(InternalInventory.empty()); // Hidden disk.
        var combined = new CombinedPatternInventory();
        combined.bind(List.of(first, second));
        InternalInventory terminal = combined.createTerminalView();
        ItemStack pattern = new ItemStack(Items.PAPER);
        when(firstView.extractItem(2, 1, false)).thenReturn(pattern);
        when(firstView.insertItem(2, pattern, false)).thenReturn(pattern); // Guard refuses insertion.

        assertEquals(5, terminal.size());
        assertSame(guardedSlot, terminal.getSlotInv(2));
        assertEquals(0, terminal.getSlotInv(0).size());
        assertSame(pattern, terminal.extractItem(2, 1, false));
        assertSame(pattern, terminal.insertItem(2, pattern, false));
        terminal.setItemDirect(3, pattern);
        verify(secondView).setItemDirect(0, pattern);
        // A real bus gaining pages must not shift the open menu's already published slot IDs.
        when(first.getPatternSlotCount()).thenReturn(4);
        assertEquals(6, combined.size());
        assertEquals(5, terminal.size());
        terminal.setItemDirect(3, ItemStack.EMPTY);
        verify(secondView).setItemDirect(0, ItemStack.EMPTY);
    }

    private static ECOCraftingPatternBusBlockEntity createBus() throws ReflectiveOperationException {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var field = ECOCraftingPatternBusBlockEntity.class.getField("itemHandler");
        field.setAccessible(true);
        field.set(bus, new ItemStackHandler(4) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }
        });
        when(bus.getPatternSlotCount()).thenReturn(3);
        return bus;
    }
}
