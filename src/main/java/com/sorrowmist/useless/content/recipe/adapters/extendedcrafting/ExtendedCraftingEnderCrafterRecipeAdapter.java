package com.sorrowmist.useless.content.recipe.adapters.extendedcrafting;

import com.blakebr0.extendedcrafting.api.crafting.IEnderCrafterRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
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
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts Ender Crafter recipes; source crafting time is expressed in seconds. */
public class ExtendedCraftingEnderCrafterRecipeAdapter implements IRecipeAdapter<IEnderCrafterRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<IEnderCrafterRecipe> getRecipeClass() {
        return IEnderCrafterRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.ENDER_CRAFTER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<IEnderCrafterRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Extended Crafting ender crafter recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                converted.processTime(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<IEnderCrafterRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<IEnderCrafterRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<IEnderCrafterRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.ENDER_CRAFTER.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable IEnderCrafterRecipe source) {
        if (source == null) {
            return null;
        }
        List<Ingredient> ingredients = source.getIngredients();
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (!ExtendedCraftingAdapterUtils.mergeIngredients(requirements, ingredients)) {
            return null;
        }

        OptionalInt processTime;
        try {
            processTime = ExtendedCraftingAdapterUtils.secondsToTicks(source.getCraftingTime());
        } catch (RuntimeException exception) {
            return null;
        }
        if (processTime.isEmpty()) {
            return null;
        }

        ItemStack result = ExtendedCraftingAdapterUtils.copyResult(source);
        if (result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        Optional<List<ItemStack>> remainders = ExtendedCraftingAdapterUtils.deterministicRemainders(
                ingredients,
                java.util.Set.of(),
                stacks -> {
                    int[] dimensions = ExtendedCraftingAdapterUtils.gridDimensions(source, stacks.size());
                    return CraftingInput.of(dimensions[0], dimensions[1], stacks);
                },
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
        return new Converted(counted, outputs, requirements, processTime.getAsInt());
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            int processTime) {
    }
}
