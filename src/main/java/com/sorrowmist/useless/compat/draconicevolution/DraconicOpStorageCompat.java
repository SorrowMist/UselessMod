package com.sorrowmist.useless.compat.draconicevolution;

import com.brandon3055.brandonscore.api.power.IOPStorage;
import com.brandon3055.brandonscore.capability.CapabilityOP;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class DraconicOpStorageCompat {
    private DraconicOpStorageCompat() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                CapabilityOP.BLOCK,
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> new FurnaceOpStorage(blockEntity.getEnergyManager())
        );
        event.registerBlockEntity(
                CapabilityOP.BLOCK,
                ModBlockEntities.MULTIBLOCK_ALLOY_FURNACE_CORE.get(),
                (blockEntity, side) -> new FurnaceOpStorage(blockEntity.getEnergyManager())
        );
    }

    private record FurnaceOpStorage(IEnergyManager energy) implements IOPStorage {
        @Override
        public long receiveOP(long maxReceive, boolean simulate) {
            return this.energy.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public long extractOP(long maxExtract, boolean simulate) {
            return this.energy.extractEnergy(maxExtract, simulate);
        }

        @Override
        public long getOPStored() {
            return this.energy.getEnergyStoredLong();
        }

        @Override
        public long getMaxOPStored() {
            return this.energy.getMaxEnergyStoredLong();
        }

        @Override
        public long maxExtract() {
            return this.energy.getMaxExtractLong();
        }

        @Override
        public long maxReceive() {
            return this.energy.getMaxReceiveLong();
        }

        @Override
        public boolean canExtract() {
            return this.energy.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.energy.canReceive();
        }

        @Override
        public long modifyEnergyStored(long amount) {
            return this.energy.modifyEnergyStored(amount);
        }
    }
}
