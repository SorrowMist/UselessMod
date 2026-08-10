package com.sorrowmist.useless.content.blockentities;

import net.minecraft.world.item.ItemStack;

import java.util.function.IntSupplier;
import java.util.function.Predicate;

/** Sample storage for the ore generator. Every active slot holds one template item. */
public final class OreGeneratorSampleHandler extends RecoverableItemStackHandler {
    public OreGeneratorSampleHandler(IntSupplier activeSlots, Predicate<ItemStack> validator,
                                     Runnable changeListener) {
        super(MAX_SLOTS, 0, activeSlots, validator, changeListener);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!stack.isEmpty() && isValidItem(stack)) {
            ItemStack sample = stack.copy();
            sample.setCount(1);
            super.setStackInSlot(slot, sample);
        } else {
            super.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }
}
