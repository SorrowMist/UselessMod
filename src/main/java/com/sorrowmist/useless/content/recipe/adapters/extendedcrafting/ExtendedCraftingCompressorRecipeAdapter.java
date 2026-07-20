package com.sorrowmist.useless.content.recipe.adapters.extendedcrafting;

import com.blakebr0.extendedcrafting.api.crafting.ICompressorRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
import com.blakebr0.cucumber.crafting.ingredient.IngredientWithCount;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Converts Extended Crafting compressor recipes, including their catalyst return. */
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
            LOGGER.warn("Skipping invalid Extended Crafting compressor recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                converted.energy(),
                converted.processTime(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
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
        for (RecipeHolder<ICompressorRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.COMPRESSOR.get())) {
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

        Ingredient countedInput = sourceIngredients.getFirst();
        if (countedInput == null || countedInput.isEmpty()) {
            return null;
        }
        int inputCount;
        try {
            inputCount = source.getCount(0);
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
        Optional<ItemStack> catalystStack = ExtendedCraftingAdapterUtils.deterministicStack(catalyst);
        if (catalystStack.isEmpty()) {
            LOGGER.warn("Skipping compressor recipe with non-deterministic catalyst");
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
        AdapterUtils.mergeIngredient(requirements, catalyst, 1L);

        Optional<List<ItemStack>> remainders = ExtendedCraftingAdapterUtils.deterministicRemainders(
                List.of(input, catalyst),
                Set.of(1),
                stacks -> CraftingInput.of(2, 1, stacks),
                source::getRemainingItems
        );
        if (remainders.isEmpty()) {
            return null;
        }

        List<ItemStack> outputs = new ArrayList<>();
        if (!ExtendedCraftingAdapterUtils.mergeOutput(outputs, result)
                || !ExtendedCraftingAdapterUtils.mergeOutput(outputs, catalystStack.get())) {
            return null;
        }
        for (ItemStack remainder : remainders.get()) {
            if (!ExtendedCraftingAdapterUtils.mergeOutput(outputs, remainder)) {
                return null;
            }
        }

        List<CountedIngredient> counted = ExtendedCraftingAdapterUtils.countedIngredients(requirements);
        return new Converted(counted, outputs, requirements, powerCost, processTime.getAsInt());
    }

    @Nullable
    private static Ingredient unwrapCountIngredient(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        ICustomIngredient custom = ingredient.getCustomIngredient();
        if (!(custom instanceof IngredientWithCount withCount)) {
            return ingredient;
        }
        ItemStack[] candidates = withCount.getItems()
                .map(stack -> stack.copyWithCount(1))
                .filter(stack -> !stack.isEmpty())
                .toArray(ItemStack[]::new);
        if (candidates.length == 0) {
            return null;
        }
        Ingredient[] componentCandidates = java.util.Arrays.stream(candidates)
                .map(stack -> stack.isComponentsPatchEmpty()
                        ? Ingredient.of(stack.getItem())
                        : DataComponentIngredient.of(true, stack))
                .toArray(Ingredient[]::new);
        return componentCandidates.length == 1
                ? componentCandidates[0]
                : CompoundIngredient.of(componentCandidates);
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            long energy,
            int processTime) {
    }
}
