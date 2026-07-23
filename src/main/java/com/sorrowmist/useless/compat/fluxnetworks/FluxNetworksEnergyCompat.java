package com.sorrowmist.useless.compat.fluxnetworks;

import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

public final class FluxNetworksEnergyCompat {
    public static final String MOD_ID = "fluxnetworks";

    private FluxNetworksEnergyCompat() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                FluxCapabilities.BLOCK,
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> new FurnaceFluxEnergyStorage(blockEntity.getEnergyManager())
        );
        event.registerBlockEntity(
                FluxCapabilities.BLOCK,
                ModBlockEntities.MULTIBLOCK_ALLOY_FURNACE_CORE.get(),
                (blockEntity, side) -> new FurnaceFluxEnergyStorage(blockEntity.getEnergyManager())
        );
    }

    static record FurnaceFluxEnergyStorage(IEnergyManager energy) implements IFNEnergyStorage {
        @Override
        public long receiveEnergyL(long maxReceive, boolean simulate) {
            return this.energy.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public long extractEnergyL(long maxExtract, boolean simulate) {
            return this.energy.extractEnergy(maxExtract, simulate);
        }

        @Override
        public long getEnergyStoredL() {
            return this.energy.getEnergyStoredLong();
        }

        @Override
        public long getMaxEnergyStoredL() {
            return this.energy.getMaxEnergyStoredLong();
        }

        @Override
        public boolean canExtract() {
            return this.energy.canExtract();
        }

        @Override
        public boolean canReceive() {
            return this.energy.canReceive();
        }
    }
}
