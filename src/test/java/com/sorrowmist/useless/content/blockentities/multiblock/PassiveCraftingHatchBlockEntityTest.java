package com.sorrowmist.useless.content.blockentities.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PassiveCraftingHatchBlockEntityTest {
    @Test
    void activeSlotsIncreaseByThreeForEveryCoilTier() {
        assertEquals(0, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(0));
        for (int tier = 1; tier <= 10; tier++) {
            assertEquals(tier * 3,
                    PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(tier));
        }
        assertEquals(30, PassiveCraftingHatchBlockEntity.activeSlotsForCoilTier(100));
    }
}
