package com.sorrowmist.useless.compat.mekanism;

import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.init.ModBlockEntities;
import mekanism.api.energy.IEnergyConversion;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import com.sorrowmist.useless.compat.forge1201.capability.RegisterCapabilitiesEvent;

public final class MekanismEnergyCompat {
    public static final String MOD_ID = "mekanism";

    private MekanismEnergyCompat() {
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.STRICT_ENERGY,
                ModBlockEntities.ADVANCED_ALLOY_FURNACE.get(),
                (blockEntity, side) -> new FurnaceStrictEnergyHandler(blockEntity.getEnergyManager())
        );
        event.registerBlockEntity(
                Capabilities.STRICT_ENERGY,
                ModBlockEntities.MULTIBLOCK_ALLOY_FURNACE_CORE.get(),
                (blockEntity, side) -> new FurnaceStrictEnergyHandler(blockEntity.getEnergyManager())
        );
    }

    static final class FurnaceStrictEnergyHandler extends com.sorrowmist.useless.compat.forge1201.mekanism.NativeStrictEnergyHandler {
        FurnaceStrictEnergyHandler(IEnergyManager energy) { super(energy, EnergyUnit.FORGE_ENERGY); }
        FurnaceStrictEnergyHandler(IEnergyManager energy, IEnergyConversion converter) { super(energy, converter); }
    }
}
