package com.sorrowmist.useless.utils;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 能量存储包装类 - 用于解决 NeoForge 1.21 中 EnergyStorage 反序列化限制
 * 该类提供一个兼容的 IEnergyStorage 实现，可以安全地进行序列化/反序列化
 */
public class EnergyStorageWrapper implements IEnergyStorage {
    private int energy;
    private final int capacity;
    private final int maxReceive;
    private final int maxExtract;
    private final EnergyChangeListener energyChangeListener;

    public interface EnergyChangeListener {
        void onEnergyChange();
    }

    public EnergyStorageWrapper(int capacity) {
        this(capacity, capacity, capacity, 0, null);
    }

    public EnergyStorageWrapper(int capacity, int maxTransfer) {
        this(capacity, maxTransfer, maxTransfer, 0, null);
    }

    public EnergyStorageWrapper(int capacity, int maxReceive, int maxExtract) {
        this(capacity, maxReceive, maxExtract, 0, null);
    }

    public EnergyStorageWrapper(int capacity, int maxReceive, int maxExtract, int initialEnergy) {
        this(capacity, maxReceive, maxExtract, initialEnergy, null);
    }

    public EnergyStorageWrapper(int capacity, int maxReceive, int maxExtract, int initialEnergy, EnergyChangeListener listener) {
        this.capacity = Math.max(0, capacity);
        this.maxReceive = Math.max(0, Math.min(capacity, maxReceive));
        this.maxExtract = Math.max(0, Math.min(capacity, maxExtract));
        this.energy = Math.max(0, Math.min(initialEnergy, capacity));
        this.energyChangeListener = listener;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!canReceive()) {
            return 0;
        }

        int energyReceived = Math.min(capacity - energy, Math.min(this.maxReceive, maxReceive));
        if (!simulate && energyReceived > 0) {
            energy += energyReceived;
            notifyEnergyChange();
        }

        return energyReceived;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!canExtract()) {
            return 0;
        }

        int energyExtracted = Math.min(energy, Math.min(this.maxExtract, maxExtract));
        if (!simulate && energyExtracted > 0) {
            energy -= energyExtracted;
            notifyEnergyChange();
        }

        return energyExtracted;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return this.maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return this.maxReceive > 0;
    }

    public void setEnergy(int energy) {
        int oldEnergy = this.energy;
        this.energy = Math.max(0, Math.min(energy, capacity));
        if (oldEnergy != this.energy) {
            notifyEnergyChange();
        }
    }

    public void modifyEnergy(int delta) {
        setEnergy(this.energy + delta);
    }

    private void notifyEnergyChange() {
        if (energyChangeListener != null) {
            energyChangeListener.onEnergyChange();
        }
    }
}