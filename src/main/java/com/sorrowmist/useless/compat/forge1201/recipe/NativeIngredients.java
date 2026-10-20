package com.sorrowmist.useless.compat.forge1201.recipe;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import java.util.Optional;

/** Inspect native serialized values, not private Ingredient fields or materialized tag members. */
public final class NativeIngredients {
    private NativeIngredients() {}
    public static Optional<TagKey<Item>> directItemTag(Ingredient ingredient) {
        if(ingredient==null || !ingredient.isVanilla()) return Optional.empty();
        JsonElement value=ingredient.toJson();
        if(!value.isJsonObject() || !value.getAsJsonObject().has("tag")) return Optional.empty();
        return Optional.of(TagKey.create(Registries.ITEM,new ResourceLocation(value.getAsJsonObject().get("tag").getAsString())));
    }
    /** Pure native tag unions only; never infer a custom/NBT predicate from display items. */
    public static Optional<java.util.List<TagKey<Item>>> itemTags(Ingredient ingredient) {
        if(ingredient==null || !ingredient.isVanilla()) return Optional.empty();
        JsonElement value=ingredient.toJson();
        var elements=value.isJsonArray()?value.getAsJsonArray():java.util.List.of(value);
        var tags=new java.util.LinkedHashSet<TagKey<Item>>();
        for(JsonElement child:elements) {
            if(!child.isJsonObject() || !child.getAsJsonObject().has("tag")) return Optional.empty();
            tags.add(TagKey.create(Registries.ITEM,new ResourceLocation(child.getAsJsonObject().get("tag").getAsString())));
        }
        return tags.isEmpty()?Optional.empty():Optional.of(java.util.List.copyOf(tags));
    }
    public static boolean hasTag(Ingredient ingredient) {
        if(ingredient==null || !ingredient.isVanilla()) return false;
        JsonElement value=ingredient.toJson();
        if(value.isJsonArray()) {
            for(JsonElement child:value.getAsJsonArray())
                if(child.isJsonObject() && child.getAsJsonObject().has("tag")) return true;
            return false;
        }
        return value.isJsonObject() && value.getAsJsonObject().has("tag");
    }
    public static boolean isSingleItem(Ingredient ingredient) {
        if(ingredient==null || !ingredient.isVanilla() || ingredient.isEmpty()) return false;
        JsonElement value=ingredient.toJson();
        return value.isJsonObject() && value.getAsJsonObject().has("item");
    }
}
