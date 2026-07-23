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
    private final Predicate<ItemStack> validator;
    private final Runnable changeListener;

    public RecoverableItemStackHandler(IntSupplier activeSlots, Predicate<ItemStack> validator, Runnable changeListener) {
        super(MAX_SLOTS);
        this.activeSlots = Objects.requireNonNull(activeSlots, "activeSlots");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public int getActiveSlots() {
        return Math.max(27, Math.min(MAX_SLOTS, activeSlots.getAsInt()));
    }

    public boolean isRecoverySlot(int slot) {
        return slot >= getActiveSlots() && slot < getSlots();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0 && slot < getActiveSlots() && validator.test(stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    protected void onContentsChanged(int slot) {
        changeListener.run();
    }
}
