package com.sorrowmist.useless.compat.forge1201.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;

/** The source manager's ID is retained verbatim, including KubeJS/runtime recipe IDs. */
public record RecipeHolder<T extends Recipe<?>>(ResourceLocation id,T value) {
    public RecipeHolder {
        java.util.Objects.requireNonNull(id,"Recipe ID");
        java.util.Objects.requireNonNull(value,"Recipe");
    }
    public static <T extends Recipe<?>> RecipeHolder<T> of(T recipe) {
        return new RecipeHolder<>(recipe.getId(),recipe);
    }
}
