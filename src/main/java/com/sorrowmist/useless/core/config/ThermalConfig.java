package com.sorrowmist.useless.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Thermal options share the community build's common configuration. */
public final class ThermalConfig {
    private ThermalConfig() {
    }

    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_ITEM_CAPACITY;
    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_FLUID_CAPACITY;
    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_ENERGY_CAPACITY;
    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_ITEM_TRANSFER;
    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_FLUID_TRANSFER;
    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_ENERGY_TRANSFER;
    public static ForgeConfigSpec.BooleanValue PARALLEL_INCREASE_ENERGY_CONSUMPTION;
    public static ForgeConfigSpec.BooleanValue BASE_MOD_AFFECT_PARALLEL;

    public static void define(ForgeConfigSpec.Builder builder) {
        builder.push("thermal_parallel");
        PARALLEL_INCREASE_ITEM_CAPACITY = builder.define("parallel_increase_item_capacity", true);
        PARALLEL_INCREASE_FLUID_CAPACITY = builder.define("parallel_increase_fluid_capacity", true);
        PARALLEL_INCREASE_ENERGY_CAPACITY = builder.define("parallel_increase_energy_capacity", false);
        PARALLEL_INCREASE_ITEM_TRANSFER = builder.define("parallel_increase_item_transfer", true);
        PARALLEL_INCREASE_FLUID_TRANSFER = builder.define("parallel_increase_fluid_transfer", true);
        PARALLEL_INCREASE_ENERGY_TRANSFER = builder.define("parallel_increase_energy_transfer", true);
        PARALLEL_INCREASE_ENERGY_CONSUMPTION = builder.define("parallel_increase_energy_consumption", false);
        BASE_MOD_AFFECT_PARALLEL = builder.define("base_mod_affect_parallel", false);
        builder.pop();
    }

    public static boolean enabled(ForgeConfigSpec.BooleanValue option) {
        try {
            return option.get();
        } catch (IllegalStateException ignored) {
            return option.getDefault();
        }
    }
}
