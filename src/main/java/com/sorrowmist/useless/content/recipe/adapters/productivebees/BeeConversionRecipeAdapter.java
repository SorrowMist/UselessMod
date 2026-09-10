package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.common.crafting.ingredient.BeeIngredient;
import cy.jdkdigital.productivebees.common.crafting.ingredient.ComponentIngredient;
import cy.jdkdigital.productivebees.common.recipe.BeeConversionRecipe;
import cy.jdkdigital.productivebees.init.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts Productive Bees bee-conversion recipes into item-input alloy-furnace recipes. */
public final class BeeConversionRecipeAdapter implements IRecipeAdapter<BeeConversionRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<BeeConversionRecipe> getRecipeClass() {
        return BeeConversionRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BeeConversionRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();

        BeeConversionRecipe source = holder.value();
        BeeIngredient sourceBee = ProductiveBeesAdapterUtils.resolveBee(source.source);
        BeeIngredient resultBee = ProductiveBeesAdapterUtils.resolveBee(source.result);
        if (sourceBee == null || resultBee == null || source.item == null || source.item.isEmpty()) {
            LOGGER.warn("Skipping Productive Bees conversion recipe with unresolved ingredient: {}",
                    holder.id());
            return List.of();
        }

        ItemStack sourceEgg = ProductiveBeesAdapterUtils.spawnEgg(sourceBee);
        ItemStack resultEgg = ProductiveBeesAdapterUtils.spawnEgg(resultBee);
        if (sourceEgg.isEmpty() || resultEgg.isEmpty()) {
            LOGGER.warn("Skipping Productive Bees conversion recipe with unresolved spawn egg: {}",
                    holder.id());
            return List.of();
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ExpectedOutputScaler.scale(List.of(
                new ExpectedOutputScaler.WeightedItemOutput(
                        resultEgg, 1, 1, source.chance)));
        if (scaled.isEmpty() || scaled.get().outputs().isEmpty()) {
            LOGGER.warn("Skipping Productive Bees conversion recipe with unsupported chance: {}",
                    holder.id());
            return List.of();
        }

        int operations = scaled.get().operations();
        OptionalInt energy = ExpectedOutputScaler.multiplyToInt(AdapterUtils.DEFAULT_ENERGY, operations);
        OptionalInt processTime = ExpectedOutputScaler.multiplyToInt(AdapterUtils.DEFAULT_PROCESS_TIME, operations);
        if (energy.isEmpty() || processTime.isEmpty()) {
            LOGGER.warn("Skipping overflowing Productive Bees conversion recipe: {}", holder.id());
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(
                        new CountedIngredient(source.item, operations),
                        new CountedIngredient(ComponentIngredient.of(sourceEgg), operations)),
                List.of(),
                List.of(),
                scaled.get().outputs(),
                List.of(),
                List.of(),
                energy.getAsInt(),
                processTime.getAsInt(),
                Ingredient.EMPTY,
                0,
                List.of(),
                AlloyFurnaceMode.NORMAL,
                AdvancedAlloyFurnaceRecipe.NO_EXPLICIT_TIER));
    }

    @Override
    public List<RecipeHolder<BeeConversionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()) return List.of();

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<BeeConversionRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<BeeConversionRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.BEE_CONVERSION_TYPE.get())) {
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (!converted.isEmpty() && matchesInputs(converted.getFirst(), mergedInputs)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean matchesInputs(
            AdvancedAlloyFurnaceRecipe recipe, Map<Ingredient, Long> mergedInputs) {
        java.util.LinkedHashMap<Ingredient, Long> required = new java.util.LinkedHashMap<>();
        for (CountedIngredient input : recipe.inputs()) {
            AdapterUtils.mergeIngredient(required, input.ingredient(), input.count());
        }
        return AdapterUtils.matchesRequired(mergedInputs, required);
    }
}
