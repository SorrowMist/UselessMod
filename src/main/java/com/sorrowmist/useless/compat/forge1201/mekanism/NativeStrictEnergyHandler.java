package com.sorrowmist.useless.compat.forge1201.mekanism;

import com.sorrowmist.useless.energy.IEnergyManager;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.math.FloatingLong;
import java.util.Objects;

/** Mekanism 10.4 uses fractional unsigned joules; the furnace stores signed long FE.
 * Never round a fractional FE up or report more native energy than was actually transferred. */
public class NativeStrictEnergyHandler implements IStrictEnergyHandler {
    private final IEnergyManager energy;
    private final IEnergyConversion conversion;
    public NativeStrictEnergyHandler(IEnergyManager energy, IEnergyConversion conversion) {
        this.energy=Objects.requireNonNull(energy); this.conversion=Objects.requireNonNull(conversion);
    }
    @Override public int getEnergyContainerCount() { return 1; }
    @Override public FloatingLong getEnergy(int container) {
        return container==0 ? conversion.convertFrom(energy.getEnergyStoredLong()) : FloatingLong.ZERO;
    }
    @Override public void setEnergy(int container, FloatingLong amount) {
        if(container==0) energy.setEnergyStored(conversion.convertToAsLong(amount));
    }
    @Override public FloatingLong getMaxEnergy(int container) {
        return container==0 ? conversion.convertFrom(energy.getMaxEnergyStoredLong()) : FloatingLong.ZERO;
    }
    @Override public FloatingLong getNeededEnergy(int container) {
        return container==0 ? conversion.convertFrom(Math.max(0L,energy.getMaxEnergyStoredLong()-energy.getEnergyStoredLong())) : FloatingLong.ZERO;
    }
    @Override public FloatingLong insertEnergy(int container,FloatingLong amount,Action action) {
        return container==0 ? insertEnergy(amount,action) : amount;
    }
    @Override public FloatingLong insertEnergy(FloatingLong amount,Action action) {
        if(amount.isZero() || !energy.canReceive() || !conversion.isEnabled()) return amount;
        long candidate=Math.max(0L,conversion.convertToAsLong(amount));
        long accepted=energy.receiveEnergy(candidate,true);
        long fits=bounded(accepted,amount);
        if(fits<=0) return amount;
        long inserted=energy.receiveEnergy(fits,action.simulate());
        return amount.subtract(conversion.convertFrom(inserted));
    }
    @Override public FloatingLong extractEnergy(int container,FloatingLong amount,Action action) {
        return container==0 ? extractEnergy(amount,action) : FloatingLong.ZERO;
    }
    @Override public FloatingLong extractEnergy(FloatingLong amount,Action action) {
        if(amount.isZero() || !energy.canExtract() || !conversion.isEnabled()) return FloatingLong.ZERO;
        long candidate=Math.max(0L,conversion.convertToAsLong(amount));
        long available=energy.extractEnergy(candidate,true);
        long fits=bounded(available,amount);
        if(fits<=0) return FloatingLong.ZERO;
        return conversion.convertFrom(energy.extractEnergy(fits,action.simulate()));
    }
    private long bounded(long candidate,FloatingLong limit) {
        if(candidate<=0) return 0;
        FloatingLong converted=conversion.convertFrom(candidate);
        if(converted.isZero()) return 0; // Invalid conversion must not create free energy.
        if(converted.smallerOrEqual(limit)) return candidate;
        // Configurable conversion precision may not round-trip exactly. Find a conservative bound.
        long low=0,high=candidate;
        while(low<high) {
            long middle=low+(high-low)/2+1;
            if(conversion.convertFrom(middle).smallerOrEqual(limit)) low=middle;
            else high=middle-1;
        }
        return low;
    }
}
