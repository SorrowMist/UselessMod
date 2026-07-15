package com.sorrowmist.useless.compat.fluxnetworks;

import com.sorrowmist.useless.energy.EnergyManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FluxNetworksEnergyCompatTest {
    @Test
    void transfersLongFluxEnergyDirectly() {
        EnergyManager energy = EnergyManager.builder()
                .capacity(Long.MAX_VALUE)
                .maxReceive(Long.MAX_VALUE)
                .maxExtract(Long.MAX_VALUE)
                .build();
        var storage = new FluxNetworksEnergyCompat.FurnaceFluxEnergyStorage(energy);

        assertEquals(5_000_000_000L, storage.receiveEnergyL(5_000_000_000L, false));
        assertEquals(5_000_000_000L, storage.getEnergyStoredL());
        assertEquals(Long.MAX_VALUE, storage.getMaxEnergyStoredL());
        assertEquals(4_000_000_000L, storage.extractEnergyL(4_000_000_000L, false));
    }
}
