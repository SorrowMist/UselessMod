package com.sorrowmist.useless.core.constants;

public final class NBTConstants {

    public static final String INVENTORY = "Inventory";
    public static final String ENERGY = "Energy";
    public static final String PROGRESS = "Progress";
    public static final String MAX_PROGRESS = "MaxProgress";
    public static final String CURRENT_PARALLEL = "CurrentParallel";
    public static final String MAX_PARALLEL = "MaxParallel";
    public static final String HAS_MOLD = "HasMold";
    private NBTConstants() {
    }

    public static String getInputFluidTag(int index) {
        return "InputFluid" + index;
    }

    public static String getOutputFluidTag(int index) {
        return "OutputFluid" + index;
    }
}
