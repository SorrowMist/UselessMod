package com.sorrowmist.useless.compat.forge1201.fluid;
import net.minecraftforge.fluids.FluidStack;
/** Source compatibility name; predicates are implemented using native Forge fluid NBT. */
public final class DataComponentFluidIngredient {
    private DataComponentFluidIngredient() {}
    public static FluidIngredient of(boolean strict,FluidStack stack) { return FluidIngredient.nbt(strict,stack); }
}
