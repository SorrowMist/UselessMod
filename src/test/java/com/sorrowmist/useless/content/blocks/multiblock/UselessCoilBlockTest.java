package com.sorrowmist.useless.content.blocks.multiblock;

import com.sorrowmist.useless.init.ModBlocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UselessCoilBlockTest {
    @Test
    void everyTierDefaultsToInactiveAndSupportsTheActiveState() {
        for (int tier = UselessCoilBlock.MIN_TIER;
             tier <= UselessCoilBlock.MAX_TIER; tier++) {
            UselessCoilBlock coil = ModBlocks.USELESS_COILS.get(tier).get();
            assertFalse(coil.defaultBlockState().getValue(UselessCoilBlock.ACTIVE));
            assertTrue(coil.defaultBlockState()
                    .setValue(UselessCoilBlock.ACTIVE, true)
                    .getValue(UselessCoilBlock.ACTIVE));
        }
    }

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
