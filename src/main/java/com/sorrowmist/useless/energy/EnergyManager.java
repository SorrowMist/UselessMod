package com.sorrowmist.useless.energy;

import net.minecraft.nbt.CompoundTag;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe long-backed energy storage. */
public class EnergyManager implements IEnergyManager {

    private final AtomicLong energy = new AtomicLong(0L);
    private volatile long capacity;
    private volatile long maxReceive;
    private volatile long maxExtract;
    private Runnable changeListener;

    public EnergyManager(long capacity) {
        this(capacity, capacity, capacity);
    }

    public EnergyManager(long capacity, long maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    private EnergyManager(long capacity, long maxReceive, long maxExtract) {
        this(capacity, maxReceive, maxExtract, 0L);
    }

    private EnergyManager(long capacity, long maxReceive, long maxExtract, long initialEnergy) {
        this.capacity = Math.max(0L, capacity);
        this.maxReceive = clamp(maxReceive, 0L, this.capacity);
        this.maxExtract = clamp(maxExtract, 0L, this.capacity);
        this.energy.set(clamp(initialEnergy, 0L, this.capacity));
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public long receiveEnergy(long requested, boolean simulate) {
        if (!this.canReceive() || requested <= 0L) {
            return 0L;
        }

        while (true) {
            long current = this.energy.get();
            long accepted = Math.min(this.capacity - current, Math.min(this.maxReceive, requested));
            if (accepted <= 0L || simulate) {
                return Math.max(0L, accepted);
            }
            if (this.energy.compareAndSet(current, current + accepted)) {
                this.notifyChange();
                return accepted;
            }
        }
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return (int) Math.min(Integer.MAX_VALUE, this.receiveEnergy((long) maxReceive, simulate));
    }

    @Override
    public long extractEnergy(long requested, boolean simulate) {
        if (!this.canExtract() || requested <= 0L) {
            return 0L;
        }

        while (true) {
            long current = this.energy.get();
            long extracted = Math.min(current, Math.min(this.maxExtract, requested));
            if (extracted <= 0L || simulate) {
                return Math.max(0L, extracted);
            }
            if (this.energy.compareAndSet(current, current - extracted)) {
                this.notifyChange();
                return extracted;
            }
        }
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return (int) Math.min(Integer.MAX_VALUE, this.extractEnergy((long) maxExtract, simulate));
    }

    @Override
    public int getEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, this.getEnergyStoredLong());
    }

    @Override
    public long getEnergyStoredLong() {
        return this.energy.get();
    }

    @Override
    public int getMaxEnergyStored() {
        return (int) Math.min(Integer.MAX_VALUE, this.getMaxEnergyStoredLong());
    }

    @Override
    public long getMaxEnergyStoredLong() {
        return this.capacity;
    }

    @Override
    public long getMaxReceiveLong() {
        return this.maxReceive;
    }

    @Override
    public long getMaxExtractLong() {
        return this.maxExtract;
    }

    @Override
    public boolean canExtract() {
        return this.maxExtract > 0L;
    }

    @Override
    public boolean canReceive() {
        return this.maxReceive > 0L;
    }

    @Override
    public void setMaxEnergyStored(long capacity) {
        long previousCapacity = this.capacity;
        this.capacity = Math.max(0L, capacity);
        this.maxReceive = Math.min(this.maxReceive, this.capacity);
        this.maxExtract = Math.min(this.maxExtract, this.capacity);
        this.setEnergyStored(this.energy.get());
        if (previousCapacity != this.capacity) {
            this.notifyChange();
        }
    }

    @Override
    public void setMaxReceive(long maxReceive) {
        this.maxReceive = clamp(maxReceive, 0L, this.capacity);
    }

    @Override
    public void setMaxExtract(long maxExtract) {
        this.maxExtract = clamp(maxExtract, 0L, this.capacity);
    }

    @Override
    public void modifyEnergy(long delta) {
        this.modifyEnergyStored(delta);
    }

    @Override
    public long modifyEnergyStored(long delta) {
        if (delta == 0L) {
            return 0L;
        }
        while (true) {
            long current = this.energy.get();
            long next = clamp(saturatingAdd(current, delta), 0L, this.capacity);
            if (next == current) {
                return 0L;
            }
            if (this.energy.compareAndSet(current, next)) {
                this.notifyChange();
                return next > current ? next - current : current - next;
            }
        }
    }

    @Override
    public boolean canWork(long energyRequired) {
        return energyRequired >= 0L && this.energy.get() >= energyRequired;
    }

    @Override
    public boolean tryConsumeEnergy(long amount) {
        if (amount <= 0L) {
            return false;
        }

        while (true) {
            long current = this.energy.get();
            if (current < amount) {
                return false;
            }
            if (this.energy.compareAndSet(current, current - amount)) {
                this.notifyChange();
                return true;
            }
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Energy", this.energy.get());
        tag.putLong("Capacity", this.capacity);
        tag.putLong("MaxReceive", this.maxReceive);
        tag.putLong("MaxExtract", this.maxExtract);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        long loadedCapacity = tag.contains("Capacity") ? tag.getLong("Capacity") : this.capacity;
        this.capacity = Math.max(0L, loadedCapacity);
        this.maxReceive = clamp(tag.contains("MaxReceive") ? tag.getLong("MaxReceive") : this.maxReceive,
                0L, this.capacity);
        this.maxExtract = clamp(tag.contains("MaxExtract") ? tag.getLong("MaxExtract") : this.maxExtract,
                0L, this.capacity);
        this.energy.set(clamp(tag.contains("Energy") ? tag.getLong("Energy") : this.energy.get(),
                0L, this.capacity));
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
    public void setEnergyStored(long energy) {
        long next = clamp(energy, 0L, this.capacity);
        long previous = this.energy.getAndSet(next);
        if (previous != next) {
            this.notifyChange();
        }
    }

    private void notifyChange() {
        if (this.changeListener != null) {
            this.changeListener.run();
        }
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    public static class Builder {
        private long capacity = 10_000L;
        private long maxReceive = 1_000L;
        private long maxExtract = 1_000L;
        private long initialEnergy = 0L;
        private Runnable changeListener;

        public Builder capacity(long capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder maxReceive(long maxReceive) {
            this.maxReceive = maxReceive;
            return this;
        }

        public Builder maxExtract(long maxExtract) {
            this.maxExtract = maxExtract;
            return this;
        }

        public Builder maxTransfer(long maxTransfer) {
            this.maxReceive = maxTransfer;
            this.maxExtract = maxTransfer;
            return this;
        }

        public Builder initialEnergy(long initialEnergy) {
            this.initialEnergy = initialEnergy;
            return this;
        }

        public Builder onChange(Runnable listener) {
            this.changeListener = listener;
            return this;
        }

        public EnergyManager build() {
            EnergyManager manager = new EnergyManager(
                    this.capacity, this.maxReceive, this.maxExtract, this.initialEnergy);
            if (this.changeListener != null) {
                manager.setChangeListener(this.changeListener);
            }
            return manager;
        }
    }
}
