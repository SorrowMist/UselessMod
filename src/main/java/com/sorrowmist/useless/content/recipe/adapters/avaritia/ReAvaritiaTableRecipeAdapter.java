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
import committee.nova.mods.avaritia.common.ingredient.StackIngredient;
import committee.nova.mods.avaritia.init.registry.ModBlocks;
import committee.nova.mods.avaritia.init.registry.ModRecipeTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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

        // Ingredient.equals() does not include item components. Re-Avaritia represents
        // singularities as the same item with a different SINGULARITY_ID component, so a normal
        // equality-based map would collapse all of them into one requirement. The identity map is
        // only the storage layer; equal exact stacks are still merged explicitly below.
        Map<Ingredient, Long> requirements = new IdentityHashMap<>();
        if (!mergeTableIngredients(requirements, ingredients)) {
            return null;
        }
        List<CountedIngredient> inputs = ExtendedCraftingAdapterUtils.countedIngredients(requirements);
        if (inputs.isEmpty() || requirements.isEmpty()) {
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
                inputs,
                outputs,
                requirements,
                tableMold(tier));
    }

    private static boolean mergeTableIngredients(
            Map<Ingredient, Long> target, List<Ingredient> ingredients) {
        if (target == null || ingredients == null) {
            return false;
        }

        boolean found = false;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            if (!mergeExactStackIngredient(target, ingredient)) {
                mergeNonExactIngredient(target, ingredient);
            }
            found = true;
        }
        return found;
    }

    private static void mergeNonExactIngredient(
            Map<Ingredient, Long> target, Ingredient ingredient) {
        for (Map.Entry<Ingredient, Long> entry : target.entrySet()) {
            if (AdapterUtils.areIngredientsEqual(entry.getKey(), ingredient)) {
                entry.setValue(entry.getValue() + 1L);
                return;
            }
        }
        target.put(ingredient, 1L);
    }

    /**
     * Aggregates ingredients whose semantics identify one exact item stack. Ingredient equality
     * ignores item components for vanilla item values, while Re-Avaritia's StackIngredient is
     * custom and therefore intentionally opaque to the generic equality helper. Comparing the
     * canonical stack makes both representations follow the same rule without treating tags or
     * arbitrary custom display candidates as exact identities.
     */
    private static boolean mergeExactStackIngredient(
            Map<Ingredient, Long> target, Ingredient ingredient) {
        Optional<ItemStack> currentStack = exactStack(ingredient);
        if (currentStack.isEmpty()) {
            return false;
        }

        for (Map.Entry<Ingredient, Long> entry : target.entrySet()) {
            Optional<ItemStack> existingStack = exactStack(entry.getKey());
            if (existingStack.isEmpty()) {
                continue;
            }
            if (ItemStack.isSameItemSameComponents(existingStack.get(), currentStack.get())) {
                entry.setValue(entry.getValue() + 1L);
                return true;
            }
        }

        target.put(ingredient, 1L);
        return true;
    }

    private static Optional<ItemStack> exactStack(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return Optional.empty();
        }

        if (ingredient.getCustomIngredient() instanceof StackIngredient) {
            return singleCandidate(ingredient);
        }
        if (ingredient.getCustomIngredient() instanceof DataComponentIngredient dataComponent) {
            return dataComponent.isStrict() ? singleCandidate(ingredient) : Optional.empty();
        }
        if (ingredient.isCustom()) {
            return Optional.empty();
        }

        try {
            Ingredient.Value[] values = ingredient.getValues();
            if (values.length != 1 || !(values[0] instanceof Ingredient.ItemValue)) {
                return Optional.empty();
            }
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        return singleCandidate(ingredient);
    }

    private static Optional<ItemStack> singleCandidate(@Nullable Ingredient ingredient) {
        if (ingredient == null) {
            return Optional.empty();
        }
        try {
            ItemStack[] candidates = ingredient.getItems();
            if (candidates.length != 1 || candidates[0] == null || candidates[0].isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(candidates[0].copyWithCount(1));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
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
