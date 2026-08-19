package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Shared helpers for UFO Future recipe adapters. */
final class UfoAdapterUtils {
    static final String MOD_ID = "ufo";

    private UfoAdapterUtils() {
    }

    static ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Nullable
    static Fluid fluid(String id) {
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.tryParse(id));
        return fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY ? null : fluid;
    }

    static ItemStack toItemStack(@Nullable GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEItemKey key)) {
            return ItemStack.EMPTY;
        }
        int count = (int) Math.min(Integer.MAX_VALUE, stack.amount());
        return count <= 0 ? ItemStack.EMPTY : key.toStack(count);
    }

    static FluidStack toFluidStack(@Nullable GenericStack stack) {
        if (stack == null || !(stack.what() instanceof AEFluidKey key)) {
            return FluidStack.EMPTY;
        }
        int amount = (int) Math.min(Integer.MAX_VALUE, stack.amount());
        return amount <= 0 ? FluidStack.EMPTY : key.toStack(amount);
    }

    /** Maps a Stellar Nexus cooling level (1-3) to its coolant fluid id, or null for no coolant. */
    @Nullable
    static String coolantFluid(int coolingLevel) {
        return switch (coolingLevel) {
            case 1 -> "source_gelid_cryotheum";
            case 2 -> "source_stable_coolant";
            case 3 -> "source_temporal_fluid";
            default -> null;
        };
    }
}
