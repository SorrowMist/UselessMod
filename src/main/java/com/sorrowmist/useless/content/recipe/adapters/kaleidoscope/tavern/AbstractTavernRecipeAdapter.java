package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class AbstractTavernRecipeAdapter<T extends Recipe<?>> implements IRecipeAdapter<T> {
    protected abstract ResourceLocation moldId();

    @Nullable
    protected abstract AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, T source, Level level);

    @Override
    public final String sourceId() {
        return RecipeSourceIds.KALEIDOSCOPE_TAVERN;
    }

    @Override
    public final ItemStack getMoldItem() {
        Item item = DelightRecipeAdapterUtils.registeredItem(moldId());
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public final List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<T> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        AdvancedAlloyFurnaceRecipe converted = convertSource(holder.id(), holder.value(), level);
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public final List<RecipeHolder<T>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public final List<RecipeHolder<T>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)
                || (mergedKeys != null && !mergedKeys.isEmpty())) {
            return List.of();
        }

        List<RecipeHolder<T>> matches = new ArrayList<>();
        for (RecipeHolder<T> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), getRecipeClass())) {
            AdvancedAlloyFurnaceRecipe converted = convertSource(
                    holder.id(), holder.value(), level);
            if (converted != null
                    && DelightRecipeAdapterUtils.matchesItems(
                    converted.inputs(), mergedInputs, actualInputs)
                    && DelightRecipeAdapterUtils.matchesFluids(
                    converted.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    protected final List<Ingredient> moldIngredients() {
        Ingredient mold = AdapterUtils.toMoldIngredient(getMoldItem());
        return mold.isEmpty() ? List.of() : List.of(mold);
    }

    protected static List<Ingredient> nonEmptyIngredients(List<Ingredient> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        return ingredients.stream()
                .filter(ingredient -> !AdapterUtils.isIngredientEmpty(ingredient))
                .toList();
    }

    protected static int clampProcessTime(long processTime) {
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, processTime));
    }

    protected static long scaledEnergy(int processTime) {
        return Math.max(1L, (long) processTime * AdapterUtils.DEFAULT_ENERGY / 200L);
    }
}
