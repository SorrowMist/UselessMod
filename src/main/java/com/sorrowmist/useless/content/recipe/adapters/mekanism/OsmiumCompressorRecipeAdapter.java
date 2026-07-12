package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.api.recipes.ItemStackChemicalToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public class OsmiumCompressorRecipeAdapter extends MetallurgicInfuserRecipeAdapter {

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.OSMIUM_COMPRESSOR.get());
    }

    @Override
    protected RecipeType<ItemStackChemicalToItemStackRecipe> getMekanismRecipeType() {
        return MekanismRecipeTypes.TYPE_COMPRESSING.value();
    }
}
