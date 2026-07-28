package com.sorrowmist.useless.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaledEnergyAmountTest {
    @Test
    void formatsLongValuesWithScaledSuffixesWithoutRoundingAboveTheValue() {
        assertEquals("0", ScaledEnergyAmount.format(0L));
        assertEquals("999", ScaledEnergyAmount.format(999L));
        assertEquals("1K", ScaledEnergyAmount.format(1_000L));
        assertEquals("3.27G", ScaledEnergyAmount.format(3_276_800_000L));
        assertEquals("9.22E", ScaledEnergyAmount.format(Long.MAX_VALUE));
    }

    @Test
    void parsesSuffixesAndClampsToFurnaceCapacity() {
        assertEquals(1_500_000_000L,
                ScaledEnergyAmount.parse("1.5G", Long.MAX_VALUE).orElseThrow());
        assertEquals(3_276_800_000L,
                ScaledEnergyAmount.parse("99E", 3_276_800_000L).orElseThrow());
        assertEquals(1_250_000L,
                ScaledEnergyAmount.parse("1.25m", Long.MAX_VALUE).orElseThrow());
    }

    @Test
    void inputFilterAllowsEditableScaledNumbersOnly() {
        assertTrue(ScaledEnergyAmount.isValidInput(""));
        assertTrue(ScaledEnergyAmount.isValidInput("1.25G"));
        assertTrue(ScaledEnergyAmount.isValidInput("."));
        assertFalse(ScaledEnergyAmount.isValidInput("G"));
        assertFalse(ScaledEnergyAmount.isValidInput("1G2"));
        assertFalse(ScaledEnergyAmount.isValidInput("-1"));
    }
}
