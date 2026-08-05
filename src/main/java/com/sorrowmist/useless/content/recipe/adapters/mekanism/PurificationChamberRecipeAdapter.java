package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

/** Direct chemical-key adapter for the Purification Chamber. */
public final class PurificationChamberRecipeAdapter extends MetallurgicInfuserRecipeAdapter {
    @Override
    protected RecipeType<ItemStackChemicalToItemStackRecipe> getMekanismRecipeType() {
        return MekanismRecipeTypes.TYPE_PURIFYING.value();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.PURIFICATION_CHAMBER.get());
    }
}
