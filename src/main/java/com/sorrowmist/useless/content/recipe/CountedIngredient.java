package com.sorrowmist.useless.content.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import com.sorrowmist.useless.compat.forge1201.network.ByteBufCodecs;
import com.sorrowmist.useless.compat.forge1201.network.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;

public record CountedIngredient(
        Ingredient ingredient,
        long count
) {
    static final StreamCodec<FriendlyByteBuf, Ingredient> INGREDIENT_STREAM_CODEC =
            com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs.INGREDIENT_STREAM;

    public static final StreamCodec<FriendlyByteBuf, CountedIngredient> STREAM_CODEC =
            StreamCodec.composite(
                    INGREDIENT_STREAM_CODEC, CountedIngredient::ingredient,
                    ByteBufCodecs.VAR_LONG, CountedIngredient::count,
                    CountedIngredient::new
            );
    static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            com.sorrowmist.useless.compat.forge1201.recipe.RecipeCodecs.INGREDIENT.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
            Codec.LONG.optionalFieldOf("count", 1L).forGetter(CountedIngredient::count)
    ).apply(instance, CountedIngredient::new));
}
