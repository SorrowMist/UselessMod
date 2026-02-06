package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class AdvancedAlloyFurnaceRecipeSerializer implements RecipeSerializer<AdvancedAlloyFurnaceRecipe> {
    public static final AdvancedAlloyFurnaceRecipeSerializer INSTANCE = new AdvancedAlloyFurnaceRecipeSerializer();

    @Override public MapCodec<AdvancedAlloyFurnaceRecipe> codec() {return AdvancedAlloyFurnaceRecipe.CODEC;}

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, AdvancedAlloyFurnaceRecipe> streamCodec() {return AdvancedAlloyFurnaceRecipe.STREAM_CODEC;}
}