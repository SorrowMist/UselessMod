package com.sorrowmist.useless.content.recipe.adapters.extendedcrafting;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

/** Shared, defensive conversion helpers for Extended Crafting recipes. */
public final class ExtendedCraftingAdapterUtils {
    private static final int MAX_REMAINDER_VARIANTS = 512;

    private ExtendedCraftingAdapterUtils() {
    }

    /** Adds every non-empty source ingredient, retaining component/custom ingredient identity. */
    public static boolean mergeIngredients(
            Map<Ingredient, Long> target, Iterable<? extends Ingredient> ingredients) {
        if (target == null || ingredients == null) {
            return false;
        }
        boolean found = false;
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            AdapterUtils.mergeIngredient(target, ingredient, 1L);
            found = true;
        }
        return found;
    }

    public static List<CountedIngredient> countedIngredients(Map<Ingredient, Long> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        List<CountedIngredient> result = new ArrayList<>();
        for (Map.Entry<Ingredient, Long> entry : ingredients.entrySet()) {
            Ingredient ingredient = entry.getKey();
            long count = entry.getValue() == null ? 0L : entry.getValue();
            if (ingredient == null || ingredient.isEmpty() || count <= 0L) {
                continue;
            }
            result.add(new CountedIngredient(ingredient, count));
        }
        return List.copyOf(result);
    }

    /** Returns a canonical stack only when every candidate is exactly the same stack. */
    public static Optional<ItemStack> deterministicStack(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
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

        ItemStack first = null;
        for (ItemStack candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || candidate.getCount() <= 0) {
                return Optional.empty();
            }
            if (first == null) {
                first = candidate.copy();
            } else if (!ItemStack.isSameItemSameComponents(first, candidate)
                    || first.getCount() != candidate.getCount()) {
                return Optional.empty();
            }
        }
        return first == null ? Optional.empty() : Optional.of(first);
    }

    public static ItemStack copyResult(@Nullable Recipe<?> recipe) {
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack result = recipe.getResultItem(null);
            return result == null ? ItemStack.EMPTY : result.copy();
        } catch (RuntimeException exception) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Finds remainders which are invariant for all displayed ingredient candidates.
     *
     * <p>The resolver is normally {@code Recipe#getRemainingItems}. A recipe with a
     * component-sensitive or dynamic remainder is rejected rather than creating a
     * free or incorrect output.</p>
     *
     * @param ingredientSlots input slots in the same order used by the resolver
     * @param ignoredSlots slots whose remainder is intentionally not copied
     * @param resolver creates the source recipe input and returns its remaining items
     */
    public static <I extends RecipeInput> Optional<List<ItemStack>> deterministicRemainders(
            List<Ingredient> ingredientSlots,
            Set<Integer> ignoredSlots,
            Function<List<ItemStack>, I> inputFactory,
            Function<I, List<ItemStack>> resolver) {
        return deterministicRemaindersInternal(
                ingredientSlots, ignoredSlots, inputFactory, resolver, false);
    }

    /**
     * Finds remainders which are invariant for all displayed ingredient candidates and keeps
     * one result for each source slot. Empty slots and ignored slots are represented by an empty
     * stack. Unlike {@link #deterministicRemainders(List, Set, Function, Function)}, this method
     * does not merge equal remainders because callers may need to retain their source-slot
     * identity (for example, each crafting remainder is an independent mold requirement).
     */
    public static <I extends RecipeInput> Optional<List<ItemStack>> deterministicRemaindersBySlot(
            List<Ingredient> ingredientSlots,
            Set<Integer> ignoredSlots,
            Function<List<ItemStack>, I> inputFactory,
            Function<I, List<ItemStack>> resolver) {
        return deterministicRemaindersInternal(
                ingredientSlots, ignoredSlots, inputFactory, resolver, true);
    }

    /**
     * Resolves every bounded ingredient candidate combination and keeps the source input beside
     * its normalized slot remainders. Callers which have semantic equivalence rules (for example,
     * reusable tools whose damage changes) can classify the variants without treating the exact
     * remainder stack as the identity of the recipe.
     */
    public static <I extends RecipeInput> Optional<List<RemainderVariant>> remainderVariantsBySlot(
            List<Ingredient> ingredientSlots,
            Set<Integer> ignoredSlots,
            Function<List<ItemStack>, I> inputFactory,
            Function<I, List<ItemStack>> resolver) {
        if (ingredientSlots == null || inputFactory == null || resolver == null) {
            return Optional.empty();
        }

        Set<Integer> ignored = ignoredSlots == null ? Set.of() : new HashSet<>(ignoredSlots);
        List<List<ItemStack>> candidates = new ArrayList<>(ingredientSlots.size());
        long combinations = 1L;
        for (Ingredient ingredient : ingredientSlots) {
            if (ingredient == null || ingredient.isEmpty()) {
                candidates.add(List.of(ItemStack.EMPTY));
                continue;
            }
            ItemStack[] values;
            try {
                values = ingredient.getItems();
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
            if (values == null || values.length == 0) {
                // Preserve the unresolved-tag behavior used by the existing conversion path.
                candidates.add(List.of(ItemStack.EMPTY));
                continue;
            }
            List<ItemStack> safeValues = new ArrayList<>(values.length);
            for (ItemStack value : values) {
                if (value == null || value.isEmpty() || value.getCount() <= 0) {
                    return Optional.empty();
                }
                safeValues.add(value.copy());
            }
            candidates.add(List.copyOf(safeValues));
            if (combinations <= MAX_REMAINDER_VARIANTS) {
                combinations = Math.min(
                        MAX_REMAINDER_VARIANTS + 1L,
                        combinations * Math.max(1, safeValues.size()));
            }
        }

        if (combinations > MAX_REMAINDER_VARIANTS) {
            return Optional.empty();
        }

        List<List<ItemStack>> variants = new ArrayList<>();
        buildVariants(candidates, 0, new ArrayList<>(), variants);
        List<RemainderVariant> result = new ArrayList<>(variants.size());
        for (List<ItemStack> variant : variants) {
            List<ItemStack> remainder;
            try {
                I input = inputFactory.apply(copyStacks(variant));
                remainder = resolver.apply(input);
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
            Optional<List<ItemStack>> normalized = normalizeRemainderBySlot(
                    remainder, ingredientSlots.size(), ignored);
            if (normalized.isEmpty()) {
                return Optional.empty();
            }
            result.add(new RemainderVariant(copyStacks(variant), normalized.get()));
        }
        return Optional.of(List.copyOf(result));
    }

    private static <I extends RecipeInput> Optional<List<ItemStack>> deterministicRemaindersInternal(
            List<Ingredient> ingredientSlots,
            Set<Integer> ignoredSlots,
            Function<List<ItemStack>, I> inputFactory,
            Function<I, List<ItemStack>> resolver,
            boolean preserveSlots) {
        Optional<List<RemainderVariant>> variants = remainderVariantsBySlot(
                ingredientSlots, ignoredSlots, inputFactory, resolver);
        if (variants.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> ignored = ignoredSlots == null ? Set.of() : new HashSet<>(ignoredSlots);
        List<ItemStack> expected = null;
        for (RemainderVariant variant : variants.get()) {
            List<ItemStack> normalized = preserveSlots
                    ? variant.remainders()
                    : normalizeRemainder(variant.remainders(), ignored).orElse(null);
            if (normalized == null) {
                return Optional.empty();
            }
            if (expected == null) {
                expected = normalized;
            } else if (preserveSlots
                    ? !sameStacksBySlot(expected, normalized)
                    : !sameStacks(expected, normalized)) {
                return Optional.empty();
            }
        }
        return Optional.of(expected == null ? List.of() : expected);
    }

    public record RemainderVariant(List<ItemStack> inputs, List<ItemStack> remainders) {
        public RemainderVariant {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            remainders = remainders == null ? List.of() : List.copyOf(remainders);
        }
    }

    private static void buildVariants(
            List<List<ItemStack>> candidates, int slot, List<ItemStack> current,
            List<List<ItemStack>> result) {
        if (result.size() >= MAX_REMAINDER_VARIANTS) {
            return;
        }
        if (slot >= candidates.size()) {
            result.add(copyStacks(current));
            return;
        }
        for (ItemStack candidate : candidates.get(slot)) {
            current.add(candidate);
            buildVariants(candidates, slot + 1, current, result);
            current.removeLast();
            if (result.size() >= MAX_REMAINDER_VARIANTS) {
                return;
            }
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            result.add(stack == null ? ItemStack.EMPTY : stack.copy());
        }
        return result;
    }

    private static Optional<List<ItemStack>> normalizeRemainder(
            @Nullable List<ItemStack> remainder, Set<Integer> ignoredSlots) {
        List<ItemStack> result = new ArrayList<>();
        if (remainder == null) {
            return Optional.empty();
        }
        for (int index = 0; index < remainder.size(); index++) {
            if (ignoredSlots.contains(index)) {
                continue;
            }
            ItemStack stack = remainder.get(index);
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
                continue;
            }
            if (!mergeOutput(result, stack)) {
                return Optional.empty();
            }
        }
        return Optional.of(result);
    }

    private static Optional<List<ItemStack>> normalizeRemainderBySlot(
            @Nullable List<ItemStack> remainder, int slotCount, Set<Integer> ignoredSlots) {
        if (remainder == null) {
            return Optional.empty();
        }

        List<ItemStack> result = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            if (ignoredSlots.contains(slot) || slot >= remainder.size()) {
                result.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack stack = remainder.get(slot);
            if (stack == null || stack.isEmpty()) {
                result.add(ItemStack.EMPTY);
                continue;
            }
            if (stack.getCount() <= 0) {
                return Optional.empty();
            }
            result.add(stack.copy());
        }

        // A resolver should return one remainder entry per source slot. Preserve the old
        // aggregate helper's permissive handling of trailing empty entries, but reject a
        // non-empty remainder that cannot be assigned to a source slot.
        for (int slot = slotCount; slot < remainder.size(); slot++) {
            ItemStack stack = remainder.get(slot);
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                return Optional.empty();
            }
        }
        return Optional.of(List.copyOf(result));
    }

    private static boolean sameStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        boolean[] used = new boolean[right.size()];
        for (ItemStack expected : left) {
            boolean found = false;
            for (int index = 0; index < right.size(); index++) {
                ItemStack actual = right.get(index);
                if (!used[index]
                        && expected.getCount() == actual.getCount()
                        && ItemStack.isSameItemSameComponents(expected, actual)) {
                    used[index] = true;
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameStacksBySlot(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int slot = 0; slot < left.size(); slot++) {
            ItemStack expected = left.get(slot);
            ItemStack actual = right.get(slot);
            if (expected.getCount() != actual.getCount()
                    || !ItemStack.isSameItemSameComponents(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    /** Merges equal component-aware output stacks and rejects an int overflow. */
    public static boolean mergeOutput(List<ItemStack> outputs, ItemStack output) {
        if (outputs == null || output == null || output.isEmpty() || output.getCount() <= 0) {
            return false;
        }
        for (ItemStack existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, output)) {
                continue;
            }
            long merged = (long) existing.getCount() + output.getCount();
            if (merged > Integer.MAX_VALUE) {
                return false;
            }
            existing.setCount((int) merged);
            return true;
        }
        outputs.add(output.copy());
        return true;
    }

    /** Converts a powered recipe's total cost/rate pair to positive furnace ticks. */
    public static OptionalInt powerProcessTime(long cost, long rate) {
        if (cost < 0L) {
            return OptionalInt.empty();
        }
        if (cost == 0L) {
            return OptionalInt.of(1);
        }
        if (rate <= 0L) {
            return OptionalInt.empty();
        }
        long ticks = cost / rate;
        if (cost % rate != 0L) {
            ticks++;
        }
        return OptionalInt.of(safePositiveTicks(ticks));
    }

    /** Converts source seconds to ticks using long arithmetic and an int upper bound. */
    public static OptionalInt secondsToTicks(long seconds) {
        if (seconds < 0L) {
            return OptionalInt.empty();
        }
        if (seconds == 0L) {
            return OptionalInt.of(1);
        }
        long ticks;
        if (seconds > Long.MAX_VALUE / 20L) {
            ticks = Long.MAX_VALUE;
        } else {
            ticks = seconds * 20L;
        }
        return OptionalInt.of(safePositiveTicks(ticks));
    }

    public static int safePositiveTicks(long ticks) {
        if (ticks <= 0L) {
            return 1;
        }
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    /** Picks a compact grid for synthetic shaped/shapeless remainder inputs. */
    public static int[] gridDimensions(int slots) {
        int count = Math.max(1, slots);
        int width = (int) Math.ceil(Math.sqrt(count));
        while (width > 1 && count % width != 0) {
            width--;
        }
        int height = (count + width - 1) / width;
        return new int[]{Math.max(1, width), Math.max(1, height)};
    }

    /** Uses shaped recipe dimensions when the source exposes getWidth/getHeight. */
    public static int[] gridDimensions(@Nullable Object recipe, int slots) {
        if (recipe != null) {
            try {
                Method widthMethod = recipe.getClass().getMethod("getWidth");
                Method heightMethod = recipe.getClass().getMethod("getHeight");
                Object widthValue = widthMethod.invoke(recipe);
                Object heightValue = heightMethod.invoke(recipe);
                if (widthValue instanceof Number widthNumber && heightValue instanceof Number heightNumber) {
                    int width = widthNumber.intValue();
                    int height = heightNumber.intValue();
                    if (width > 0 && height > 0 && (long) width * height == Math.max(1, slots)) {
                        return new int[]{width, height};
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Shapeless and third-party recipe implementations need no explicit dimensions.
            }
        }
        return gridDimensions(slots);
    }
}
