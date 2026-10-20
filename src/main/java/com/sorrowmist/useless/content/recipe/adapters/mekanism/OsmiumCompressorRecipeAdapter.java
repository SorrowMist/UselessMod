package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import mekanism.api.recipes.chemical.ItemStackChemicalToItemStackRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

public class OsmiumCompressorRecipeAdapter extends MetallurgicInfuserRecipeAdapter {

    @Override
    protected long getEnergyPerTick() {
        return 100L;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.OSMIUM_COMPRESSOR.getBlock());
    }

    @Override
    protected RecipeType<? extends ItemStackChemicalToItemStackRecipe<?,?,?>> getMekanismRecipeType() {
        return MekanismRecipeType.COMPRESSING.get();
    }
}
