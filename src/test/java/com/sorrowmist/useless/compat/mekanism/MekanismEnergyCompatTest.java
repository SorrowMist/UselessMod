package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.energy.EnergyManager;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MekanismEnergyCompatTest {
    private static final IEnergyConversion ONE_TO_ONE = new IEnergyConversion() {
        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public double getConversion() {
            return 1D;
        }
    };

    @Test
    void transfersLongEnergyWithoutUsingTheIntForgeBridge() {
        EnergyManager energy = EnergyManager.builder()
                .capacity(Long.MAX_VALUE)
                .maxReceive(Long.MAX_VALUE)
                .maxExtract(Long.MAX_VALUE)
                .build();
        var handler = new MekanismEnergyCompat.FurnaceStrictEnergyHandler(energy, ONE_TO_ONE);

        assertEquals(0L, handler.insertEnergy(5_000_000_000L, Action.EXECUTE));
        assertEquals(5_000_000_000L, handler.getEnergy(0));
        assertEquals(4_000_000_000L, handler.extractEnergy(4_000_000_000L, Action.EXECUTE));
        assertEquals(1_000_000_000L, energy.getEnergyStoredLong());
        assertEquals(Long.MAX_VALUE, handler.getMaxEnergy(0));
    }

    @Test
    void appliesMekanismsConfiguredJouleConversionToLongTransfers() {
        EnergyManager energy = EnergyManager.builder()
                .capacity(Long.MAX_VALUE)
                .maxReceive(Long.MAX_VALUE)
                .maxExtract(0L)
                .build();
        var handler = new MekanismEnergyCompat.FurnaceStrictEnergyHandler(energy);
        long joules = EnergyUnit.FORGE_ENERGY.convertFrom(5_000_000_000L);
        long expectedForgeEnergy = EnergyUnit.FORGE_ENERGY.convertTo(joules);

        assertEquals(0L, handler.insertEnergy(joules, Action.EXECUTE));
        assertEquals(expectedForgeEnergy, energy.getEnergyStoredLong());
        assertEquals(joules, handler.getEnergy(0));
    }
}
