package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

/** Process-wide optional chemical integration selected during capability setup. */
public final class ChemicalCompatProviders {
    private static volatile ChemicalCompatProvider provider = ChemicalCompatProvider.NONE;

    private ChemicalCompatProviders() {
    }

    public static ChemicalCompatProvider get() {
        return provider;
    }

    public static void register(ChemicalCompatProvider provider) {
        ChemicalCompatProviders.provider = provider == null ? ChemicalCompatProvider.NONE : provider;
    }
}
