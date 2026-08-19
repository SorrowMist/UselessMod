package com.sorrowmist.useless.content.recipe.adapters.oritech;

import com.sorrowmist.useless.content.recipe.FluidAliasCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import rearth.oritech.util.FluidIngredient;

/** Shared helpers for Oritech recipe adapters. */
final class OritechAdapterUtils {
    static final String MOD_ID = "oritech";

    private OritechAdapterUtils() {
    }

    /** Resolves an Oritech item by path, returning {@link ItemStack#EMPTY} when absent. */
    static ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Converts Oritech's fluid ingredient into a NeoForge {@link SizedFluidIngredient} usable by
     * the alloy furnace. Tag-based ingredients are preserved as NeoForge tag fluid ingredients so
     * any fluid in the tag matches; empty inputs are null.
     */
    @Nullable
    static SizedFluidIngredient toNeoForgeInput(@Nullable FluidIngredient input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        int amount = (int) Math.min(Integer.MAX_VALUE, input.amount());
        if (amount <= 0) {
            return null;
        }
        net.neoforged.neoforge.fluids.crafting.FluidIngredient neoForgeIngredient;
        String sourceKey;
        if (input.hasTag()) {
            neoForgeIngredient = net.neoforged.neoforge.fluids.crafting.FluidIngredient.tag(input.getTag());
            sourceKey = "#" + input.getTag().location();
        } else {
            Fluid fluid = input.getFluid();
            if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                return null;
            }
            neoForgeIngredient = net.neoforged.neoforge.fluids.crafting.FluidIngredient.single(fluid);
            ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(fluid);
            sourceKey = fluidId == null ? "" : fluidId.toString();
        }
        neoForgeIngredient = FluidAliasCompat.applyAliases(neoForgeIngredient, sourceKey);
        return new SizedFluidIngredient(neoForgeIngredient, amount);
    }

    /** Converts Architectury's fluid stack into a NeoForge {@link FluidStack}. */
    static FluidStack toNeoForgeOutput(@Nullable dev.architectury.fluid.FluidStack output) {
        if (output == null || output.isEmpty()) {
            return FluidStack.EMPTY;
        }
        Fluid fluid = output.getFluid();
        if (fluid == null || fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
            return FluidStack.EMPTY;
        }
        int amount = (int) Math.min(Integer.MAX_VALUE, output.getAmount());
        return amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }
}
