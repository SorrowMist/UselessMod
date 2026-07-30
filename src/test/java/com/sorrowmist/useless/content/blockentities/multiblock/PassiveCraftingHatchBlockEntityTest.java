package com.sorrowmist.useless.content.blockentities.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassiveCraftingHatchBlockEntityTest {
    @Test
    void defaultCapacityUnlocksWithCeilingDivision() {
        assertEquals(0, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(0, 30));
        for (int tier = 1; tier <= 10; tier++) {
            assertEquals((30 * tier + 9) / 10,
                    PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(tier, 30));
        }
        assertEquals(30, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(100, 30));
    }

    @Test
    void maximumCapacityUnlocksAllFiveHundredFortySlotsAtTierTen() {
        assertEquals(54, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(1, 540));
        assertEquals(270, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(5, 540));
        assertEquals(540, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(10, 540));
        assertEquals(540, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(100, 540));
    }

    @Test
    void configuredCapacityAndTierAreClampedToSupportedBounds() {
        assertEquals(1, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(1, 1));
        assertEquals(0, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(-1, 540));
        assertEquals(540, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(10, 999));
    }
}
