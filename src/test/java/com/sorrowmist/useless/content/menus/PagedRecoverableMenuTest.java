package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.content.blockentities.RecoverableItemStackHandler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedRecoverableMenuTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void serverViewMapsSlotWritesToTheSelectedBackingPage() {
        AtomicInteger page = new AtomicInteger();
        RecoverableItemStackHandler backing = handler();
        backing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        backing.setStackInSlot(27, new ItemStack(Items.GOLD_INGOT, 3));
        backing.setStackInSlot(539, new ItemStack(Items.EMERALD, 4));
        PagedRecoverableMenu.PageView view =
                new PagedRecoverableMenu.PageView(backing, page::get, false);
        SlotItemHandler slot = new SlotItemHandler(view, 0, 0, 0);

        assertInstanceOf(IItemHandlerModifiable.class, view);
        assertTrue(slot.getItem().is(Items.IRON_INGOT));
        page.set(1);
        assertTrue(slot.getItem().is(Items.GOLD_INGOT));

        slot.set(new ItemStack(Items.DIAMOND, 4));
        assertTrue(backing.getStackInSlot(27).is(Items.DIAMOND));
        assertEquals(4, backing.getStackInSlot(27).getCount());

        page.set(19);
        SlotItemHandler finalSlot = new SlotItemHandler(view, 26, 0, 0);
        assertTrue(finalSlot.getItem().is(Items.EMERALD));
        assertEquals(4, finalSlot.getItem().getCount());
    }

    @Test
    void clientViewAcceptsContentPacketsIndependentlyOfPagePacketOrder() {
        AtomicInteger page = new AtomicInteger();
        RecoverableItemStackHandler backing = handler();
        backing.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        backing.setStackInSlot(27, new ItemStack(Items.GOLD_INGOT));
        PagedRecoverableMenu.PageView view =
                new PagedRecoverableMenu.PageView(backing, page::get, true);
        SlotItemHandler slot = new SlotItemHandler(view, 0, 0, 0);

        assertTrue(slot.getItem().isEmpty());
        page.set(1); // The page-data packet may arrive before the slot packet.
        assertDoesNotThrow(() -> slot.set(new ItemStack(Items.DIAMOND, 5)));
        assertTrue(slot.getItem().is(Items.DIAMOND));
        assertEquals(5, slot.getItem().getCount());

        page.set(0);
        assertTrue(slot.getItem().is(Items.DIAMOND));
        assertTrue(backing.getStackInSlot(0).is(Items.IRON_INGOT));
        assertTrue(backing.getStackInSlot(27).is(Items.GOLD_INGOT));
        assertDoesNotThrow(() -> slot.set(ItemStack.EMPTY));
        assertTrue(slot.getItem().isEmpty());
    }

    private static RecoverableItemStackHandler handler() {
        return new RecoverableItemStackHandler(() -> 54, stack -> true, () -> {
        });
    }
}
