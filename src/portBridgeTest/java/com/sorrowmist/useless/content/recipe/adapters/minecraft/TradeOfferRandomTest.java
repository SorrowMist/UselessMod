package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class TradeOfferRandomTest {
    @Test void repeatedSamplesAreIdenticalOnBothSides() {
        RandomSource client = TradeOfferRandom.create();
        RandomSource server = TradeOfferRandom.create();
        for (int i = 0; i < 100; i++) {
            assertEquals(client.nextInt(50), server.nextInt(50));
            assertEquals(client.nextDouble(), server.nextDouble());
            assertEquals(client.nextGaussian(), server.nextGaussian());
        }
    }

    @Test void enchantmentSelectionTerminatesEvenWhenCandidatesRemainCompatible() {
        RandomSource random = TradeOfferRandom.create();
        // Apotheosis 7.4.8's level eventually reaches zero. A constant zero source
        // never exits this loop if another mod leaves an eligible enchantment.
        int level = 30;
        int selections = 0;
        while (random.nextInt(50) <= level) {
            assertTrue(++selections < 100, "Enchantment selection did not terminate");
            level /= 2;
        }
    }

    @Test void forkedAndPositionalStreamsAreNotConstantZeroSources() {
        RandomSource source = TradeOfferRandom.create();
        assertVaries(source);
        assertVaries(source.fork());
        assertVaries(source.forkPositional().fromHashOf("villager_trade"));
        assertVaries(source.forkPositional().at(1, 2, 3));
    }

    private static void assertVaries(RandomSource random) {
        assertTrue(IntStream.range(0, 100).map(i -> random.nextInt(50)).distinct().count() > 1);
    }
}
