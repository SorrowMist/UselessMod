package com.sorrowmist.useless.content.blockentities;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecoverableItemStackHandlerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void shrinkingCapacityKeepsOverflowWithdrawOnly() {
        AtomicInteger activeSlots = new AtomicInteger(108);
        AtomicInteger changes = new AtomicInteger();
        RecoverableItemStackHandler handler = new RecoverableItemStackHandler(
                activeSlots::get, stack -> stack.is(Items.IRON_INGOT), changes::incrementAndGet);

        assertTrue(handler.insertItem(80, new ItemStack(Items.IRON_INGOT, 12), false).isEmpty());
        activeSlots.set(27);

        assertTrue(handler.isRecoverySlot(80));
        assertFalse(handler.isItemValid(80, new ItemStack(Items.IRON_INGOT)));
        assertEquals(4, handler.insertItem(80, new ItemStack(Items.IRON_INGOT, 4), false).getCount());
        assertEquals(12, handler.extractItem(80, 64, false).getCount());
        assertTrue(handler.getStackInSlot(80).isEmpty());
        assertTrue(changes.get() >= 2);
    }

    @Test
    void activeSlotCountIsClampedToSupportedBounds() {
        AtomicInteger activeSlots = new AtomicInteger(0);
        RecoverableItemStackHandler handler = new RecoverableItemStackHandler(
                activeSlots::get, stack -> true, () -> {});
        assertEquals(27, handler.getActiveSlots());
        activeSlots.set(10_000);
        assertEquals(RecoverableItemStackHandler.MAX_SLOTS, handler.getActiveSlots());
    }
}
