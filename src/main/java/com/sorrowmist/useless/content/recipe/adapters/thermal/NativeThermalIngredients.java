package com.sorrowmist.useless.content.recipe.adapters.thermal;

import cofh.lib.util.crafting.IngredientWithCount;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** CoFH's toJson omits the count; unwrapping must also preserve its actual tag/NBT predicate. */
public final class NativeThermalIngredients {
    private static final Field COUNT = field("count");
    private static final Field WRAPPED = field("wrappedIngredient");

    private NativeThermalIngredients() {
    }

    private static Field field(String name) {
        try {
            Field field = IngredientWithCount.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unsupported CoFH counted ingredient", exception);
        }
    }

    public static List<CountedIngredient> items(List<Ingredient> inputs, long batch) {
        List<CountedIngredient> result = new ArrayList<>();
        for (Ingredient ingredient : inputs) {
            if (ingredient == Ingredient.EMPTY) continue;
            long count = batch;
            int depth = 0;
            try {
                while (ingredient instanceof IngredientWithCount) {
                    if (++depth > 64) throw new IllegalArgumentException("Nested CoFH ingredient exceeds 64");
                    count = Math.multiplyExact(count, COUNT.getInt(ingredient));
                    ingredient = (Ingredient) WRAPPED.get(ingredient);
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot read CoFH ingredient count", exception);
            }
            if (count <= 0L || ingredient.isEmpty()) throw new IllegalArgumentException("Invalid required CoFH ingredient");
            result.add(new CountedIngredient(ingredient, count));
        }
        return List.copyOf(result);
    }
}
