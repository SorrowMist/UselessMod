package com.sorrowmist.useless.compat.forge1201.recipe;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** No independent cache: a datapack/KubeJS reload is visible on the very next lookup. */
public final class RecipeManagerAccess {
    private RecipeManagerAccess() {}
    public static Collection<RecipeHolder<?>> getRecipes(RecipeManager manager) {
        List<RecipeHolder<?>> values=new ArrayList<>();
        for(Recipe<?> recipe:manager.getRecipes()) values.add(RecipeHolder.of(recipe));
        return List.copyOf(values);
    }
    public static <C extends Container,T extends Recipe<C>> List<RecipeHolder<T>> getAllRecipesFor(RecipeManager manager,RecipeType<? extends T> type) {
        List<RecipeHolder<T>> result=new ArrayList<>();
        for(T recipe:manager.getAllRecipesFor(type)) result.add(RecipeHolder.of(recipe));
        return List.copyOf(result);
    }
    public static <C extends Container,T extends Recipe<C>> List<RecipeHolder<T>> getRecipesFor(RecipeManager manager,RecipeType<T> type,C input,Level level) {
        return manager.getRecipesFor(type,input,level).stream().map(RecipeHolder::of).toList();
    }
    public static <C extends Container,T extends Recipe<C>> Optional<RecipeHolder<T>> getRecipeFor(RecipeManager manager,RecipeType<T> type,C input,Level level) {
        return manager.getRecipeFor(type,input,level).map(RecipeHolder::of);
    }
    public static Optional<RecipeHolder<?>> byKey(RecipeManager manager,ResourceLocation id) {
        return manager.byKey(id).map(recipe->new RecipeHolder<>(recipe.getId(),recipe));
    }
}
