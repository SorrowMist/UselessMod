package com.sorrowmist.useless.content.recipe.adapters.minecraft;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.FluidIngredientAllocator;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
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
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
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

        Converted converted = convertData(holder.value(), level);
        if (converted == null) {
            LOGGER.debug("Skipping non-static crafting recipe: {}", holder.id());
            return List.of();
        }

        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<CraftingRecipe> holder, Level level, List<ItemStack> actualInputs) {
        if (holder == null || !isSupported(holder.value())) {
            return List.of();
        }
        if (actualInputs == null || actualInputs.isEmpty()) {
            return convertAll(holder, level);
        }

        Converted converted = convertRuntimeData(holder.value(), level, actualInputs);
        if (converted == null) {
            LOGGER.debug("Skipping crafting recipe with no valid runtime result: {}", holder.id());
            return List.of();
        }

        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<RecipeHolder<CraftingRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        return findMatchingRecipes(level, mergedInputs, mergedFluids, Map.of(), mold, List.of());
    }

    @Override
    public List<RecipeHolder<CraftingRecipe>> findMatchingRecipes(
            Level level,
            Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys,
            @Nullable ItemStack mold,
            List<ItemStack> actualInputs) {
        if (level == null || !matchesMold(mold)
                || ((mergedInputs == null || mergedInputs.isEmpty())
                && (mergedFluids == null || mergedFluids.isEmpty())
                && (actualInputs == null || actualInputs.isEmpty())
                && (mold == null || mold.isEmpty()))) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CraftingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : recipeManager.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!isSupported(holder.value())) {
                continue;
            }
            if (actualInputs != null && !actualInputs.isEmpty()) {
                if (!matchesSourceInputs(holder.value(), actualInputs)) {
                    continue;
                }
                Converted converted = convertRuntimeData(holder.value(), level, actualInputs);
                if (converted != null
                        && ItemIngredientAllocator.matches(converted.itemInputs(), actualInputs, 1L)
                        && matchesFluids(mergedFluids, converted.fluidInputs())) {
                    matches.add(holder);
                }
                continue;
            }

            // Static lookup is used for recipe discovery and JEI/catalogue views. It must not
            // require a result or a deterministic output remainder: those values may only be
            // available after the real machine inputs are supplied. Keep only the source input
            // predicates here, including a bucket-to-fluid substitution when that substitution
            // is independently deterministic.
            InputRequirements requirements = staticInputRequirements(holder.value());
            if (requirements != null
                    && matchesItems(mergedInputs, requirements.itemInputs())
                    && matchesFluids(mergedFluids, requirements.fluidInputs())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static boolean isSupported(@Nullable CraftingRecipe recipe) {
        if (recipe == null) {
            return false;
        }
        if (recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe) {
            return true;
        }
        try {
            return !recipe.getIngredients().isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static AdvancedAlloyFurnaceRecipe createRecipe(
            RecipeHolder<CraftingRecipe> holder, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.itemInputs(),
                converted.fluidInputs(),
                List.of(),
                converted.outputs(),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                converted.molds(),
                AlloyFurnaceMode.NORMAL
        );
    }

    @Nullable
    private static Converted convertData(CraftingRecipe source, @Nullable Level level) {
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

        if (!usesBaseCraftingAssembly(source)
                && !hasStableAssemblyResult(source, ingredientSlots, result, level)) {
            return null;
        }

        Optional<RemainderAnalysis> analysis = analyzeRemainders(
                source, ingredientSlots, canonicalStacks);
        if (analysis.isEmpty()) {
            return null;
        }

        return convertData(source, ingredientSlots, canonicalStacks, result,
                analysis.get().remainders());
    }

    private static boolean isVanillaCraftingRecipe(CraftingRecipe source) {
        return source instanceof ShapedRecipe || source instanceof ShapelessRecipe;
    }

    /**
     * A subclass may add input-dependent behavior while still inheriting the vanilla fixed-result
     * assembly implementation. Those recipes can use the bounded per-slot remainder analysis;
     * subclasses which override assemble() must be proven with concrete combinations instead.
     */
    private static boolean usesBaseCraftingAssembly(CraftingRecipe source) {
        Class<?> baseClass = source instanceof ShapedRecipe
                ? ShapedRecipe.class
                : source instanceof ShapelessRecipe ? ShapelessRecipe.class : null;
        if (baseClass == null) {
            return false;
        }
        try {
            return source.getClass()
                    .getMethod("assemble", CraftingInput.class,
                            net.minecraft.core.HolderLookup.Provider.class)
                    .getDeclaringClass() == baseClass;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean hasStableAssemblyResult(
            CraftingRecipe source, List<Ingredient> ingredientSlots,
            ItemStack expected, @Nullable Level level) {
        Optional<List<ItemStack>> assembled =
                ExtendedCraftingAdapterUtils.deterministicRemaindersBySlot(
                        ingredientSlots,
                        Set.of(),
                        stacks -> craftingInput(source, stacks),
                        input -> {
                            ItemStack result;
                            try {
                                result = source.assemble(input,
                                        level == null ? null : level.registryAccess());
                            } catch (RuntimeException exception) {
                                return null;
                            }
                            return result == null ? null : List.of(result);
                        });
        if (assembled.isEmpty() || assembled.get().isEmpty()) {
            return false;
        }
        ItemStack actual = assembled.get().getFirst();
        return !actual.isEmpty()
                && expected.getCount() == actual.getCount()
                && ItemStack.isSameItemSameComponents(expected, actual);
    }

    private static Optional<RemainderAnalysis> analyzeRemainders(
            CraftingRecipe source, List<Ingredient> ingredientSlots,
            List<ItemStack> canonicalStacks) {
        if (isVanillaCraftingRecipe(source) && usesBaseCraftingAssembly(source)) {
            return analyzeVanillaRemainders(source, ingredientSlots, canonicalStacks);
        }

        Optional<List<ExtendedCraftingAdapterUtils.RemainderVariant>> variants =
                ExtendedCraftingAdapterUtils.remainderVariantsBySlot(
                        ingredientSlots,
                        Set.of(),
                        stacks -> craftingInput(source, stacks),
                        source::getRemainingItems);
        return variants.flatMap(values -> classifyRemainders(values, ingredientSlots));
    }

    /**
     * Vanilla crafting recipes only derive remainders from the item in the corresponding slot.
     * Probe one slot at a time so a large tag does not create a Cartesian product.
     */
    private static Optional<RemainderAnalysis> analyzeVanillaRemainders(
            CraftingRecipe source, List<Ingredient> ingredientSlots,
            List<ItemStack> canonicalStacks) {
        List<ExtendedCraftingAdapterUtils.RemainderVariant> probes = new ArrayList<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Ingredient ingredient = ingredientSlots.get(slot);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            ItemStack[] candidates;
            try {
                candidates = ingredient.getItems();
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
            if (candidates == null || candidates.length == 0) {
                candidates = new ItemStack[]{ItemStack.EMPTY};
            }

            for (ItemStack candidate : candidates) {
                if (candidate == null || (!candidate.isEmpty() && candidate.getCount() <= 0)) {
                    return Optional.empty();
                }
                List<ItemStack> inputStacks = copyStacks(canonicalStacks);
                inputStacks.set(slot, candidate.copy());
                List<ItemStack> remainder;
                try {
                    CraftingInput input = craftingInput(source, inputStacks);
                    if (input == null) {
                        return Optional.empty();
                    }
                    remainder = source.getRemainingItems(input);
                } catch (RuntimeException exception) {
                    return Optional.empty();
                }
                List<ItemStack> normalized = normalizeRemainders(remainder, ingredientSlots.size());
                if (normalized == null) {
                    return Optional.empty();
                }
                probes.add(new ExtendedCraftingAdapterUtils.RemainderVariant(
                        inputStacks, normalized));
            }
        }
        return classifyRemainders(probes, ingredientSlots);
    }

    private static Optional<RemainderAnalysis> classifyRemainders(
            List<ExtendedCraftingAdapterUtils.RemainderVariant> variants,
            List<Ingredient> ingredientSlots) {
        if (variants == null || variants.isEmpty()) {
            return Optional.empty();
        }

        List<ItemStack> representative = new ArrayList<>(ingredientSlots.size());
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            representative.add(ItemStack.EMPTY);
        }
        Set<Integer> moldSlots = new LinkedHashSet<>();
        Set<Integer> fluidSlots = new LinkedHashSet<>();

        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Ingredient ingredient = ingredientSlots.get(slot);
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            RemainderKind kind = null;
            ItemStack expectedOutput = null;
            ItemStack firstRemainder = ItemStack.EMPTY;
            for (ExtendedCraftingAdapterUtils.RemainderVariant variant : variants) {
                if (slot >= variant.inputs().size() || slot >= variant.remainders().size()) {
                    return Optional.empty();
                }
                ItemStack original = variant.inputs().get(slot);
                ItemStack remainder = variant.remainders().get(slot);
                RemainderKind current = classifyRemainder(ingredient, original, remainder);
                if (kind == null) {
                    kind = current;
                } else if (kind != current) {
                    // A tag cannot be represented by one mold/input when only some candidates
                    // are reusable. The runtime path may still resolve a concrete candidate.
                    return Optional.empty();
                }

                if (!remainder.isEmpty() && firstRemainder.isEmpty()) {
                    firstRemainder = remainder.copy();
                }
                if (current == RemainderKind.OUTPUT) {
                    if (expectedOutput == null) {
                        expectedOutput = remainder.copy();
                    } else if (!sameStack(expectedOutput, remainder)) {
                        return Optional.empty();
                    }
                }
            }

            if (kind == RemainderKind.MOLD) {
                moldSlots.add(slot);
            } else if (kind == RemainderKind.FLUID) {
                fluidSlots.add(slot);
            }
            if (kind == RemainderKind.MOLD || kind == RemainderKind.FLUID) {
                representative.set(slot, firstRemainder);
            } else if (kind == RemainderKind.OUTPUT && expectedOutput != null) {
                representative.set(slot, expectedOutput);
            }
        }
        return Optional.of(new RemainderAnalysis(
                List.copyOf(representative), Set.copyOf(moldSlots), Set.copyOf(fluidSlots)));
    }

    private static RemainderKind classifyRemainder(
            Ingredient ingredient, @Nullable ItemStack original, @Nullable ItemStack remainder) {
        if (remainder == null || remainder.isEmpty()) {
            return RemainderKind.CONSUMED;
        }
        if (fluidSubstitute(ingredient, remainder).isPresent()) {
            return RemainderKind.FLUID;
        }
        if (isReusableRemainder(original, remainder)) {
            return RemainderKind.MOLD;
        }
        return RemainderKind.OUTPUT;
    }

    @Nullable
    private static List<ItemStack> normalizeRemainders(
            @Nullable List<ItemStack> remainder, int slotCount) {
        if (remainder == null) {
            return null;
        }
        List<ItemStack> normalized = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            if (slot >= remainder.size()) {
                normalized.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack stack = remainder.get(slot);
            if (stack == null || stack.isEmpty()) {
                normalized.add(ItemStack.EMPTY);
                continue;
            }
            if (stack.getCount() <= 0) {
                return null;
            }
            normalized.add(stack.copy());
        }
        for (int slot = slotCount; slot < remainder.size(); slot++) {
            ItemStack stack = remainder.get(slot);
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                return null;
            }
        }
        return List.copyOf(normalized);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            result.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return result;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left != null && right != null
                && left.getCount() == right.getCount()
                && ItemStack.isSameItemSameComponents(left, right);
    }

    @Nullable
    private static Converted convertRuntimeData(
            CraftingRecipe source, @Nullable Level level, List<ItemStack> actualInputs) {
        List<Ingredient> ingredientSlots = ingredientSlots(source);
        if (ingredientSlots == null || ingredientSlots.isEmpty()) {
            return null;
        }

        List<ItemStack> craftingStacks = actualCraftingStacks(ingredientSlots, actualInputs);
        if (craftingStacks == null) {
            return null;
        }

        CraftingInput input = craftingInput(source, craftingStacks);
        if (input == null) {
            return null;
        }

        try {
            if (!source.matches(input, level)) {
                return null;
            }
            ItemStack result = source.assemble(input, level == null ? null : level.registryAccess());
            if (result == null || result.isEmpty() || result.getCount() <= 0) {
                return null;
            }
            List<ItemStack> remainders = source.getRemainingItems(input);
            if (remainders == null || remainders.size() != ingredientSlots.size()) {
                return null;
            }
            return convertData(source, ingredientSlots, craftingStacks, result.copy(), remainders);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Converted convertData(
            CraftingRecipe source,
            List<Ingredient> ingredientSlots,
            List<ItemStack> concreteStacks,
            ItemStack result,
            List<ItemStack> remainders) {
        if (result == null || result.isEmpty() || result.getCount() <= 0
                || remainders == null || remainders.size() != ingredientSlots.size()) {
            return null;
        }

        List<ItemStack> outputs = new ArrayList<>();
        if (!ExtendedCraftingAdapterUtils.mergeOutput(outputs, result)) {
            return null;
        }

        Set<Integer> fluidSlots = new LinkedHashSet<>();
        List<SizedFluidIngredient> fluidInputs = new ArrayList<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Optional<SizedFluidIngredient> fluid = fluidSubstitute(
                    ingredientSlots.get(slot), remainders.get(slot));
            if (fluid.isEmpty()) {
                continue;
            }
            SizedFluidIngredient stack = fluid.get();
            fluidSlots.add(slot);
            mergeFluidIngredient(fluidInputs, stack);
        }

        Map<Ingredient, Long> itemAmounts = new LinkedHashMap<>();
        List<Ingredient> remainderMolds = new ArrayList<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Ingredient ingredient = ingredientSlots.get(slot);
            ItemStack remainder = remainders.get(slot);
            if (remainder == null) {
                remainder = ItemStack.EMPTY;
            }
            if (fluidSlots.contains(slot)) {
                // The filled bucket is represented by the fluid input, while the stable empty
                // bucket remains a machine mold so the conversion does not create a free item.
                remainderMolds.add(stackIngredient(remainder));
                continue;
            }

            if (remainder.isEmpty()) {
                if (ingredient != null && !ingredient.isEmpty()) {
                    AdapterUtils.mergeIngredient(itemAmounts, ingredient, 1L);
                }
                continue;
            }

            ItemStack concrete = concreteStacks.get(slot);
            if (isReusableRemainder(concrete, remainder)) {
                // A source item which survives crafting, either unchanged or with only damage
                // consumed, is a reusable mold. Keep the original Ingredient so tags and custom
                // ingredient predicates remain available to the multiblock mold hub.
                remainderMolds.add(ingredient);
            } else {
                if (!ExtendedCraftingAdapterUtils.mergeOutput(outputs, remainder)) {
                    return null;
                }
            }

            if (!isReusableRemainder(concrete, remainder)
                    && ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(itemAmounts, ingredient, 1L);
            }
        }

        List<Ingredient> molds = new ArrayList<>();
        molds.add(AdapterUtils.toMoldIngredient(new ItemStack(Items.CRAFTING_TABLE)));
        molds.addAll(remainderMolds);
        if (itemAmounts.isEmpty() && fluidInputs.isEmpty() && remainderMolds.isEmpty()) {
            return null;
        }

        List<CountedIngredient> itemInputs = itemAmounts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new Converted(itemInputs, List.copyOf(fluidInputs), List.copyOf(outputs), List.copyOf(molds));
    }

    private static boolean matchesSourceInputs(CraftingRecipe source, List<ItemStack> actualInputs) {
        List<Ingredient> slots = ingredientSlots(source);
        return slots != null && actualCraftingStacks(slots, actualInputs) != null;
    }

    @Nullable
    private static InputRequirements staticInputRequirements(CraftingRecipe source) {
        List<Ingredient> ingredientSlots = ingredientSlots(source);
        if (ingredientSlots == null || ingredientSlots.isEmpty()) {
            return null;
        }

        List<ItemStack> canonicalStacks = canonicalStacks(ingredientSlots);
        if (canonicalStacks == null) {
            return null;
        }
        Optional<RemainderAnalysis> analysis = analyzeRemainders(
                source, ingredientSlots, canonicalStacks);
        if (analysis.isEmpty()) {
            return null;
        }
        List<ItemStack> remainders = analysis.get().remainders();

        Set<Integer> fluidSlots = analysis.get().fluidSlots();
        Set<Integer> moldSlots = analysis.get().moldSlots();
        List<SizedFluidIngredient> fluidInputs = new ArrayList<>();
        for (int slot : fluidSlots) {
            Optional<SizedFluidIngredient> fluid = fluidSubstitute(
                    ingredientSlots.get(slot), remainders.get(slot));
            if (fluid.isEmpty()) {
                return null;
            }
            mergeFluidIngredient(fluidInputs, fluid.get());
        }

        Map<Ingredient, Long> itemAmounts = new LinkedHashMap<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            if (fluidSlots.contains(slot) || moldSlots.contains(slot)) {
                continue;
            }
            Ingredient ingredient = ingredientSlots.get(slot);
            if (ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(itemAmounts, ingredient, 1L);
            }
        }

        if (itemAmounts.isEmpty() && fluidInputs.isEmpty()) {
            return null;
        }
        List<CountedIngredient> itemInputs = itemAmounts.entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
        return new InputRequirements(itemInputs, List.copyOf(fluidInputs));
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

        int[] dimensions = craftingDimensions(source, ingredients.size());
        if (dimensions == null) {
            return null;
        }
        int slots = dimensions[0] * dimensions[1];
        if (ingredients.size() > slots) {
            return null;
        }
        while (ingredients.size() < slots) {
            ingredients.add(Ingredient.EMPTY);
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
            if (candidates == null || candidates.length == 0) {
                // A tag can be temporarily unresolved while the recipe index is built. The
                // original ingredient remains the actual requirement; an empty probe is enough
                // for ordinary recipes which do not inspect the concrete input item.
                stacks.add(ItemStack.EMPTY);
                foundInput = true;
                continue;
            }
            if (candidates[0] == null || candidates[0].isEmpty()) {
                return null;
            }
            stacks.add(candidates[0].copyWithCount(1));
            foundInput = true;
        }
        return foundInput ? List.copyOf(stacks) : null;
    }

    @Nullable
    private static List<ItemStack> actualCraftingStacks(
            List<Ingredient> ingredientSlots, List<ItemStack> actualInputs) {
        if (ingredientSlots == null || actualInputs == null) {
            return null;
        }

        List<Integer> requiredSlots = new ArrayList<>();
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            Ingredient ingredient = ingredientSlots.get(slot);
            if (ingredient != null && !ingredient.isEmpty()) {
                requiredSlots.add(slot);
            }
        }
        if (requiredSlots.isEmpty()) {
            return null;
        }

        List<ItemStack> units = new ArrayList<>();
        for (ItemStack stack : actualInputs) {
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                continue;
            }
            int copies = Math.min(stack.getCount(), requiredSlots.size());
            for (int copy = 0; copy < copies; copy++) {
                units.add(stack.copyWithCount(1));
            }
        }
        if (units.size() < requiredSlots.size()) {
            return null;
        }

        List<ItemStack> assigned = new ArrayList<>(ingredientSlots.size());
        for (int slot = 0; slot < ingredientSlots.size(); slot++) {
            assigned.add(ItemStack.EMPTY);
        }
        boolean[] used = new boolean[units.size()];
        return assignCraftingStacks(ingredientSlots, requiredSlots, units, 0, used, assigned)
                ? List.copyOf(assigned)
                : null;
    }

    private static boolean assignCraftingStacks(
            List<Ingredient> ingredientSlots,
            List<Integer> requiredSlots,
            List<ItemStack> units,
            int position,
            boolean[] used,
            List<ItemStack> assigned) {
        if (position >= requiredSlots.size()) {
            return true;
        }

        int slot = requiredSlots.get(position);
        Ingredient ingredient = ingredientSlots.get(slot);
        for (int unit = 0; unit < units.size(); unit++) {
            if (used[unit]) {
                continue;
            }
            try {
                if (!ingredient.test(units.get(unit))) {
                    continue;
                }
            } catch (RuntimeException exception) {
                return false;
            }
            used[unit] = true;
            assigned.set(slot, units.get(unit));
            if (assignCraftingStacks(ingredientSlots, requiredSlots, units, position + 1, used, assigned)) {
                return true;
            }
            assigned.set(slot, ItemStack.EMPTY);
            used[unit] = false;
        }
        return false;
    }

    @Nullable
    private static CraftingInput craftingInput(CraftingRecipe source, List<ItemStack> stacks) {
        if (source == null || stacks == null) {
            return null;
        }
        int[] dimensions = craftingDimensions(source, stacks.size());
        if (dimensions == null) {
            return null;
        }
        int slotCount = dimensions[0] * dimensions[1];
        if (stacks.size() > slotCount) {
            return null;
        }
        List<ItemStack> normalized = new ArrayList<>(stacks);
        while (normalized.size() < slotCount) {
            normalized.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(dimensions[0], dimensions[1], normalized);
    }

    @Nullable
    private static int[] craftingDimensions(CraftingRecipe source, int ingredientCount) {
        if (source instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width <= 0 || height <= 0 || (long) width * height > CRAFTING_GRID_SLOTS) {
                return null;
            }
            return new int[]{width, height};
        }

        int count = Math.max(1, ingredientCount);
        int width = Math.min(CRAFTING_GRID_SIZE, count);
        int height = (count + width - 1) / width;
        return (long) width * height > CRAFTING_GRID_SLOTS ? null : new int[]{width, height};
    }

    private static Optional<SizedFluidIngredient> fluidSubstitute(
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
        if (candidates == null || candidates.length == 0) {
            return Optional.empty();
        }

        List<FluidIngredient> ingredients = new ArrayList<>();
        int amount = -1;
        for (ItemStack candidateStack : candidates) {
            if (candidateStack == null || candidateStack.isEmpty()) {
                return Optional.empty();
            }
            ItemStack candidate = candidateStack.copyWithCount(1);
            if (!(candidate.getItem() instanceof BucketItem)
                    && !(candidate.getItem() instanceof MilkBucketItem)) {
                return Optional.empty();
            }
            GenericStack contained = ContainerItemStrategies.getContainedStack(candidate, AEKeyType.fluids());
            if (contained == null || !(contained.what() instanceof AEFluidKey fluidKey)
                    || contained.amount() <= 0L || contained.amount() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            if (amount < 0) {
                amount = (int) contained.amount();
            } else if (amount != contained.amount()) {
                return Optional.empty();
            }
            FluidStack fluid = fluidKey.toStack(amount);
            FluidIngredient fluidIngredient = fluid.getComponents().isEmpty()
                    ? FluidIngredient.single(fluid)
                    : DataComponentFluidIngredient.of(true, fluid);
            if (ingredients.stream().noneMatch(fluidIngredient::equals)) {
                ingredients.add(fluidIngredient);
            }
        }
        if (ingredients.isEmpty() || amount <= 0) {
            return Optional.empty();
        }
        return Optional.of(new SizedFluidIngredient(CompoundFluidIngredient.of(ingredients), amount));
    }

    private static boolean isReusableRemainder(
            @Nullable ItemStack original, @Nullable ItemStack remainder) {
        if (original == null || original.isEmpty() || remainder == null || remainder.isEmpty()
                || original.getCount() != remainder.getCount()) {
            return false;
        }
        return ItemStack.isSameItemSameComponents(original, remainder)
                || isDurabilityRemainder(original, remainder);
    }

    private static boolean isDurabilityRemainder(
            @Nullable ItemStack original, @Nullable ItemStack remainder) {
        if (original == null || original.isEmpty() || remainder == null || remainder.isEmpty()
                || original.getCount() != remainder.getCount()
                || !original.isDamageableItem() || !remainder.isDamageableItem()
                || remainder.getDamageValue() <= original.getDamageValue()) {
            return false;
        }

        ItemStack originalWithoutDamage = original.copy();
        ItemStack remainderWithoutDamage = remainder.copy();
        originalWithoutDamage.setDamageValue(0);
        remainderWithoutDamage.setDamageValue(0);
        return ItemStack.isSameItemSameComponents(originalWithoutDamage, remainderWithoutDamage);
    }

    private static Ingredient stackIngredient(ItemStack stack) {
        return DataComponentIngredient.of(true, stack.copyWithCount(1));
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
            @Nullable Map<FluidStack, Long> available, List<SizedFluidIngredient> required) {
        if (required.isEmpty()) {
            return true;
        }
        if (available == null || available.isEmpty()) {
            return false;
        }
        return FluidIngredientAllocator.matches(required, available, 1L);
    }

    private static long saturatingAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        return left >= Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private record Converted(
            List<CountedIngredient> itemInputs,
            List<SizedFluidIngredient> fluidInputs,
            List<ItemStack> outputs,
            List<Ingredient> molds) {
    }

    private record InputRequirements(
            List<CountedIngredient> itemInputs,
            List<SizedFluidIngredient> fluidInputs) {
    }

    private enum RemainderKind {
        CONSUMED,
        MOLD,
        FLUID,
        OUTPUT
    }

    private record RemainderAnalysis(
            List<ItemStack> remainders,
            Set<Integer> moldSlots,
            Set<Integer> fluidSlots) {
    }

    private static void mergeFluidIngredient(
            List<SizedFluidIngredient> target, SizedFluidIngredient ingredient) {
        for (int i = 0; i < target.size(); i++) {
            SizedFluidIngredient existing = target.get(i);
            if (!existing.ingredient().equals(ingredient.ingredient())) {
                continue;
            }
            long amount = (long) existing.amount() + ingredient.amount();
            if (amount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Crafting fluid input amount exceeds integer range");
            }
            target.set(i, new SizedFluidIngredient(existing.ingredient(), (int) amount));
            return;
        }
        target.add(ingredient);
    }
}
