package com.sorrowmist.useless.content.blockentities.multiblock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiblockAlloyFurnaceEnergyLimitTest {
    @Test
    void automaticDrawStopsAtConfiguredLimit() {
        assertEquals(100L, MultiblockAlloyFurnaceCoreBlockEntity.calculateAutomaticEnergyRequest(
                900L, 10_000L, 500L, 1_000L));
        assertEquals(0L, MultiblockAlloyFurnaceCoreBlockEntity.calculateAutomaticEnergyRequest(
                1_000L, 10_000L, 500L, 1_000L));
        assertEquals(0L, MultiblockAlloyFurnaceCoreBlockEntity.calculateAutomaticEnergyRequest(
                2_000L, 10_000L, 500L, 1_000L));
    }

    @Test
    void zeroDisablesDrawAndPhysicalLimitsStillApply() {
        assertEquals(0L, MultiblockAlloyFurnaceCoreBlockEntity.calculateAutomaticEnergyRequest(
                0L, 10_000L, 500L, 0L));
        assertEquals(50L, MultiblockAlloyFurnaceCoreBlockEntity.calculateAutomaticEnergyRequest(
                950L, 1_000L, 500L, Long.MAX_VALUE));
        assertEquals(7L, MultiblockAlloyFurnaceCoreBlockEntity.calculateAutomaticEnergyRequest(
                Long.MAX_VALUE - 7L, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void configuredLimitCannotExceedPhysicalCapacity() {
        assertEquals(1_000L,
                MultiblockAlloyFurnaceCoreBlockEntity.clampAutomaticEnergyLimit(2_000L, 1_000L));
        assertEquals(750L,
                MultiblockAlloyFurnaceCoreBlockEntity.clampAutomaticEnergyLimit(750L, 1_000L));
        assertEquals(0L,
                MultiblockAlloyFurnaceCoreBlockEntity.clampAutomaticEnergyLimit(-1L, 1_000L));
    }
}
