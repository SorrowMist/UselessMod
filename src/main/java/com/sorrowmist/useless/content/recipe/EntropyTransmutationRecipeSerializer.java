package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

public class EntropyTransmutationRecipeSerializer implements RecipeSerializer<EntropyTransmutationRecipe> {
    public static final EntropyTransmutationRecipeSerializer INSTANCE = new EntropyTransmutationRecipeSerializer();

    @Override
    public MapCodec<EntropyTransmutationRecipe> codec() {
        return EntropyTransmutationRecipe.CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, EntropyTransmutationRecipe> streamCodec() {
        return EntropyTransmutationRecipe.STREAM_CODEC;
    }
}
