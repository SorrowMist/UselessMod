package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import java.util.List;

/** Shared helpers for NeoVitae recipe adapters. */
final class NeoVitaeAdapterUtils {
    static final String MOD_ID = "neovitae";

    private NeoVitaeAdapterUtils() {
    }

    /** Resolves a NeoVitae item by path, returning {@link ItemStack#EMPTY} when absent. */
    static ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Returns whether the given fluid ingredient is NeoVitae's Essentia Vitae ("life essence")
     * blood fluid. This fluid can normally only be produced by self-sacrifice beside an altar,
     * so alloy-furnace adapters convert it to FE energy instead of requiring it as a fluid input.
     */
    static boolean isEssentiaVitae(FluidIngredient ingredient) {
        if (ingredient == null) {
            return false;
        }
        for (String name : List.of("essentia_vitae_source", "essentia_vitae_flowing")) {
            Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, name));
            if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY
                    && ingredient.test(new FluidStack(fluid, 1000))) {
                return true;
            }
        }
        return false;
    }
}
