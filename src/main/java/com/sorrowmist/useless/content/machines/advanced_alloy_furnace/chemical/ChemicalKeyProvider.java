package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

/** Converts optional chemical views to and from the AE key representation. */
public interface ChemicalKeyProvider {
    ChemicalKeyProvider NONE = new ChemicalKeyProvider() {
        @Override
        public @Nullable GenericStack toGenericStack(ChemicalStackView stack) {
            return null;
        }

        @Override
        public @Nullable ChemicalStackView fromGenericStack(GenericStack stack) {
            return null;
        }

        @Override
        public boolean isChemicalKey(AEKey key) {
            return false;
        }
    };

    @Nullable
    GenericStack toGenericStack(ChemicalStackView stack);

    @Nullable
    ChemicalStackView fromGenericStack(GenericStack stack);

    boolean isChemicalKey(@Nullable AEKey key);
}
