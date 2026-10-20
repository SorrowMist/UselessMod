package com.sorrowmist.useless.content.recipe.adapters.extendedcrafting;

import com.sorrowmist.useless.utils.RateLimitedLogger;

import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Converts Extended Crafting compressor recipes, retaining the catalyst as a mold requirement. */
public class ExtendedCraftingCompressorRecipeAdapter implements IRecipeAdapter<ICompressorRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<ICompressorRecipe> getRecipeClass() {
        return ICompressorRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.COMPRESSOR.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ICompressorRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            RateLimitedLogger.warn(LOGGER, "Skipping invalid Extended Crafting compressor recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.<SizedFluidIngredient>of(),
                List.of(),
                converted.outputs(),
                List.of(),
                List.of(),
                converted.energy(),
                converted.processTime(),
                Ingredient.EMPTY,
                0,
                converted.molds(),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<ICompressorRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<ICompressorRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<ICompressorRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getAllRecipesFor(recipeManager, ModRecipeTypes.COMPRESSOR.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable ICompressorRecipe source) {
        if (source == null) {
            return null;
        }
        List<Ingredient> sourceIngredients = source.getIngredients();
        if (sourceIngredients == null || sourceIngredients.isEmpty()) {
            return null;
        }

        Ingredient countedInput = sourceIngredients.get(0);
        if (countedInput == null || countedInput.isEmpty()) {
            return null;
        }
        int inputCount;
        try {
            inputCount = source.getInputCount();
        } catch (RuntimeException exception) {
            return null;
        }
        if (inputCount <= 0) {
            return null;
        }

        // IngredientWithCount already checks the stack count in test(). Keep its
        // quantity as the CountedIngredient amount and unwrap only the item
        // candidates, otherwise the count would be applied twice.
        Ingredient input = unwrapCountIngredient(countedInput);
        if (input == null || input.isEmpty()) {
            return null;
        }

        Ingredient catalyst = source.getCatalyst();
        if (catalyst == null || catalyst.isEmpty()) {
            return null;
        }
        long powerCost = source.getPowerCost();
        long powerRate = source.getPowerRate();
        OptionalInt processTime = ExtendedCraftingAdapterUtils.powerProcessTime(powerCost, powerRate);
        if (processTime.isEmpty()) {
            return null;
        }

        ItemStack result = ExtendedCraftingAdapterUtils.copyResult(source);
        if (result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        AdapterUtils.mergeIngredient(requirements, input, inputCount);

        Optional<List<ItemStack>> remainders = ExtendedCraftingAdapterUtils.deterministicRemainders(
                List.of(input, catalyst),
                Set.of(1),
                stacks -> com.sorrowmist.useless.compat.forge1201.recipe.CraftingInputs.of(2, 1, stacks),
                source::getRemainingItems
        );
        if (remainders.isEmpty()) {
            return null;
        }

        List<ItemStack> outputs = new ArrayList<>();
        if (!ExtendedCraftingAdapterUtils.mergeOutput(outputs, result)) {
            return null;
        }
        for (ItemStack remainder : remainders.get()) {
            if (!ExtendedCraftingAdapterUtils.mergeOutput(outputs, remainder)) {
                return null;
            }
        }

        List<CountedIngredient> counted = ExtendedCraftingAdapterUtils.countedIngredients(requirements);
        List<Ingredient> molds = List.of(
                AdapterUtils.toMoldIngredient(new ItemStack(ModBlocks.COMPRESSOR.get())),
                catalyst);
        return new Converted(counted, outputs, requirements, molds, powerCost, processTime.getAsInt());
    }

    @Nullable
    private static Ingredient unwrapCountIngredient(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        return ingredient; // Native compressor keeps quantity in getInputCount().
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            List<Ingredient> molds,
            long energy,
            int processTime) {
    }
}
