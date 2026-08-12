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

/**
 * Mekanism 富集仓配方适配器
 */
public class EnrichmentChamberRecipeAdapter implements IRecipeAdapter<ItemStackToItemStackRecipe> {

    @Override
    public Class<ItemStackToItemStackRecipe> getRecipeClass() {
        return ItemStackToItemStackRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.ENRICHMENT_CHAMBER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ItemStackToItemStackRecipe> holder, Level level) {
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (holder == null) return result;

        ItemStackToItemStackRecipe originalRecipe = holder.value();

        if (!originalRecipe.getType().equals(MekanismRecipeTypes.TYPE_ENRICHING.value())) {
            return result;
        }

        List<CountedIngredient> countedIngredients = new ArrayList<>();
        var input = originalRecipe.getInput();
        addCountedIngredient(countedIngredients, input, 1);

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
                outputs,
                List.of(),
                AdapterUtils.mekanismEnrichmentChamberEnergyCost(1),
                AdapterUtils.mekanismEnrichmentChamberProcessTime(1),
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
                MekanismRecipeTypes.TYPE_ENRICHING.value()
        );

        List<RecipeHolder<ItemStackToItemStackRecipe>> matches = new java.util.ArrayList<>();
        for (RecipeHolder<ItemStackToItemStackRecipe> holder : recipes) {
            ItemStackToItemStackRecipe recipe = holder.value();

            var input = recipe.getInput();
            if (input == null) continue;

            if (matchesIngredient(mergedInputs, input)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    // ========== Helper Methods ==========

    @Nullable
    private static CountedIngredient countedIngredient(ItemStackIngredient input, long multiplier) {
        return MekanismChemicalRecipeSupport.countedItem(input, multiplier);
    }

    private static boolean matchesIngredient(Map<Ingredient, Long> mergedInputs, ItemStackIngredient required) {
        if (required == null) {
            return false;
        }
        CountedIngredient counted = MekanismChemicalRecipeSupport.item(required);
        if (counted == null) return false;
        Map<Ingredient, Long> requiredCounts = new java.util.LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requiredCounts, counted.ingredient(), counted.count());
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
