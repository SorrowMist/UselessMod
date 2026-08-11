package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.SawmillRecipe;
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

public class PrecisionSawmillRecipeAdapter implements IRecipeAdapter<SawmillRecipe> {

    @Override
    public Class<SawmillRecipe> getRecipeClass() {
        return SawmillRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.PRECISION_SAWMILL.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<SawmillRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        SawmillRecipe originalRecipe = holder.value();

        if (!originalRecipe.getType().equals(MekanismRecipeTypes.TYPE_SAWING.value())) {
            return result;
        }

        ChanceScale chanceScale = scaleChance(originalRecipe.getSecondaryChance());
        long multiplier = chanceScale.denominator();

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        addCountedIngredient(countedIngredients, originalRecipe.getInput(), multiplier);

        if (countedIngredients.isEmpty()) {
            return result;
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack output : originalRecipe.getMainOutputDefinition()) {
            if (!output.isEmpty()) {
                ItemStack scaled = output.copy();
                scaled.setCount(AdapterUtils.safeInt((long) output.getCount() * multiplier));
                outputs.add(scaled);
            }
        }
        if (chanceScale.numerator() > 0) {
            for (ItemStack output : originalRecipe.getSecondaryOutputDefinition()) {
                if (!output.isEmpty()) {
                    ItemStack scaled = output.copy();
                    scaled.setCount(AdapterUtils.safeInt((long) output.getCount() * chanceScale.numerator()));
                    outputs.add(scaled);
                }
            }
        }

        if (outputs.isEmpty()) {
            return result;
        }

        result.add(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                countedIngredients,
                List.of(),
                outputs,
                List.of(),
                AdapterUtils.mekanismEnergyCost(AdapterUtils.MEKANISM_ENRICHMENT_CHAMBER_ENERGY_PER_TICK, multiplier),
                AdapterUtils.mekanismProcessTime(multiplier),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
        return result;
    }

    @Override
    @Nullable
    public List<RecipeHolder<SawmillRecipe>> findMatchingRecipes(Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        if (mold != null && !mold.isEmpty() && !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<SawmillRecipe>> recipes = recipeManager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_SAWING.value()
        );

        List<RecipeHolder<SawmillRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<SawmillRecipe> holder : recipes) {
            SawmillRecipe recipe = holder.value();
            ItemStackIngredient input = recipe.getInput();
            if (input == null || input.hasNoMatchingInstances()) continue;

            if (matchesIngredient(mergedInputs, input)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static ChanceScale scaleChance(double chance) {
        if (chance <= 0) {
            return new ChanceScale(0, 1);
        }
        if (chance >= 1) {
            return new ChanceScale(1, 1);
        }
        int bestDenominator = 1;
        int bestNumerator = 0;
        double bestError = Double.MAX_VALUE;
        for (int denominator = 1; denominator <= 1000; denominator++) {
            int numerator = (int) Math.round(chance * denominator);
            double error = Math.abs(chance - (double) numerator / denominator);
            if (error < bestError) {
                bestError = error;
                bestDenominator = denominator;
                bestNumerator = numerator;
                if (error < 1.0E-9) {
                    break;
                }
            }
        }
        long gcd = AdapterUtils.gcd(bestNumerator, bestDenominator);
        return new ChanceScale((int) (bestNumerator / gcd), (int) (bestDenominator / gcd));
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

    private record ChanceScale(int numerator, int denominator) {}
}
