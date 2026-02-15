package com.sorrowmist.useless.core.constants;

public final class NBTConstants {

    public static final String INVENTORY = "Inventory";
    public static final String ENERGY = "Energy";
    public static final String PROGRESS = "Progress";
    public static final String MAX_PROGRESS = "MaxProgress";
    public static final String CURRENT_PARALLEL = "CurrentParallel";
    public static final String HAS_MOLD = "HasMold";
    public static final String CACHED_PARALLEL = "CachedParallel";
    public static final String IS_USELESS_INGOT_RECIPE = "IsUselessIngotRecipe";
    public static final String TARGET_USELESS_INGOT_TIER = "TargetUselessIngotTier";
    public static final String ACCUMULATED_ENERGY = "AccumulatedEnergy";

    // 熔炉升级相关
    public static final String FURNACE_TIER = "FurnaceTier";

    private NBTConstants() {
    }

    public static String getInputFluidTag(int index) {
        return "InputFluid" + index;
    }

    public static String getOutputFluidTag(int index) {
        return "OutputFluid" + index;
    }
}
