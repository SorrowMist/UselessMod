package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.api.recipes.chemical.ItemStackChemicalToItemStackRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** Direct chemical-key adapter for the Chemical Injection Chamber. */
public final class ChemicalInjectionChamberRecipeAdapter extends MetallurgicInfuserRecipeAdapter {
    @Override
    protected long getEnergyPerTick() {
        return 400L;
    }

    @Override
    protected RecipeType<? extends ItemStackChemicalToItemStackRecipe<?,?,?>> getMekanismRecipeType() {
        return MekanismRecipeType.INJECTING.get();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.CHEMICAL_INJECTION_CHAMBER.getBlock());
    }
}
