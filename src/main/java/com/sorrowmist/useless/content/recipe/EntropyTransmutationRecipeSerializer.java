package com.sorrowmist.useless.content.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/** Forge 1.20.1 recipe-manager entry points; the resource path owns the recipe ID. */
public final class EntropyTransmutationRecipeSerializer implements RecipeSerializer<EntropyTransmutationRecipe> {
    public static final EntropyTransmutationRecipeSerializer INSTANCE = new EntropyTransmutationRecipeSerializer();

    @Override
    public EntropyTransmutationRecipe fromJson(ResourceLocation id, JsonObject input) {
        JsonObject json = input.deepCopy();
        json.addProperty("id", id.toString());
        try {
            return EntropyTransmutationRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json)
                    .getOrThrow(false, message -> { throw new JsonSyntaxException(id + ": " + message); });
        } catch (IllegalArgumentException error) {
            throw new JsonSyntaxException(id + ": " + error.getMessage(), error);
        }
    }

    @Override
    public EntropyTransmutationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        EntropyTransmutationRecipe recipe = EntropyTransmutationRecipe.STREAM_CODEC.decode(buffer);
        if (!id.equals(recipe.id())) throw new IllegalArgumentException("Entropy recipe packet ID mismatch: " + id);
        return recipe;
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, EntropyTransmutationRecipe recipe) {
        EntropyTransmutationRecipe.STREAM_CODEC.encode(buffer, recipe);
    }
}
