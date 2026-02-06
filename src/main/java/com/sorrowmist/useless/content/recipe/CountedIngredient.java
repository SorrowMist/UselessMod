package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

public record CountedIngredient(
        Ingredient ingredient,
        long count
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CountedIngredient> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, CountedIngredient::ingredient,
                    ByteBufCodecs.VAR_LONG, CountedIngredient::count,
                    CountedIngredient::new
            );
    static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
            Codec.LONG.optionalFieldOf("count", 1L).forGetter(CountedIngredient::count)
    ).apply(instance, CountedIngredient::new));
}