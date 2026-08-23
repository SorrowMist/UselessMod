package com.sorrowmist.useless.content.recipe;

import net.minecraft.world.item.crafting.Recipe;

/**
 * @deprecated Implement {@link com.sorrowmist.useless.api.recipe.IRecipeAdapter} instead. This
 * bridge keeps existing built-in and third-party adapters source-compatible.
 */
@Deprecated(forRemoval = false)
public interface IRecipeAdapter<T extends Recipe<?>>
        extends com.sorrowmist.useless.api.recipe.IRecipeAdapter<T> {
    @Override
    default String sourceId() {
        return RecipeSourceIds.fromAdapterClass(getClass());
    }
}
