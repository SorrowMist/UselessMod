package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
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
    protected RecipeType<ItemStackChemicalToItemStackRecipe> getMekanismRecipeType() {
        return MekanismRecipeTypes.TYPE_INJECTING.value();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.CHEMICAL_INJECTION_CHAMBER.get());
    }
}
