package com.sorrowmist.useless.energy;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyManagerLongTest {

    @Test
    void storesTransfersAndPersistsBeyondIntegerRange() {
        EnergyManager energy = EnergyManager.builder()
                .capacity(5_000_000_000L)
                .maxReceive(3_000_000_000L)
                .maxExtract(0L)
                .build();

        assertEquals(3_000_000_000L, energy.receiveEnergy(3_000_000_000L, false));
        assertEquals(3_000_000_000L, energy.getEnergyStoredLong());
        assertEquals(Integer.MAX_VALUE, energy.getEnergyStored());
        assertTrue(energy.tryConsumeEnergy(2_500_000_000L));
        assertEquals(500_000_000L, energy.getEnergyStoredLong());

        CompoundTag saved = energy.serializeNBT();
        EnergyManager restored = new EnergyManager(1L);
        restored.deserializeNBT(saved);

        assertEquals(5_000_000_000L, restored.getMaxEnergyStoredLong());
        assertEquals(500_000_000L, restored.getEnergyStoredLong());
        assertEquals(3_000_000_000L, restored.getMaxReceiveLong());
    }

    @Test
    void tierNineCapacityUsesTheOriginalCurveWithoutIntClamping() {
        assertEquals(3_276_800_000L,
                com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity
                        .calculateEnergyCapacity(9));
        assertEquals(327_680_000L,
                com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity
                        .calculateEnergyReceive(9));
    }

    @Test
    void usefulIngotTierUsesTheLongLimit() {
        assertEquals(Long.MAX_VALUE,
                com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity
                        .calculateEnergyCapacity(10));
        assertEquals(Long.MAX_VALUE,
                com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity
                        .calculateEnergyReceive(10));
        assertEquals(Long.MAX_VALUE,
                com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceData.joinBits(
                        (int) Long.MAX_VALUE, (int) (Long.MAX_VALUE >>> 32)));

        EnergyManager tierTenEnergy = EnergyManager.builder()
                .capacity(Long.MAX_VALUE)
                .maxReceive(Long.MAX_VALUE)
                .maxExtract(0L)
                .build();
        assertEquals(Long.MAX_VALUE, tierTenEnergy.receiveEnergy(Long.MAX_VALUE, false));
        assertEquals(Long.MAX_VALUE, tierTenEnergy.getEnergyStoredLong());
    }
}
