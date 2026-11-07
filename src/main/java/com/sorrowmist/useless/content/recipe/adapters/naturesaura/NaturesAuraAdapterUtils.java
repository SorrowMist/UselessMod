package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Common validation and component-preserving helpers for Nature's Aura adapters. */
final class NaturesAuraAdapterUtils {
    private static final String MOD_ID = "naturesaura";

    private NaturesAuraAdapterUtils() {
    }

    static ItemStack item(String path) {
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_ID, path))
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        return stack.is(Items.AIR) ? ItemStack.EMPTY : stack;
    }

    static int ingredientAmount(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return 0;
        }
        int amount = 0;
        for (ItemStack stack : ingredient.getItems()) {
            if (stack != null && stack.getCount() > amount) {
                amount = stack.getCount();
            }
        }
        return amount;
    }

    static boolean addIngredient(Map<Ingredient, Long> target, @Nullable Ingredient ingredient, long count) {
        if (target == null || ingredient == null || ingredient.isEmpty() || count <= 0L) {
            return false;
        }
        try {
            for (Map.Entry<Ingredient, Long> entry : target.entrySet()) {
                if (AdapterUtils.areIngredientsEqual(entry.getKey(), ingredient)) {
                    target.put(entry.getKey(), Math.addExact(entry.getValue(), count));
                    return true;
                }
            }
            target.put(ingredient, count);
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    static Map<Ingredient, Long> requirements() {
        return new LinkedHashMap<>();
    }

    static List<CountedIngredient> counted(Map<Ingredient, Long> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<CountedIngredient> counted = new ArrayList<>(requirements.size());
        for (Map.Entry<Ingredient, Long> entry : requirements.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()
                    || entry.getValue() == null || entry.getValue() <= 0L) {
                return List.of();
            }
            counted.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(counted);
    }

    @Nullable
    static ItemStack multipliedOutput(@Nullable ItemStack source, int multiplier) {
        if (source == null || source.isEmpty() || source.getCount() <= 0 || multiplier <= 0) {
            return null;
        }
        try {
            int count = Math.multiplyExact(source.getCount(), multiplier);
            return source.copyWithCount(count);
        } catch (ArithmeticException exception) {
            return null;
        }
    }
}
