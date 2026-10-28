package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import net.minecraft.util.RandomSource;

/** Reproducible trade samples without violating the random source's distribution. */
final class TradeOfferRandom {
    private static final long SEED = 0x5553454C455353L;

    private TradeOfferRandom() {
    }

    static RandomSource create() {
        // A fresh stream keeps client/server samples independent of enumeration order.
        // In particular, nextInt(50) must be able to terminate enchantment selection.
        return RandomSource.create(SEED);
    }
}
