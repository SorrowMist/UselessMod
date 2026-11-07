package com.sorrowmist.useless.content.recipe.adapters.delight;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared lookup and container handling for the Delight family of recipe adapters. */
public final class DelightRecipeAdapterUtils {
    private static final TagKey<Item> EXTRA_DELIGHT_BAKING_TRAYS = TagKey.create(
            Registries.ITEM,
            new ResourceLocation("extradelight", "baking_trays"));

    private DelightRecipeAdapterUtils() {
    }

    public static <T extends Recipe<?>> List<RecipeHolder<T>> allOf(
            RecipeManager recipeManager, Class<T> recipeClass) {
        if (recipeManager == null || recipeClass == null) {
            return List.of();
        }

        List<RecipeHolder<T>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.getRecipes(recipeManager)) {
            if (recipeClass.isInstance(holder.value())) {
                result.add(new RecipeHolder<>(holder.id(), recipeClass.cast(holder.value())));
            }
        }
        return List.copyOf(result);
    }

    /** Concrete stacks must not pass through vanilla Ingredient.of, which discards NBT. */
    public static Ingredient exactStack(ItemStack stack) {
        return stack == null || stack.isEmpty() ? Ingredient.EMPTY
                : com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient.of(true, stack);
    }

    /** Keep container counts as longs instead of expanding one Ingredient per serving. */
    public static List<CountedIngredient> withContainerInputs(
            List<Ingredient> ingredients, @Nullable ItemStack container) {
        return withContainerInputs(ingredients, container, !isBakingTray(container));
    }

    public static List<CountedIngredient> withContainerInputs(
            List<Ingredient> ingredients, @Nullable ItemStack container, boolean consume) {
        List<CountedIngredient> inputs = new ArrayList<>(AdapterUtils.mergeIngredients(ingredients));
        if (consume && container != null && !container.isEmpty() && container.getCount() > 0) {
            inputs.add(new CountedIngredient(exactStack(container), (long) container.getCount()));
        }
        return List.copyOf(inputs);
    }

    /** Farmer's Delight stores one container per output serving, separately from ingredients. */
    public static List<CountedIngredient> cookingInputs(CookingPotRecipe recipe) {
        if (recipe == null) return List.of();
        List<CountedIngredient> inputs = new ArrayList<>(AdapterUtils.mergeIngredients(recipe.getIngredients()));
        ItemStack container = recipe.getOutputContainer();
        ItemStack result = recipe.getResultItem(null);
        if (!isBakingTray(container) && container != null && !container.isEmpty()
                && result != null && !result.isEmpty()) {
            long count = Math.multiplyExact((long) container.getCount(), (long) result.getCount());
            if (count > 0) inputs.add(new CountedIngredient(exactStack(container), count));
        }
        return List.copyOf(inputs);
    }

    public static boolean isBakingTray(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(EXTRA_DELIGHT_BAKING_TRAYS);
    }

    public static List<Ingredient> bakingTrayMolds(@Nullable ItemStack stack) {
        if (!isBakingTray(stack) || stack.getCount() <= 0) {
            return List.of();
        }
        List<Ingredient> molds = new ArrayList<>(stack.getCount());
        for (int i = 0; i < stack.getCount(); i++) {
            molds.add(exactStack(stack));
        }
        return List.copyOf(molds);
    }

    public static Map<Ingredient, Long> requirements(List<CountedIngredient> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return Map.of();
        }

        Map<Ingredient, Long> result = new LinkedHashMap<>();
        for (CountedIngredient input : inputs) {
            if (input == null || AdapterUtils.isIngredientEmpty(input.ingredient())
                    || input.count() <= 0) {
                continue;
            }
            AdapterUtils.mergeIngredient(result, input.ingredient(), input.count());
        }
        return result;
    }

    public static boolean hasConcreteInputs(@Nullable List<ItemStack> inputs) {
        return inputs != null && inputs.stream().anyMatch(stack ->
                stack != null && !stack.isEmpty() && stack.getCount() > 0);
    }

    public static boolean matchesItems(List<CountedIngredient> requirements,
                                       @Nullable Map<Ingredient, Long> mergedInputs,
                                       @Nullable List<ItemStack> actualInputs) {
        boolean hasActualInputs = hasConcreteInputs(actualInputs);
        boolean hasMergedInputs = mergedInputs != null && !mergedInputs.isEmpty();
        if (requirements == null || requirements.isEmpty()) {
            return !hasActualInputs && !hasMergedInputs;
        }
        if (hasActualInputs) {
            return com.sorrowmist.useless.content.recipe.ItemIngredientAllocator.matches(
                    requirements, actualInputs, 1L);
        }
        return hasMergedInputs && AdapterUtils.matchesRequired(
                mergedInputs, requirements(requirements));
    }

    public static boolean matchesFluids(
            List<com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient> requirements,
            @Nullable Map<net.minecraftforge.fluids.FluidStack, Long> mergedFluids) {
        boolean hasFluids = mergedFluids != null && !mergedFluids.isEmpty();
        if (requirements == null || requirements.isEmpty()) {
            return !hasFluids;
        }
        return hasFluids && com.sorrowmist.useless.content.recipe.FluidIngredientAllocator
                .matchesLong(requirements, mergedFluids, 1L);
    }

    @Nullable
    public static Item registeredItem(ResourceLocation id) {
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    /** Reads an external recipe field without linking the adapter to its implementation classes. */
    @Nullable
    public static Object fieldValue(@Nullable Object source, String name) {
        if (source == null || name == null || name.isEmpty()) {
            return null;
        }

        Class<?> current = source.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                if (!field.trySetAccessible()) {
                    return null;
                }
                return field.get(source);
            } catch (NoSuchFieldException exception) {
                current = current.getSuperclass();
            } catch (IllegalAccessException | RuntimeException exception) {
                return null;
            }
        }
        return null;
    }

    @Nullable
    public static <T> T fieldValue(@Nullable Object source, String name, Class<T> type) {
        Object value = fieldValue(source, name);
        return type != null && type.isInstance(value) ? type.cast(value) : null;
    }

    public static int intField(@Nullable Object source, String name, int fallback) {
        Object value = fieldValue(source, name);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
