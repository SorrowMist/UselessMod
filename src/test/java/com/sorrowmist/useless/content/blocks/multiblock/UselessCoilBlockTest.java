package com.sorrowmist.useless.content.blocks.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UselessCoilBlockTest {
    @Test
    void usefulTierHasItsOwnRegistryName() {
        assertEquals(10, UselessCoilBlock.MAX_TIER);
        assertEquals("useless_coil_tier_9", UselessCoilBlock.registryName(9));
        assertEquals("useful_coil", UselessCoilBlock.registryName(UselessCoilBlock.USEFUL_TIER));
    }

    @Test
    void rejectsTiersOutsideTheSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> UselessCoilBlock.registryName(0));
        assertThrows(IllegalArgumentException.class, () -> UselessCoilBlock.registryName(11));
    }
}
