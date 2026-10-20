package com.sorrowmist.useless.compat.forge1201.nbt;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/** Native 1.20.1 NBT, including full foreign tags/capabilities and large item counts. */
public final class NativeStackNbt {
    private NativeStackNbt() {}
    public static CompoundTag saveItem(ItemStack stack, HolderLookup.Provider ignored) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CompoundTag result = stack.save(new CompoundTag());
        // Native ItemStack writes Count as a signed byte. Never truncate a high-stack buffer.
        if (stack.getCount() > Byte.MAX_VALUE) {
            result.putInt("RealCount", stack.getCount());
            result.putByte("Count", (byte)1);
        }
        return result;
    }
    public static java.util.Optional<ItemStack> parseItem(HolderLookup.Provider lookup, CompoundTag tag) {
        ItemStack result=readItem(lookup,tag);
        return result.isEmpty()?java.util.Optional.empty():java.util.Optional.of(result);
    }
    public static ItemStack readItem(HolderLookup.Provider ignored, CompoundTag tag) {
        requireNative(tag);
        ItemStack result = ItemStack.of(tag.copy());
        if (!result.isEmpty() && tag.contains("RealCount", Tag.TAG_ANY_NUMERIC)) {
            result.setCount(Math.max(0, tag.getInt("RealCount")));
        }
        return result;
    }
    public static CompoundTag saveFluid(FluidStack stack, HolderLookup.Provider ignored) {
        return stack == null ? new CompoundTag() : stack.writeToNBT(new CompoundTag());
    }
    public static FluidStack readFluid(HolderLookup.Provider ignored, CompoundTag tag) {
        requireNative(tag);
        return FluidStack.loadFluidStackFromNBT(tag.copy());
    }
    private static void requireNative(CompoundTag tag) {
        if (tag.contains("components", Tag.TAG_COMPOUND) && !tag.getCompound("components").isEmpty()) {
            throw new IllegalArgumentException("Cannot silently discard non-native 1.21 component NBT");
        }
    }
}
