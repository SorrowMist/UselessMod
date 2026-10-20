package com.sorrowmist.useless.content.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class AdvancedAlloyFurnaceRecipeSerializer implements RecipeSerializer<AdvancedAlloyFurnaceRecipe> {
    public static final AdvancedAlloyFurnaceRecipeSerializer INSTANCE = new AdvancedAlloyFurnaceRecipeSerializer();
    @Override public AdvancedAlloyFurnaceRecipe fromJson(ResourceLocation id,JsonObject input) {
        // The manager/resource path is authoritative. KubeJS may omit or replace a JSON id.
        JsonObject json=input.deepCopy(); json.addProperty("id",id.toString());
        return AdvancedAlloyFurnaceRecipe.CODEC.codec().parse(JsonOps.INSTANCE,json)
                .getOrThrow(false,message -> { throw new JsonSyntaxException(id+": "+message); });
    }
    @Override public AdvancedAlloyFurnaceRecipe fromNetwork(ResourceLocation id,FriendlyByteBuf buffer) {
        AdvancedAlloyFurnaceRecipe recipe=AdvancedAlloyFurnaceRecipe.STREAM_CODEC.decode(buffer);
        if(!id.equals(recipe.id())) throw new IllegalArgumentException("Recipe packet ID does not match manager ID: "+id);
        return recipe;
    }
    @Override public void toNetwork(FriendlyByteBuf buffer,AdvancedAlloyFurnaceRecipe recipe) {
        AdvancedAlloyFurnaceRecipe.STREAM_CODEC.encode(buffer,recipe);
    }
}
