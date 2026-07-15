package com.sorrowmist.useless.energy;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Long-backed energy storage with an int Forge Energy compatibility surface. */
public interface IEnergyManager extends IEnergyStorage {

    long receiveEnergy(long maxReceive, boolean simulate);

    long extractEnergy(long maxExtract, boolean simulate);

    long getEnergyStoredLong();

    long getMaxEnergyStoredLong();

    long getMaxReceiveLong();

    long getMaxExtractLong();

    void setEnergyStored(long energy);

    void setMaxEnergyStored(long capacity);

    void setMaxReceive(long maxReceive);

    void setMaxExtract(long maxExtract);

    void modifyEnergy(long delta);

    /**
     * Changes stored energy without applying transfer-rate limits.
     *
     * @return the absolute amount that was actually added or removed
     */
    long modifyEnergyStored(long delta);

    boolean canWork(long energyRequired);

    boolean tryConsumeEnergy(long amount);

    default double getEnergyPercentage() {
        long capacity = this.getMaxEnergyStoredLong();
        return capacity <= 0L ? 0.0D : (double) this.getEnergyStoredLong() / capacity;
    }

    CompoundTag serializeNBT();

    void deserializeNBT(CompoundTag tag);

    void setChangeListener(Runnable listener);

    void removeChangeListener();
}
