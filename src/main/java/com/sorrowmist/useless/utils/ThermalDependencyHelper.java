package com.sorrowmist.useless.utils;
import net.neoforged.fml.ModList;

public class ThermalDependencyHelper {
    private static final boolean THERMAL_LOADED = ModList.get().isLoaded("thermal");
    private static final boolean THERMAL_FOUNDATION_LOADED = ModList.get().isLoaded("thermal_foundation");
    private static final boolean THERMAL_EXPANSION_LOADED = ModList.get().isLoaded("thermal_expansion");
    private static final boolean THERMAL_DYNAMICS_LOADED = ModList.get().isLoaded("thermal_dynamics");

    public static boolean isThermalLoaded() {
        return THERMAL_LOADED;
    }

    public static boolean isThermalFoundationLoaded() {
        return THERMAL_FOUNDATION_LOADED;
    }

    public static boolean isThermalExpansionLoaded() {
        return THERMAL_EXPANSION_LOADED;
    }

    public static boolean isAnyThermalModLoaded() {
        return THERMAL_LOADED || THERMAL_FOUNDATION_LOADED || THERMAL_EXPANSION_LOADED || THERMAL_DYNAMICS_LOADED;
    }
}