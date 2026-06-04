package com.sorrowmist.useless.energy;

import net.minecraft.nbt.CompoundTag;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 能源管理器实现类 - 提供完整的能源管理功能
 * 使用 AtomicInteger 实现无锁线程安全
 */
public class EnergyManager implements IEnergyManager {

    private final AtomicInteger energy = new AtomicInteger(0);
    private int capacity;
    private int maxReceive;
    private int maxExtract;
    private Runnable changeListener;

    public EnergyManager(int capacity) {
        this(capacity, capacity, capacity);
    }

    public EnergyManager(int capacity, int maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    private EnergyManager(int capacity, int maxReceive, int maxExtract) {
        this(capacity, maxReceive, maxExtract, 0);
    }

    private EnergyManager(int capacity, int maxReceive, int maxExtract, int initialEnergy) {
        this.capacity = Math.max(0, capacity);
        this.maxReceive = Math.max(0, Math.min(capacity, maxReceive));
        this.maxExtract = Math.max(0, Math.min(capacity, maxExtract));
        this.energy.set(Math.max(0, Math.min(initialEnergy, this.capacity)));
    }

    /**
     * 创建能源管理器的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (!this.canReceive()) {
            return 0;
        }

        int currentEnergy = this.energy.get();
        int energyReceived = Math.min(this.capacity - currentEnergy, Math.min(this.maxReceive, maxReceive));
        if (!simulate && energyReceived > 0) {
            this.energy.addAndGet(energyReceived);
            this.notifyChange();
        }

        return energyReceived;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!this.canExtract()) {
            return 0;
        }

        int currentEnergy = this.energy.get();
        int energyExtracted = Math.min(currentEnergy, Math.min(this.maxExtract, maxExtract));
        if (!simulate && energyExtracted > 0) {
            this.energy.addAndGet(-energyExtracted);
            this.notifyChange();
        }

        return energyExtracted;
    }

    @Override
    public int getEnergyStored() {
        return this.energy.get();
    }

    @Override
    public int getMaxEnergyStored() {
        return this.capacity;
    }

    @Override
    public boolean canExtract() {
        return this.maxExtract > 0;
    }

    @Override
    public boolean canReceive() {
        return this.maxReceive > 0;
    }

    @Override
    public void setMaxEnergyStored(int capacity) {
        this.capacity = Math.max(0, capacity);
        this.maxReceive = Math.min(this.maxReceive, capacity);
        this.maxExtract = Math.min(this.maxExtract, capacity);
        int currentEnergy = this.energy.get();
        if (currentEnergy > capacity) {
            this.energy.set(capacity);
            this.notifyChange();
        }
    }

    @Override
    public void setMaxReceive(int maxReceive) {
        this.maxReceive = Math.max(0, Math.min(this.capacity, maxReceive));
    }

    @Override
    public void setMaxExtract(int maxExtract) {
        this.maxExtract = Math.max(0, Math.min(this.capacity, maxExtract));
    }

    @Override
    public void modifyEnergy(int delta) {
        this.setEnergyStored(this.energy.get() + delta);
    }

    @Override
    public boolean canWork(int energyRequired) {
        return this.energy.get() >= energyRequired;
    }

    @Override
    public boolean tryConsumeEnergy(int amount) {
        if (amount <= 0) return false;

        // 使用 CAS 操作实现无锁线程安全
        while (true) {
            int current = this.energy.get();
            if (current < amount) {
                return false;
            }
            int next = Math.max(0, current - amount);
            if (this.energy.compareAndSet(current, next)) {
                // CAS 成功，在锁外调用 notifyChange 避免死锁
                this.notifyChange();
                return true;
            }
            // CAS 失败，重试
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Energy", this.energy.get());
        tag.putInt("Capacity", this.capacity);
        tag.putInt("MaxReceive", this.maxReceive);
        tag.putInt("MaxExtract", this.maxExtract);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.contains("Energy")) {
            this.energy.set(tag.getInt("Energy"));
        }
        if (tag.contains("Capacity")) {
            this.capacity = tag.getInt("Capacity");
        }
        if (tag.contains("MaxReceive")) {
            this.maxReceive = tag.getInt("MaxReceive");
        }
        if (tag.contains("MaxExtract")) {
            this.maxExtract = tag.getInt("MaxExtract");
        }
        this.notifyChange();
    }

    @Override
    public void setChangeListener(Runnable listener) {
        this.changeListener = listener;
    }

    @Override
    public void removeChangeListener() {
        this.changeListener = null;
    }

    @Override
    public void setEnergyStored(int energy) {
        int oldEnergy = this.energy.get();
        int newEnergy = Math.max(0, Math.min(energy, this.capacity));
        this.energy.set(newEnergy);
        if (oldEnergy != newEnergy) {
            this.notifyChange();
        }
    }

    private void notifyChange() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
    }

    public static class Builder {
        private int capacity = 10000;
        private int maxReceive = 1000;
        private int maxExtract = 1000;
        private int initialEnergy = 0;
        private Runnable changeListener;

        public Builder capacity(int capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder maxReceive(int maxReceive) {
            this.maxReceive = maxReceive;
            return this;
        }

        public Builder maxExtract(int maxExtract) {
            this.maxExtract = maxExtract;
            return this;
        }

        public Builder maxTransfer(int maxTransfer) {
            this.maxReceive = maxTransfer;
            this.maxExtract = maxTransfer;
            return this;
        }

        public Builder initialEnergy(int initialEnergy) {
            this.initialEnergy = initialEnergy;
            return this;
        }

        public Builder onChange(Runnable listener) {
            this.changeListener = listener;
            return this;
        }

        public EnergyManager build() {
            EnergyManager manager = new EnergyManager(this.capacity, this.maxReceive, this.maxExtract,
                                                      this.initialEnergy
            );
            if (this.changeListener != null) {
                manager.setChangeListener(this.changeListener);
            }
            return manager;
        }
    }
}
