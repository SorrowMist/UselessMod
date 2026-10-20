package com.sorrowmist.useless.compat.forge1201.fluid;
import net.minecraftforge.fluids.FluidStack;
public final class FluidStacks {
    private FluidStacks() {}
    public static boolean same(FluidStack first,FluidStack second) {
        return first.isFluidEqual(second);
    }
}
