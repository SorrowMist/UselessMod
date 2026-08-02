package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.extendedcrafting.ExtendedCraftingAdapterUtils;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Converts standard shaped and shapeless crafting-table recipes. */
public final class CraftingRecipeAdapter implements IRecipeAdapter<CraftingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CRAFTING_GRID_SIZE = 3;
    private static final int CRAFTING_GRID_SLOTS = CRAFTING_GRID_SIZE * CRAFTING_GRID_SIZE;

    @Override
    public Class<CraftingRecipe> getRecipeClass() {
        return CraftingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(Items.CRAFTING_TABLE);
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty() && mold.is(Items.CRAFTING_TABLE);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CraftingRecipe> holder, Level level) {
        if (holder == null || !isSupported(holder.value())) {
            return List.of();
        }

        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.debug("Skipping non-static crafting recipe: {}", holder.id());
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.itemInputs(),
                converted.fluidInputs(),
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
                || ((mergedInputs == null || mergedInputs.isEmpty())
                && (mergedFluids == null || mergedFluids.isEmpty()))) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!isSupported(holder.value())) {
                continue;
            }
            Converted converted = convertData(holder.value());
            if (converted != null
                    && matchesItems(mergedInputs, converted.itemInputs())
                    && matchesFluids(mergedFluids, converted.fluidInputs())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean isSupported(@Nullable CraftingRecipe recipe) {
        return recipe != null && (recipe.getClass() == ShapedRecipe.class
                || recipe.getClass() == ShapelessRecipe.class);
    }

    @Nullable
    private static Converted convertData(CraftingRecipe source) {
        List<Ingredient> ingredientSlots = ingredientSlots(source);
        if (ingredientSlots == null || ingredientSlots.isEmpty()) {
            return null;
        }

        ItemStack result = ExtendedCraftingAdapterUtils.copyResult(source);
        if (result.isEmpty() || result.getCount() <= 0) {
            return null;
        }

        List<ItemStack> canonicalStacks = canonicalStacks(ingredientSlots);
        if (canonicalStacks == null) {
            return null;
        }

        List<ItemStack> canonicalRemainders;
        try {
            canonicalRemainders = source.getRemainingItems(craftingInput(source, canonicalStacks));
        } catch (RuntimeException exception) {
            return null;
        }
        if (canonicalRemainders == null || canonicalRemainders.size() != ingredientSlots.size()) {
            return null;
        }

        Set<Integer> fluidSlots = new LinkedHashSet<>();
        Map<AEFluidKey, Long> fluidAmounts = new LinkedHashMap<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Optional<GenericStack> fluid = fluidSubstitute(
                    ingredientSlots.get(slot), canonicalRemainders.get(slot));
            if (fluid.isEmpty()) {
                continue;
            }
            GenericStack stack = fluid.get();
            fluidSlots.add(slot);
            fluidAmounts.merge((AEFluidKey) stack.what(), stack.amount(), CraftingRecipeAdapter::saturatingAdd);
        }

        Map<Ingredient, Long> itemAmounts = new LinkedHashMap<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Ingredient ingredient = ingredientSlots.get(slot);
            if (!fluidSlots.contains(slot) && ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(itemAmounts, ingredient, 1L);
            }
        }
        if (itemAmounts.isEmpty() && fluidAmounts.isEmpty()) {
            return null;
        }

        Optional<List<ItemStack>> remainders = ExtendedCraftingAdapterUtils.deterministicRemainders(
                ingredientSlots,
                fluidSlots,
                stacks -> craftingInput(source, stacks),
                source::getRemainingItems);
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

        List<CountedIngredient> itemInputs = itemAmounts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        List<FluidStack> fluidInputs = new ArrayList<>(fluidAmounts.size());
        for (Map.Entry<AEFluidKey, Long> entry : fluidAmounts.entrySet()) {
            long amount = entry.getValue();
            if (amount <= 0L || amount > Integer.MAX_VALUE) {
                return null;
            }
            fluidInputs.add(entry.getKey().toStack((int) amount));
        }
        return new Converted(itemInputs, List.copyOf(fluidInputs), List.copyOf(outputs));
    }

    @Nullable
    private static List<Ingredient> ingredientSlots(CraftingRecipe source) {
        List<Ingredient> ingredients;
        try {
            ingredients = new ArrayList<>(source.getIngredients());
        } catch (RuntimeException exception) {
            return null;
        }
        if (ingredients.isEmpty() || ingredients.size() > CRAFTING_GRID_SLOTS) {
            return null;
        }
        if (source.getClass() == ShapelessRecipe.class) {
            int width = Math.min(CRAFTING_GRID_SIZE, ingredients.size());
            int height = (ingredients.size() + width - 1) / width;
            int slots = width * height;
            while (ingredients.size() < slots) {
                ingredients.add(Ingredient.EMPTY);
            }
        }
        return List.copyOf(ingredients);
    }

    @Nullable
    private static List<ItemStack> canonicalStacks(List<Ingredient> ingredients) {
        List<ItemStack> stacks = new ArrayList<>(ingredients.size());
        boolean foundInput = false;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                stacks.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack[] candidates;
            try {
                candidates = ingredient.getItems();
            } catch (RuntimeException exception) {
                return null;
            }
            if (candidates == null || candidates.length == 0
                    || candidates[0] == null || candidates[0].isEmpty()) {
                return null;
            }
            stacks.add(candidates[0].copyWithCount(1));
            foundInput = true;
        }
        return foundInput ? List.copyOf(stacks) : null;
    }

    private static CraftingInput craftingInput(CraftingRecipe source, List<ItemStack> stacks) {
        if (source.getClass() == ShapedRecipe.class) {
            ShapedRecipe shaped = (ShapedRecipe) source;
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), stacks);
        }
        int width = Math.min(CRAFTING_GRID_SIZE, stacks.size());
        int height = (stacks.size() + width - 1) / width;
        return CraftingInput.of(width, height, stacks);
    }

    private static Optional<GenericStack> fluidSubstitute(
            Ingredient ingredient, @Nullable ItemStack remainder) {
        if (ingredient == null || ingredient.isEmpty() || remainder == null
                || remainder.getCount() != 1 || !remainder.is(Items.BUCKET)) {
            return Optional.empty();
        }

        ItemStack[] candidates;
        try {
            candidates = ingredient.getItems();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (candidates == null || candidates.length != 1
                || candidates[0] == null || candidates[0].isEmpty()) {
            return Optional.empty();
        }

        ItemStack candidate = candidates[0].copyWithCount(1);
        if (!(candidate.getItem() instanceof BucketItem)
                && !(candidate.getItem() instanceof MilkBucketItem)) {
            return Optional.empty();
        }
        GenericStack contained = ContainerItemStrategies.getContainedStack(candidate, AEKeyType.fluids());
        return contained != null && contained.what() instanceof AEFluidKey && contained.amount() > 0L
                ? Optional.of(contained)
                : Optional.empty();
    }

    private static boolean matchesItems(
            @Nullable Map<Ingredient, Long> available, List<CountedIngredient> required) {
        if (required.isEmpty()) {
            return true;
        }
        if (available == null || available.isEmpty()) {
            return false;
        }
        Map<Ingredient, Long> requiredCounts = new LinkedHashMap<>();
        for (CountedIngredient ingredient : required) {
            AdapterUtils.mergeIngredient(requiredCounts, ingredient.ingredient(), ingredient.count());
        }
        return AdapterUtils.matchesRequired(available, requiredCounts);
    }

    private static boolean matchesFluids(
            @Nullable Map<FluidStack, Long> available, List<FluidStack> required) {
        if (required.isEmpty()) {
            return true;
        }
        if (available == null || available.isEmpty()) {
            return false;
        }
        for (FluidStack requirement : required) {
            long found = 0L;
            for (Map.Entry<FluidStack, Long> entry : available.entrySet()) {
                if (FluidStack.isSameFluidSameComponents(requirement, entry.getKey())) {
                    found = saturatingAdd(found, Math.max(0L, entry.getValue()));
                }
            }
            if (found < requirement.getAmount()) {
                return false;
            }
        }
        return true;
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record Converted(
            List<CountedIngredient> itemInputs,
            List<FluidStack> fluidInputs,
            List<ItemStack> outputs) {
    }
}
