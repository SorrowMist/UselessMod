package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CrusherRecipeAdapter implements IRecipeAdapter<ItemStackToItemStackRecipe> {

    @Override
    public Class<ItemStackToItemStackRecipe> getRecipeClass() {
        return ItemStackToItemStackRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.CRUSHER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackToItemStackRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        ItemStackToItemStackRecipe originalRecipe = holder.value();

        if (!originalRecipe.getType().equals(MekanismRecipeTypes.TYPE_CRUSHING.value())) {
            return result;
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        addCountedIngredient(countedIngredients, originalRecipe.getInput(), 1);

        if (countedIngredients.isEmpty()) {
            return result;
        }

        List<ItemStack> outputs = originalRecipe.getOutputDefinition();
        if (outputs.isEmpty()) {
            return result;
        }

        result.add(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                countedIngredients,
                List.of(),
                outputs.stream().map(ItemStack::copy).toList(),
                List.of(),
                AdapterUtils.mekanismEnergyCost(AdapterUtils.MEKANISM_ENRICHMENT_CHAMBER_ENERGY_PER_TICK, 1),
                AdapterUtils.mekanismProcessTime(1),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
        return result;
    }

    @Override
    @Nullable
    public List<RecipeHolder<ItemStackToItemStackRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        if (mold != null && !mold.isEmpty() && !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ItemStackToItemStackRecipe>> recipes = recipeManager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_CRUSHING.value()
        );

        List<RecipeHolder<ItemStackToItemStackRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<ItemStackToItemStackRecipe> holder : recipes) {
            ItemStackToItemStackRecipe recipe = holder.value();
            ItemStackIngredient input = recipe.getInput();
            if (input == null || input.hasNoMatchingInstances()) continue;

            if (matchesIngredient(mergedInputs, input)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static CountedIngredient countedIngredient(ItemStackIngredient input, long multiplier) {
        Ingredient ingredient = ingredient(input);
        if (ingredient.isEmpty()) {
            return null;
        }
        return new CountedIngredient(ingredient, input.ingredient().count() * multiplier);
    }

    private static Ingredient ingredient(ItemStackIngredient input) {
        if (input == null || input.hasNoMatchingInstances()) {
            return Ingredient.EMPTY;
        }
        return input.ingredient() == null ? Ingredient.EMPTY : input.ingredient().ingredient();
    }

    private static boolean matchesIngredient(Map<Ingredient, Long> mergedInputs, ItemStackIngredient required) {
        if (required == null || required.hasNoMatchingInstances()) {
            return false;
        }
        Map<Ingredient, Long> requiredCounts = new java.util.LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requiredCounts, ingredient(required), required.ingredient().count());
        return AdapterUtils.matchesRequired(mergedInputs, requiredCounts);
    }

    private static void addCountedIngredient(List<CountedIngredient> countedIngredients, ItemStackIngredient input, long multiplier) {
        CountedIngredient counted = countedIngredient(input, multiplier);
        if (counted == null) {
            return;
        }
        for (int i = 0; i < countedIngredients.size(); i++) {
            CountedIngredient existing = countedIngredients.get(i);
            if (AdapterUtils.areIngredientsEqual(existing.ingredient(), counted.ingredient())) {
                countedIngredients.set(i, new CountedIngredient(existing.ingredient(), existing.count() + counted.count()));
                return;
            }
        }
        countedIngredients.add(counted);
    }
}
