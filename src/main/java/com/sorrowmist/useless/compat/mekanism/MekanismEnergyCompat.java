package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import mekanism.api.Action;
import mekanism.api.energy.IEnergyConversion;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class MekanismEnergyCompat {
    public static final String MOD_ID = "mekanism";

    private MekanismEnergyCompat() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.STRICT_ENERGY.block(),
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> new FurnaceStrictEnergyHandler(blockEntity.getEnergyManager())
        );
    }

    static final class FurnaceStrictEnergyHandler implements IStrictEnergyHandler {
        private final IEnergyManager energy;
        private final IEnergyConversion converter;

        FurnaceStrictEnergyHandler(IEnergyManager energy) {
            this(energy, EnergyUnit.FORGE_ENERGY);
        }

        FurnaceStrictEnergyHandler(IEnergyManager energy, IEnergyConversion converter) {
            this.energy = energy;
            this.converter = converter;
        }

        @Override
        public int getEnergyContainerCount() {
            return 1;
        }

        @Override
        public long getEnergy(int container) {
            return container == 0 ? this.converter.convertFrom(this.energy.getEnergyStoredLong()) : 0L;
        }

        @Override
        public void setEnergy(int container, long energy) {
            if (container == 0) {
                this.energy.setEnergyStored(this.converter.convertTo(Math.max(0L, energy)));
            }
        }

        @Override
        public long getMaxEnergy(int container) {
            return container == 0 ? this.converter.convertFrom(this.energy.getMaxEnergyStoredLong()) : 0L;
        }

        @Override
        public long getNeededEnergy(int container) {
            if (container != 0) {
                return 0L;
            }
            long needed = this.energy.getMaxEnergyStoredLong() - this.energy.getEnergyStoredLong();
            return this.converter.convertFrom(Math.max(0L, needed));
        }

        @Override
        public long insertEnergy(int container, long amount, Action action) {
            return container == 0 ? this.insertEnergy(amount, action) : amount;
        }

        @Override
        public long insertEnergy(long amount, Action action) {
            if (!this.energy.canReceive() || amount <= 0L) {
                return amount;
            }

            long toInsert = this.converter.convertTo(amount);
            if (toInsert <= 0L) {
                return amount;
            }
            if (!this.converter.isOneToOne()) {
                long simulated = this.energy.receiveEnergy(toInsert, true);
                if (simulated <= 0L) {
                    return amount;
                }
                toInsert = this.convertFromAndBack(simulated);
                if (toInsert <= 0L) {
                    return amount;
                }
            }

            long inserted = this.energy.receiveEnergy(toInsert, action.simulate());
            long insertedJoules = this.converter.convertFrom(inserted);
            return amount - Math.min(amount, insertedJoules);
        }

        @Override
        public long extractEnergy(int container, long amount, Action action) {
            return container == 0 ? this.extractEnergy(amount, action) : 0L;
        }

        @Override
        public long extractEnergy(long amount, Action action) {
            if (!this.energy.canExtract() || amount <= 0L) {
                return 0L;
            }

            long toExtract = this.converter.convertTo(amount);
            if (toExtract <= 0L) {
                return 0L;
            }
            if (!this.converter.isOneToOne()) {
                long simulated = this.energy.extractEnergy(toExtract, true);
                toExtract = this.convertFromAndBack(simulated);
                if (toExtract <= 0L) {
                    return 0L;
                }
            }

            long extracted = this.energy.extractEnergy(toExtract, action.simulate());
            return Math.min(amount, this.converter.convertFrom(extracted));
        }

        private long convertFromAndBack(long forgeEnergy) {
            long joules = this.converter.convertFrom(forgeEnergy);
            long result = this.converter.convertTo(joules);
            double inverseConversion = 1D / this.converter.getConversion();
            if (inverseConversion >= 1D && result % inverseConversion > 0D) {
                return this.converter.convertTo(Math.max(0L, joules - 1L));
            }
            return result;
        }
    }
}
