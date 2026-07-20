package com.sorrowmist.useless.content.recipe.adapters.extendedcrafting;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.blakebr0.extendedcrafting.api.TableCraftingInput;
import com.blakebr0.extendedcrafting.api.crafting.ITableRecipe;
import com.blakebr0.extendedcrafting.init.ModBlocks;
import com.blakebr0.extendedcrafting.init.ModRecipeTypes;
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

/** Converts the four normal Extended Crafting tables (tier 1-4). */
public class ExtendedCraftingTableRecipeAdapter implements IRecipeAdapter<ITableRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<ITableRecipe> getRecipeClass() {
        return ITableRecipe.class;
    }

    /** A table adapter serves four possible molds, so it uses the fallback registry. */
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
        return isTableItem(mold, 1) || isTableItem(mold, 2)
                || isTableItem(mold, 3) || isTableItem(mold, 4);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ITableRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Extended Crafting table recipe: {}", holder.id());
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
    public List<RecipeHolder<ITableRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<ITableRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ITableRecipe> holder : recipeManager.getAllRecipesFor(ModRecipeTypes.TABLE.get())) {
            Converted converted = convertData(holder.value());
            if (converted != null && converted.mold().getItem() == mold.getItem()
                    && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable ITableRecipe source) {
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

        List<Ingredient> ingredients = source.getIngredients();
        if (ingredients == null || ingredients.isEmpty()
                || ingredients.size() > tableCapacity(tier)) {
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

        Optional<List<ItemStack>> remainders = ExtendedCraftingAdapterUtils.deterministicRemainders(
                ingredients,
                Set.of(),
                stacks -> {
                    int[] dimensions = ExtendedCraftingAdapterUtils.gridDimensions(source, stacks.size());
                    return TableCraftingInput.of(dimensions[0], dimensions[1], stacks, tier);
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
        return new Converted(counted, outputs, requirements, tableMold(tier));
    }

    private static ItemStack tableMold(int tier) {
        return switch (tier) {
            case 1 -> new ItemStack(ModBlocks.BASIC_TABLE.get());
            case 2 -> new ItemStack(ModBlocks.ADVANCED_TABLE.get());
            case 3 -> new ItemStack(ModBlocks.ELITE_TABLE.get());
            case 4 -> new ItemStack(ModBlocks.ULTIMATE_TABLE.get());
            default -> ItemStack.EMPTY;
        };
    }

    private static int tableCapacity(int tier) {
        return switch (tier) {
            case 1 -> 9;
            case 2 -> 25;
            case 3 -> 49;
            case 4 -> 81;
            default -> 0;
        };
    }

    private static boolean isTableItem(ItemStack stack, int tier) {
        ItemStack mold = tableMold(tier);
        return !mold.isEmpty() && stack.getItem() == mold.getItem();
    }

    private record Converted(
            List<CountedIngredient> inputs,
            List<ItemStack> outputs,
            Map<Ingredient, Long> requirements,
            ItemStack mold) {
    }
}
