package com.sorrowmist.useless.compat.neoecoae.compact.provider;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    void managementReadsPhysicalSlotsEvenWhenTheTerminalHidesThem() {
        var bus = mock(ECOCraftingPatternBusBlockEntity.class);
        var raw = new AppEngInternalInventory(null, 2, 1);
        ItemStack disk = new ItemStack(Items.PAPER);
        raw.setItemDirect(0, disk);
        var terminal = mock(InternalInventory.class);
        when(terminal.getStackInSlot(0)).thenReturn(ItemStack.EMPTY);
        when(bus.getPatternSlotCount()).thenReturn(2);
        when(bus.getPatternSlotInventory()).thenReturn(raw);
        when(bus.getTerminalPatternInventory()).thenReturn(terminal);
        var combined = new CombinedPatternInventory();
        combined.bind(List.of(bus));

        assertSame(disk, combined.getStackInSlot(0));
        assertEquals(1, combined.getSlotLimit(0));
        combined.setItemDirect(1, disk);
        verify(bus).setPatternDirect(1, disk);
        verifyNoInteractions(terminal);
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
        verify(first, never()).setPatternDirect(anyInt(), any());
        verify(second, never()).setPatternDirect(anyInt(), any());

        // A real bus gaining pages must not shift the open menu's already published slot IDs.
        when(first.getPatternSlotCount()).thenReturn(4);
        assertEquals(6, combined.size());
        assertEquals(5, terminal.size());
        terminal.setItemDirect(3, ItemStack.EMPTY);
        verify(secondView).setItemDirect(0, ItemStack.EMPTY);
    }
}
