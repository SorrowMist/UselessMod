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
                // Some custom KJS ingredients intentionally expose no display
                // candidates. They can still be converted when the source
                // recipe reports no reusable remainder for the empty probe.
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

        List<List<ItemStack>> variants = new ArrayList<>();
        if (combinations <= MAX_REMAINDER_VARIANTS) {
            buildVariants(candidates, 0, new ArrayList<>(), variants);
        } else {
            // Large tags are common. Check the canonical input and each one-slot
            // substitution without allocating a cartesian product.
            List<ItemStack> canonical = canonicalVariant(candidates);
            variants.add(canonical);
            for (int slot = 0; slot < candidates.size(); slot++) {
                List<ItemStack> values = candidates.get(slot);
                for (ItemStack value : values) {
                    List<ItemStack> variant = copyStacks(canonical);
                    variant.set(slot, value.copy());
                    variants.add(variant);
                    if (variants.size() >= MAX_REMAINDER_VARIANTS) {
                        break;
                    }
                }
                if (variants.size() >= MAX_REMAINDER_VARIANTS) {
                    break;
                }
            }
        }

        List<ItemStack> expected = null;
        for (List<ItemStack> variant : variants) {
            List<ItemStack> remainder;
            try {
                I input = inputFactory.apply(copyStacks(variant));
                remainder = resolver.apply(input);
            } catch (RuntimeException exception) {
                return Optional.empty();
            }
            Optional<List<ItemStack>> normalizedResult = normalizeRemainder(remainder, ignored);
            if (normalizedResult.isEmpty()) {
                return Optional.empty();
            }
            List<ItemStack> normalized = normalizedResult.get();
            if (expected == null) {
                expected = normalized;
            } else if (!sameStacks(expected, normalized)) {
                return Optional.empty();
            }
        }
        return Optional.of(expected == null ? List.of() : expected);
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

    private static List<ItemStack> canonicalVariant(List<List<ItemStack>> candidates) {
        List<ItemStack> result = new ArrayList<>(candidates.size());
        for (List<ItemStack> values : candidates) {
            result.add(values.isEmpty() ? ItemStack.EMPTY : values.getFirst().copy());
        }
        return result;
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
