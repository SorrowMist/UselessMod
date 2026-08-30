package com.sorrowmist.useless.content.recipe.adapters.delight;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModItems;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts every Cooking Pot recipe registered by Farmer's Delight and its addons. */
public final class CookingPotRecipeAdapter implements IRecipeAdapter<CookingPotRecipe> {
    private static final int BASE_COOK_TIME = 200;

    @Override
    public Class<CookingPotRecipe> getRecipeClass() {
        return CookingPotRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModItems.COOKING_POT.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CookingPotRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        CookingPotRecipe source = holder.value();
        if (!isFarmersDelightRecipe(source)) {
            return List.of();
        }
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(sourceIngredients(source));
        if (inputs.isEmpty()) {
            return List.of();
        }

        ItemStack result = source.getResultItem(level == null ? null : level.registryAccess());
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        outputs.add(result.copy());

        int processTime = source.getCookTime() > 0 ? source.getCookTime() : BASE_COOK_TIME;
        long energy = Math.max(1L,
                (long) processTime * AdapterUtils.DEFAULT_ENERGY / BASE_COOK_TIME);

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                outputs,
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                molds(source),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<CookingPotRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CookingPotRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CookingPotRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.COOKING.get())) {
            Map<Ingredient, Long> requirements = ingredientRequirements(holder.value());
            if (!requirements.isEmpty() && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean isFarmersDelightRecipe(CookingPotRecipe recipe) {
        return recipe != null && recipe.getType() == ModRecipeTypes.COOKING.get();
    }

    private static Map<Ingredient, Long> ingredientRequirements(CookingPotRecipe recipe) {
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (recipe == null) {
            return requirements;
        }
        for (Ingredient ingredient : sourceIngredients(recipe)) {
            if (!AdapterUtils.isIngredientEmpty(ingredient)) {
                AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
            }
        }
        return requirements;
    }

    /** The cooking pot consumes its serving container while producing the meal. */
    private static List<Ingredient> sourceIngredients(CookingPotRecipe recipe) {
        if (recipe == null) {
            return List.of();
        }

        List<Ingredient> ingredients = new ArrayList<>(recipe.getIngredients());
        ItemStack container = recipe.getOutputContainer();
        if (!DelightRecipeAdapterUtils.isBakingTray(container)
                && container != null && !container.isEmpty() && container.getCount() > 0) {
            for (int i = 0; i < container.getCount(); i++) {
                ingredients.add(Ingredient.of(container.copyWithCount(1)));
            }
        }
        return ingredients;
    }

    private static List<Ingredient> molds(CookingPotRecipe recipe) {
        List<Ingredient> molds = new ArrayList<>();
        Ingredient cookingPot = AdapterUtils.toMoldIngredient(new ItemStack(ModItems.COOKING_POT.get()));
        if (!cookingPot.isEmpty()) {
            molds.add(cookingPot);
        }
        molds.addAll(DelightRecipeAdapterUtils.bakingTrayMolds(
                recipe == null ? ItemStack.EMPTY : recipe.getOutputContainer()));
        return List.copyOf(molds);
    }

}
