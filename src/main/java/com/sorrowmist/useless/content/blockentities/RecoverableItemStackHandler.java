package com.sorrowmist.useless.content.blockentities;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Fixed backing storage whose configured overflow becomes withdraw-only recovery storage. */
public class RecoverableItemStackHandler extends ItemStackHandler {
    public static final int MAX_SLOTS = 540;

    private final IntSupplier activeSlots;
    private final int minimumActiveSlots;
    private final Predicate<ItemStack> validator;
    private final Runnable changeListener;
    private int changeBatchDepth;
    private boolean changePending;

    public RecoverableItemStackHandler(IntSupplier activeSlots, Predicate<ItemStack> validator, Runnable changeListener) {
        this(MAX_SLOTS, 27, activeSlots, validator, changeListener);
    }

    public RecoverableItemStackHandler(int capacity, int minimumActiveSlots, IntSupplier activeSlots,
                                       Predicate<ItemStack> validator, Runnable changeListener) {
        super(validateCapacity(capacity));
        this.activeSlots = Objects.requireNonNull(activeSlots, "activeSlots");
        this.minimumActiveSlots = Math.max(0, Math.min(capacity, minimumActiveSlots));
        this.validator = Objects.requireNonNull(validator, "validator");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public int getActiveSlots() {
        return Math.max(minimumActiveSlots, Math.min(getSlots(), activeSlots.getAsInt()));
    }

    public boolean isRecoverySlot(int slot) {
        return slot >= getActiveSlots() && slot < getSlots();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < getActiveSlots() && validator.test(stack);
    }

    protected final boolean isValidItem(ItemStack stack) {
        return validator.test(stack);
    }

    /** Groups several backing-slot writes into one inventory change callback. */
    public void withChangeBatch(Runnable action) {
        changeBatchDepth++;
        try {
            action.run();
        } finally {
            changeBatchDepth--;
            if (changeBatchDepth == 0 && changePending) {
                changePending = false;
                changeListener.run();
            }
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (changeBatchDepth > 0) {
            changePending = true;
        } else {
            changeListener.run();
        }
    }

    private static int validateCapacity(int capacity) {
        if (capacity <= 0 || capacity > MAX_SLOTS) {
            throw new IllegalArgumentException("Capacity must be between 1 and " + MAX_SLOTS);
        }
        return capacity;
    }
}
