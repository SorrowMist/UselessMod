package com.sorrowmist.useless.compat.forge1201.fluid;
import java.util.List;
public final class CompoundFluidIngredient {
    private CompoundFluidIngredient() {}
    public static FluidIngredient of(List<FluidIngredient> choices) { return FluidIngredient.any(choices); }
}
