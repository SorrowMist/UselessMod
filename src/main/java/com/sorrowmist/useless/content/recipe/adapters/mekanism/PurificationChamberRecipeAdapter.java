package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.api.recipes.chemical.ItemStackChemicalToItemStackRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** Direct chemical-key adapter for the Purification Chamber. */
public final class PurificationChamberRecipeAdapter extends MetallurgicInfuserRecipeAdapter {
    @Override
    protected RecipeType<? extends ItemStackChemicalToItemStackRecipe<?,?,?>> getMekanismRecipeType() {
        return MekanismRecipeType.PURIFYING.get();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.PURIFICATION_CHAMBER.getBlock());
    }
}
