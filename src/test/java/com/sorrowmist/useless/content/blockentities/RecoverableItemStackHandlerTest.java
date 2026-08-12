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

    @Test
    void customCapacitySupportsZeroActiveRecoveryStorage() {
        AtomicInteger activeSlots = new AtomicInteger(0);
        RecoverableItemStackHandler handler = new RecoverableItemStackHandler(
                30, 0, activeSlots::get, stack -> stack.is(Items.IRON_INGOT), () -> {});
        handler.setStackInSlot(29, new ItemStack(Items.IRON_INGOT, 7));

        assertEquals(30, handler.getSlots());
        assertEquals(0, handler.getActiveSlots());
        assertTrue(handler.isRecoverySlot(29));
        assertFalse(handler.isItemValid(0, new ItemStack(Items.IRON_INGOT)));
        assertEquals(7, handler.extractItem(29, 64, false).getCount());

        activeSlots.set(33);
        assertEquals(30, handler.getActiveSlots());
    }

    @Test
    void changeBatchNotifiesOnceForSeveralSlotWrites() {
        AtomicInteger changes = new AtomicInteger();
        RecoverableItemStackHandler handler = new RecoverableItemStackHandler(
                () -> 27, stack -> true, changes::incrementAndGet);

        handler.withChangeBatch(() -> {
            handler.setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
            handler.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        });

        assertEquals(1, changes.get());
    }

    @Test
    void insertRunsTheItemValidatorOnce() {
        AtomicInteger validations = new AtomicInteger();
        RecoverableItemStackHandler handler = new RecoverableItemStackHandler(
                () -> 27, stack -> {
                    validations.incrementAndGet();
                    return true;
                }, () -> { });

        handler.insertItem(0, new ItemStack(Items.IRON_INGOT), false);

        assertEquals(1, validations.get());
    }
}
