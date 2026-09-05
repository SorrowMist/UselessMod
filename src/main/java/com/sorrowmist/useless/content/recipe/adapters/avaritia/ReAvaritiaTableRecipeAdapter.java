package com.sorrowmist.useless.content.recipe.adapters.avaritia;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingAdapterUtils;
import committee.nova.mods.avaritia.api.common.crafting.ITierCraftingRecipe;
import committee.nova.mods.avaritia.api.common.crafting.TierInput;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
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
import java.util.Set;

/** Converts Re-Avaritia's tiered (sculk through extreme) crafting recipes. */
public final class ReAvaritiaTableRecipeAdapter implements IRecipeAdapter<ITierCraftingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_TABLE_SLOTS = 81;

    @Override
    public String sourceId() {
        return RecipeSourceIds.AVARITIA;
    }

    @Override
    public Class<ITierCraftingRecipe> getRecipeClass() {
        return ITierCraftingRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        if (mold == null || mold.isEmpty()) {
            return false;
        }
        for (int tier = 1; tier <= 4; tier++) {
            if (tableMold(tier).is(mold.getItem())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ITierCraftingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Re-Avaritia table recipe: {}", holder.id());
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
                AdapterUtils.toMoldIngredient(converted.mold()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<ITierCraftingRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)
                || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        List<RecipeHolder<ITierCraftingRecipe>> matches = new ArrayList<>();
        RecipeManager recipeManager = level.getRecipeManager();
        for (RecipeHolder<ITierCraftingRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.CRAFTING_TABLE_RECIPE.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null
                    && converted.mold().is(mold.getItem())
                    && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable ITierCraftingRecipe source) {
        if (source == null) {
            return null;
        }

        final int tier;
        try {
            tier = source.getTier();
        } catch (RuntimeException exception) {
            return null;
        }
        if (tier < 1 || tier > 4) {
            return null;
        }

        List<Ingredient> ingredients;
        try {
            ingredients = source.getIngredients();
        } catch (RuntimeException exception) {
            return null;
        }
        if (ingredients == null || ingredients.isEmpty() || ingredients.size() > MAX_TABLE_SLOTS) {
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
                        return TierInput.of(dimensions[0], dimensions[1], stacks, tier);
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
                requirements,
                tableMold(tier));
    }

    private static ItemStack tableMold(int tier) {
        return switch (tier) {
            case 1 -> new ItemStack(ModBlocks.sculk_crafting_table.get());
            case 2 -> new ItemStack(ModBlocks.nether_crafting_table.get());
            case 3 -> new ItemStack(ModBlocks.end_crafting_table.get());
            case 4 -> new ItemStack(ModBlocks.extreme_crafting_table.get());
            default -> ItemStack.EMPTY;
        };
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            ItemStack mold) {
    }
}
