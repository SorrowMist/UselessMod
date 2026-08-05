package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * Core chemical storage contract used by the alloy furnace.
 *
 * <p>Amounts are always {@code long}.  Implementations may be backed by a
 * Mekanism handler, while the furnace and its recipe code only see this
 * contract.</p>
 */
public interface FurnaceChemicalStorage {
    FurnaceChemicalStorage DISABLED = new FurnaceChemicalStorage() {
        @Override
        public int size() {
            return 0;
        }

        @Override
        public long capacity(int slot) {
            return 0L;
        }

        @Override
        public ChemicalStackView getStackInSlot(int slot) {
            return ChemicalStackView.EMPTY;
        }

        @Override
        public ChemicalStackView insertChemical(int slot, ChemicalStackView stack, boolean simulate) {
            return stack == null ? ChemicalStackView.EMPTY : stack;
        }

        @Override
        public ChemicalStackView extractChemical(int slot, long amount, boolean simulate) {
            return ChemicalStackView.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ChemicalStackView stack) {
        }

        @Override
        public boolean isAvailable() {
            return false;
        }
    };

    int size();

    long capacity(int slot);

    ChemicalStackView getStackInSlot(int slot);

    ChemicalStackView insertChemical(int slot, ChemicalStackView stack, boolean simulate);

    ChemicalStackView extractChemical(int slot, long amount, boolean simulate);

    void setStackInSlot(int slot, ChemicalStackView stack);

    default boolean isAvailable() {
        return size() > 0;
    }

    default void setCapacity(long capacity) {
    }

    default ChemicalStackView insertChemical(ChemicalStackView stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || !isAvailable()) {
            return stack == null ? ChemicalStackView.EMPTY : stack;
        }

        ChemicalStackView remaining = stack;
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (int slot = 0; slot < size() && !remaining.isEmpty(); slot++) {
                ChemicalStackView current = getStackInSlot(slot);
                if ((pass == 0 && !current.isSameType(remaining))
                        || (pass == 1 && !current.isEmpty())) {
                    continue;
                }
                remaining = insertChemical(slot, remaining, simulate);
            }
        }
        return remaining;
    }

    default ChemicalStackView extractChemical(ChemicalStackView type, long amount, boolean simulate) {
        if (type == null || type.isEmpty() || amount <= 0L) {
            return ChemicalStackView.EMPTY;
        }
        long remaining = amount;
        ChemicalStackView result = ChemicalStackView.EMPTY;
        for (int slot = 0; slot < size() && remaining > 0L; slot++) {
            ChemicalStackView current = getStackInSlot(slot);
            if (!current.isSameType(type)) continue;
            ChemicalStackView extracted = extractChemical(slot, remaining, simulate);
            if (extracted.isEmpty()) continue;
            if (result.isEmpty()) {
                result = extracted;
            } else if (result.isSameType(extracted)) {
                result = result.copyWithAmount(saturatingAdd(result.amount(), extracted.amount()));
            }
            remaining -= extracted.amount();
        }
        return result;
    }

    /** Persist the storage without exposing the optional stack type to core code. */
    default void save(CompoundTag tag, String prefix, HolderLookup.Provider registries) {
    }

    /** Restore the storage without exposing the optional stack type to core code. */
    default void load(CompoundTag tag, String prefix, HolderLookup.Provider registries) {
    }

    static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
