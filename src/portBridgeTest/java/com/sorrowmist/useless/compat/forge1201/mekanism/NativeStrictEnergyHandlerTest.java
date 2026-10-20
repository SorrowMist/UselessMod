package com.sorrowmist.useless.compat.forge1201.mekanism;

import com.sorrowmist.useless.energy.EnergyManager;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.math.FloatingLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeStrictEnergyHandlerTest {
    private static FloatingLong joules(String value) { return FloatingLong.parseFloatingLong(value); }
    private static IEnergyConversion converter(String factor,boolean enabled) {
        FloatingLong multiplier=joules(factor);
        return new IEnergyConversion() {
            public boolean isEnabled() {return enabled;}
            public String getTranslationKey() {return "test.energy";}
            public FloatingLong convertFrom(FloatingLong amount) {return amount.multiply(multiplier);}
            public FloatingLong convertInPlaceFrom(FloatingLong amount) {return amount.timesEqual(multiplier);}
            public FloatingLong convertTo(FloatingLong amount) {return amount.divide(multiplier);}
            public FloatingLong convertInPlaceTo(FloatingLong amount) {return amount.divideEquals(multiplier);}
        };
    }
    @Test void fractionalJoulesNeverRoundUpToFreeFeOrLoseTheUnacceptedRemainder() {
        var energy=new EnergyManager(1000);
        var handler=new NativeStrictEnergyHandler(energy,converter("2.5",true));
        var amount=joules("4");
        assertEquals(joules("1.5"),handler.insertEnergy(amount,Action.EXECUTE));
        assertEquals(1,energy.getEnergyStoredLong()); assertEquals(joules("4"),amount);
        assertEquals(joules("2.5"),handler.extractEnergy(amount,Action.EXECUTE));
        assertEquals(0,energy.getEnergyStoredLong());
        assertEquals(joules("2.4999"),handler.insertEnergy(joules("2.4999"),Action.EXECUTE));
        assertEquals(0,energy.getEnergyStoredLong());
    }
    @Test void longTransfersAreNotTruncatedToAnIntAndSimulationDoesNotMutateStorage() {
        long amount=5_000_000_000_000L;
        var energy=new EnergyManager(amount*2);
        var handler=new NativeStrictEnergyHandler(energy,converter("2.5",true));
        var nativeAmount=FloatingLong.create(amount).multiply(joules("2.5"));
        assertEquals(FloatingLong.ZERO,handler.insertEnergy(nativeAmount,Action.SIMULATE));
        assertEquals(0,energy.getEnergyStoredLong());
        assertEquals(FloatingLong.ZERO,handler.insertEnergy(nativeAmount,Action.EXECUTE));
        assertEquals(amount,energy.getEnergyStoredLong());
        assertEquals(nativeAmount,handler.extractEnergy(nativeAmount,Action.SIMULATE));
        assertEquals(amount,energy.getEnergyStoredLong());
        assertEquals(nativeAmount,handler.extractEnergy(nativeAmount,Action.EXECUTE));
        assertEquals(0,energy.getEnergyStoredLong());
    }
    @Test void capacityTransferLimitsAndInvalidContainerIndexesAreRespected() {
        var energy=new EnergyManager(10,3);
        var handler=new NativeStrictEnergyHandler(energy,converter("2.5",true));
        assertEquals(joules("17.5"),handler.insertEnergy(joules("25"),Action.EXECUTE));
        assertEquals(3,energy.getEnergyStoredLong());
        assertEquals(joules("25"),handler.insertEnergy(1,joules("25"),Action.EXECUTE));
        assertEquals(FloatingLong.ZERO,handler.extractEnergy(1,joules("25"),Action.EXECUTE));
        handler.setEnergy(0,joules("100")); assertEquals(10,energy.getEnergyStoredLong());
        assertEquals(joules("25"),handler.getMaxEnergy(0));
        assertEquals(FloatingLong.ZERO,handler.getNeededEnergy(0));
        assertEquals(joules("7.5"),handler.extractEnergy(joules("100"),Action.EXECUTE));
        assertEquals(7,energy.getEnergyStoredLong());
    }
    @Test void disabledConversionCannotBypassMekanismConfiguration() {
        var energy=new EnergyManager(100); energy.setEnergyStored(50);
        var handler=new NativeStrictEnergyHandler(energy,converter("2.5",false));
        assertEquals(joules("25"),handler.insertEnergy(joules("25"),Action.EXECUTE));
        assertEquals(FloatingLong.ZERO,handler.extractEnergy(joules("25"),Action.EXECUTE));
        assertEquals(50,energy.getEnergyStoredLong());
    }
    @Test void awkwardConfigurableRatiosConserveEnergyAcrossRepeatedInsertExtractCycles() {
        for(String ratio:new String[]{"0.3","1","2.5","3.1415","10000"}) {
            var energy=new EnergyManager(100_000_000);
            var handler=new NativeStrictEnergyHandler(energy,converter(ratio,true));
            for(int i=1;i<=50;i++) {
                var offered=FloatingLong.create(i).multiply(joules(ratio)).add(joules("0.0001"));
                var before=offered.copy();
                var remainder=handler.insertEnergy(offered,Action.EXECUTE);
                var extracted=handler.extractEnergy(offered,Action.EXECUTE);
                assertTrue(extracted.smallerOrEqual(offered));
                assertEquals(offered.subtract(remainder),extracted,ratio+" round "+i);
                assertEquals(before,offered);
                assertEquals(0,energy.getEnergyStoredLong());
            }
        }
    }
}
