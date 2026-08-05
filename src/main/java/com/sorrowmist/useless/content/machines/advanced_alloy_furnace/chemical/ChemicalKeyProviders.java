package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical;

/** Process-wide provider selected by the optional AppMek integration. */
public final class ChemicalKeyProviders {
    private static volatile ChemicalKeyProvider provider = ChemicalKeyProvider.NONE;

    private ChemicalKeyProviders() {
    }

    public static ChemicalKeyProvider get() {
        return provider;
    }

    public static void register(ChemicalKeyProvider provider) {
        ChemicalKeyProviders.provider = provider == null ? ChemicalKeyProvider.NONE : provider;
    }
}
