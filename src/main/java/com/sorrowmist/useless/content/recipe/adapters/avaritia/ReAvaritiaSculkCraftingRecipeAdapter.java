package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingAdapterUtils;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts the vanilla shaped recipe used to create Re-Avaritia's sculk table. */
public final class ReAvaritiaSculkCraftingRecipeAdapter implements IRecipeAdapter<CraftingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public String sourceId() {
        return RecipeSourceIds.AVARITIA;
    }

    @Override
    public Class<CraftingRecipe> getRecipeClass() {
        return CraftingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.sculk_crafting_table.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CraftingRecipe> holder, Level level) {
        if (holder == null || !isSculkTableRecipe(holder.value())) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Re-Avaritia sculk crafting recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<CraftingRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!isSculkTableRecipe(holder.value())) {
                continue;
            }
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean isSculkTableRecipe(@Nullable CraftingRecipe recipe) {
        return recipe != null
                && ExtendedCraftingAdapterUtils.copyResult(recipe).is(ModBlocks.sculk_crafting_table.get().asItem());
    }

    @Nullable
    private static Converted convertData(@Nullable CraftingRecipe source) {
        if (source == null) {
            return null;
        }

        List<Ingredient> ingredients;
        try {
            ingredients = source.getIngredients();
        } catch (RuntimeException exception) {
            return null;
        }
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }

        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (!ExtendedCraftingAdapterUtils.mergeIngredients(requirements, ingredients)) {
            return null;
        }

        ItemStack result = ExtendedCraftingAdapterUtils.copyResult(source);
        if (result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        Optional<List<ItemStack>> remainders;
        try {
            remainders = ExtendedCraftingAdapterUtils.deterministicRemainders(
                    ingredients,
                    Set.of(),
                    stacks -> {
                        int[] dimensions = ExtendedCraftingAdapterUtils.gridDimensions(source, stacks.size());
                        return CraftingInput.of(dimensions[0], dimensions[1], stacks);
                    },
                    source::getRemainingItems
            );
        } catch (RuntimeException exception) {
            return null;
        }
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

        return new Converted(
                ExtendedCraftingAdapterUtils.countedIngredients(requirements),
                outputs,
                requirements);
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements) {
    }
}
