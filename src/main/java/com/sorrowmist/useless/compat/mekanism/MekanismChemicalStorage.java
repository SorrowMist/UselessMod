package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/** A long-sized three-tank storage backed by Mekanism ChemicalStacks. */
public final class MekanismChemicalStorage implements FurnaceChemicalStorage {
    private final ChemicalStack[] stacks;
    private final Runnable onChanged;
    private long capacity;

    public MekanismChemicalStorage(int tankCount, long capacity, Runnable onChanged) {
        this.stacks = new ChemicalStack[Math.max(0, tankCount)];
        for (int i = 0; i < this.stacks.length; i++) {
            this.stacks[i] = ChemicalStack.EMPTY;
        }
        this.capacity = Math.max(0L, capacity);
        this.onChanged = onChanged == null ? () -> { } : onChanged;
    }

    @Override
    public int size() {
        return this.stacks.length;
    }

    @Override
    public long capacity(int slot) {
        return validSlot(slot) ? this.capacity : 0L;
    }

    @Override
    public ChemicalStackView getStackInSlot(int slot) {
        return validSlot(slot) ? new MekanismChemicalStackView(this.stacks[slot]) : ChemicalStackView.EMPTY;
    }

    public ChemicalStack getNativeStack(int slot) {
        return validSlot(slot) ? this.stacks[slot].copy() : ChemicalStack.EMPTY;
    }

    @Override
    public ChemicalStackView insertChemical(int slot, ChemicalStackView view, boolean simulate) {
        if (!validSlot(slot) || view == null || view.isEmpty() || !(view instanceof MekanismChemicalStackView mekView)) {
            return view == null ? ChemicalStackView.EMPTY : view;
        }

        ChemicalStack incoming = mekView.stack();
        ChemicalStack current = this.stacks[slot];
        if (!current.isEmpty() && !ChemicalStack.isSameChemical(current, incoming)) {
            return view;
        }

        long existingAmount = current.isEmpty() ? 0L : current.getAmount();
        long available = this.capacity > existingAmount ? this.capacity - existingAmount : 0L;
        long accepted = Math.min(Math.max(0L, incoming.getAmount()), available);
        if (accepted <= 0L) return view;

        if (!simulate) {
            if (current.isEmpty()) {
                this.stacks[slot] = incoming.copyWithAmount(accepted);
            } else {
                this.stacks[slot] = current.copyWithAmount(saturatingAdd(current.getAmount(), accepted));
            }
            this.onChanged.run();
        }
        return new MekanismChemicalStackView(incoming.copyWithAmount(incoming.getAmount() - accepted));
    }

    @Override
    public ChemicalStackView extractChemical(int slot, long amount, boolean simulate) {
        if (!validSlot(slot) || amount <= 0L || this.stacks[slot].isEmpty()) {
            return ChemicalStackView.EMPTY;
        }
        ChemicalStack current = this.stacks[slot];
        long extracted = Math.min(amount, current.getAmount());
        ChemicalStack result = current.copyWithAmount(extracted);
        if (!simulate) {
            long remaining = current.getAmount() - extracted;
            this.stacks[slot] = remaining <= 0L ? ChemicalStack.EMPTY : current.copyWithAmount(remaining);
            this.onChanged.run();
        }
        return new MekanismChemicalStackView(result);
    }

    @Override
    public void setStackInSlot(int slot, ChemicalStackView view) {
        if (!validSlot(slot)) return;
        ChemicalStack replacement = view instanceof MekanismChemicalStackView mekView
                ? mekView.stack().copy() : ChemicalStack.EMPTY;
        if (replacement.getAmount() > this.capacity) {
            replacement = replacement.copyWithAmount(this.capacity);
        }
        this.stacks[slot] = replacement.isEmpty() ? ChemicalStack.EMPTY : replacement;
        this.onChanged.run();
    }

    @Override
    public void setCapacity(long capacity) {
        long newCapacity = Math.max(0L, capacity);
        if (this.capacity == newCapacity) return;
        this.capacity = newCapacity;
        boolean changed = false;
        for (int i = 0; i < this.stacks.length; i++) {
            ChemicalStack stack = this.stacks[i];
            if (stack.isEmpty() || stack.getAmount() <= newCapacity) continue;
            this.stacks[i] = newCapacity <= 0L
                    ? ChemicalStack.EMPTY
                    : stack.copyWithAmount(newCapacity);
            changed = true;
        }
        if (changed) this.onChanged.run();
    }

    @Override
    public void save(CompoundTag tag, String prefix, HolderLookup.Provider registries) {
        for (int i = 0; i < this.stacks.length; i++) {
            ChemicalStack stack = this.stacks[i];
            if (!stack.isEmpty()) {
                tag.put(prefix + i, stack.save(registries));
            }
        }
    }

    @Override
    public void load(CompoundTag tag, String prefix, HolderLookup.Provider registries) {
        for (int i = 0; i < this.stacks.length; i++) {
            String name = prefix + i;
            this.stacks[i] = tag.contains(name) && tag.get(name) instanceof CompoundTag compound
                    ? ChemicalStack.parseOptional(registries, compound) : ChemicalStack.EMPTY;
            if (this.stacks[i].getAmount() > this.capacity) {
                this.stacks[i] = this.stacks[i].copyWithAmount(this.capacity);
            }
        }
        this.onChanged.run();
    }

    private boolean validSlot(int slot) {
        return slot >= 0 && slot < this.stacks.length;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
