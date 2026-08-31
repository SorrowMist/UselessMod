package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.delight.DelightRecipeAdapterUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

abstract class AbstractCookeryRecipeAdapter<T extends Recipe<?>> implements IRecipeAdapter<T> {
    private static final int BASE_PROCESS_TIME = 200;

    protected abstract ResourceLocation moldId();

    protected abstract AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, T source, Level level);

    @Override
    public final String sourceId() {
        return RecipeSourceIds.KALEIDOSCOPE_COOKERY;
    }

    @Override
    public ItemStack getMoldItem() {
        Item item = BuiltInRegistries.ITEM.getOptional(moldId()).orElse(null);
        return item == null ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    @Override
    public final List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<T> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        AdvancedAlloyFurnaceRecipe converted = convertSource(holder.id(), holder.value(), level);
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public final List<RecipeHolder<T>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public final List<RecipeHolder<T>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold, List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)
                || (mergedKeys != null && !mergedKeys.isEmpty())) {
            return List.of();
        }

        List<RecipeHolder<T>> matches = new ArrayList<>();
        for (RecipeHolder<T> holder : DelightRecipeAdapterUtils.allOf(
                level.getRecipeManager(), getRecipeClass())) {
            AdvancedAlloyFurnaceRecipe converted = convertSource(
                    holder.id(), holder.value(), level);
            if (converted != null
                    && DelightRecipeAdapterUtils.matchesItems(
                    converted.inputs(), mergedInputs, actualInputs)
                    && DelightRecipeAdapterUtils.matchesFluids(
                    converted.inputFluids(), mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    protected List<Ingredient> moldIngredients() {
        Ingredient mold = AdapterUtils.toMoldIngredient(getMoldItem());
        return mold.isEmpty() ? List.of() : List.of(mold);
    }

    protected AdvancedAlloyFurnaceRecipe createItemRecipe(
            ResourceLocation recipeId, List<Ingredient> ingredients, Ingredient carrier,
            ItemStack output, int processTime) {
        if (output == null || output.isEmpty() || output.getCount() <= 0) {
            return null;
        }

        List<CountedIngredient> inputs = countedInputs(
                ingredients, carrier, output.getCount());
        if (inputs.isEmpty()) {
            return null;
        }

        int time = Math.max(1, processTime);
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                energyFor(time),
                time,
                Ingredient.EMPTY,
                0,
                moldIngredients(),
                AlloyFurnaceMode.NORMAL
        );
    }

    protected AdvancedAlloyFurnaceRecipe createItemRecipe(
            ResourceLocation recipeId, List<Ingredient> ingredients, ItemStack output,
            int processTime) {
        return createItemRecipe(recipeId, ingredients, Ingredient.EMPTY, output, processTime);
    }

    protected AdvancedAlloyFurnaceRecipe createMultiOutputItemRecipe(
            ResourceLocation recipeId, List<Ingredient> ingredients,
            List<ItemStack> outputs, int processTime) {
        if (outputs == null || outputs.isEmpty()) {
            return null;
        }

        List<ItemStack> normalizedOutputs = outputs.stream()
                .filter(stack -> stack != null && !stack.isEmpty() && stack.getCount() > 0)
                .map(ItemStack::copy)
                .toList();
        if (normalizedOutputs.isEmpty()) {
            return null;
        }

        List<CountedIngredient> inputs = countedInputs(ingredients, Ingredient.EMPTY, 0);
        if (inputs.isEmpty()) {
            return null;
        }

        int time = Math.max(1, processTime);
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                inputs,
                List.of(),
                List.of(),
                normalizedOutputs,
                List.of(),
                List.of(),
                energyFor(time),
                time,
                Ingredient.EMPTY,
                0,
                moldIngredients(),
                AlloyFurnaceMode.NORMAL
        );
    }

    protected AdvancedAlloyFurnaceRecipe createStockpotRecipe(
            ResourceLocation recipeId, List<Ingredient> ingredients, ResourceLocation soupBase,
            ItemStack output, int processTime, Ingredient carrier) {
        if (output == null || output.isEmpty() || output.getCount() <= 0
                || soupBase == null) {
            return null;
        }

        List<Ingredient> itemIngredients = new ArrayList<>();
        if (ingredients != null) {
            itemIngredients.addAll(ingredients);
        }

        List<LongSizedFluidIngredient> inputFluids = new ArrayList<>();
        var fluid = BuiltInRegistries.FLUID.getOptional(soupBase).orElse(null);
        if (fluid != null && fluid != net.minecraft.world.level.material.Fluids.EMPTY) {
            inputFluids.add(new LongSizedFluidIngredient(
                    net.neoforged.neoforge.fluids.crafting.FluidIngredient.single(
                            new FluidStack(fluid, 1_000)),
                    1_000));
        } else {
            Item item = BuiltInRegistries.ITEM.getOptional(soupBase).orElse(null);
            if (item != null) {
                itemIngredients.add(Ingredient.of(item));
            }
        }

        List<CountedIngredient> inputs = countedInputs(
                itemIngredients, carrier, output.getCount());
        if (inputs.isEmpty() && inputFluids.isEmpty()) {
            return null;
        }

        int time = Math.max(1, processTime);
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                inputs,
                inputFluids,
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                energyFor(time),
                time,
                Ingredient.EMPTY,
                0,
                moldIngredients(),
                AlloyFurnaceMode.NORMAL
        );
    }

    protected static List<CountedIngredient> countedInputs(
            List<Ingredient> ingredients, Ingredient carrier, long carrierCount) {
        Map<Ingredient, Long> counts = new LinkedHashMap<>();
        if (ingredients != null) {
            for (Ingredient ingredient : ingredients) {
                if (!AdapterUtils.isIngredientEmpty(ingredient)) {
                    AdapterUtils.mergeIngredient(counts, ingredient, 1L);
                }
            }
        }
        if (!AdapterUtils.isIngredientEmpty(carrier) && carrierCount > 0) {
            AdapterUtils.mergeIngredient(counts, carrier, carrierCount);
        }
        return counts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }

    protected static long energyFor(int processTime) {
        return Math.max(1L, (long) processTime * AdapterUtils.DEFAULT_ENERGY / BASE_PROCESS_TIME);
    }
}
