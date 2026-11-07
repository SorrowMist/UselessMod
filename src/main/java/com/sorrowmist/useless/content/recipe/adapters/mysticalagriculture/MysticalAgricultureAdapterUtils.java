package com.sorrowmist.useless.content.recipe.adapters.mysticalagriculture;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
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

/** Shared 1.20.1 helpers for Mystical Agriculture recipe conversion. */
final class MysticalAgricultureAdapterUtils {
    private static final String MOD_ID = "mysticalagriculture";

    private MysticalAgricultureAdapterUtils() {
    }

    private record OutputFields(java.lang.reflect.Field transferNbt, java.lang.reflect.Field transformer) {}
    private static final ClassValue<OutputFields> OUTPUT_FIELDS = new ClassValue<>() {
        @Override protected OutputFields computeValue(Class<?> type) {
            try {
                var transfer = type.getDeclaredField("transferNBT");
                var transformer = type.getDeclaredField("transformer");
                transfer.setAccessible(true);
                transformer.setAccessible(true);
                return new OutputFields(transfer, transformer);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot verify native MA output semantics", failure);
            }
        }
    };

    /** 7.0.24 exposes neither flag through its public recipe interfaces. Inspect only the pinned
     * native classes, read-only; unknown subclasses and dynamic outputs must not be flattened. */
    static boolean isStaticOutputRecipe(Object source) {
        Class<?> type = source.getClass();
        if (type != com.blakebr0.mysticalagriculture.crafting.recipe.InfusionRecipe.class
                && type != com.blakebr0.mysticalagriculture.crafting.recipe.AwakeningRecipe.class) return false;
        try {
            OutputFields fields = OUTPUT_FIELDS.get(type);
            return !fields.transferNbt().getBoolean(source) && fields.transformer().get(source) == null;
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot verify native MA output semantics", failure);
        }
    }

    static ItemStack item(String path) {
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(MOD_ID, path))
                .map(ItemStack::new)
                .orElse(ItemStack.EMPTY);
        return stack.is(Items.AIR) ? ItemStack.EMPTY : stack;
    }

    /**
     * Keeps a concrete recipe stack's NBT instead of narrowing it to a plain item.
     *
     * <p>1.20.1 has no data components; Forge's strict NBT ingredient is the native representation
     * that survives recipe serialization, network sync and JEI display.</p>
     */
    static Ingredient exactStack(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? Ingredient.EMPTY
                : DataComponentIngredient.of(true, stack.copyWithCount(1));
    }

    static void addIngredient(Map<Ingredient, Long> target, @Nullable Ingredient ingredient, long count) {
        if (target == null || ingredient == null || ingredient.isEmpty() || count <= 0L) {
            return;
        }
        AdapterUtils.mergeIngredient(target, ingredient, count);
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

    static Map<Ingredient, Long> requirements() {
        return new LinkedHashMap<>();
    }
}
