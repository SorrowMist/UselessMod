package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

/** Core view of a neighboring chemical capability used by automatic IO. */
public interface ChemicalHandlerView {
    ChemicalStackView insertChemical(ChemicalStackView stack, boolean simulate);

    ChemicalStackView extractChemical(long amount, boolean simulate);

    ChemicalStackView extractChemical(ChemicalStackView stack, long amount, boolean simulate);
}
