package com.sorrowmist.useless.content.recipe.adapters.powah;

import com.mojang.logging.LogUtils;
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
import owmii.powah.block.Blcks;
import owmii.powah.block.energizing.EnergizingRecipe;
import owmii.powah.recipe.Recipes;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Powah energizing recipes to alloy-furnace processing recipes. */
public class EnergizingRecipeAdapter implements IRecipeAdapter<EnergizingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int ENERGIZING_ORB_INPUT_CAPACITY = 6;

    @Override
    public Class<EnergizingRecipe> getRecipeClass() {
        return EnergizingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Blcks.ENERGIZING_ORB.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<EnergizingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Powah energizing recipe: {}", holder.id());
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                converted.energy(),
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<EnergizingRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<EnergizingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<EnergizingRecipe> holder : recipeManager.getAllRecipesFor(Recipes.ENERGIZING.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable EnergizingRecipe source) {
        if (source == null || source.getEnergy() < 0L) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        List<Ingredient> sourceIngredients = source.getIngredients();
        if (sourceIngredients == null || sourceIngredients.isEmpty()
                || sourceIngredients.size() > ENERGIZING_ORB_INPUT_CAPACITY) {
            return null;
        }
        for (Ingredient ingredient : sourceIngredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                return null;
            }
            AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
        }
        if (requirements.isEmpty()) {
            return null;
        }

        ItemStack result = source.getResultItem();
        if (result == null || result.isEmpty() || result.getCount() <= 0) {
            return null;
        }
        List<ItemStack> outputs = new ArrayList<>();
        if (!mergeOutput(outputs, result)) {
            return null;
        }

        List<CountedIngredient> counted = requirements.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(counted, outputs, requirements, source.getEnergy());
    }

    private static boolean mergeOutput(List<ItemStack> outputs, ItemStack output) {
        for (ItemStack existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, output)) {
                continue;
            }
            long count = (long) existing.getCount() + output.getCount();
            if (count > Integer.MAX_VALUE) {
                return false;
            }
            existing.setCount((int) count);
            return true;
        }
        outputs.add(output.copy());
        return true;
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            long energy) {
    }
}
