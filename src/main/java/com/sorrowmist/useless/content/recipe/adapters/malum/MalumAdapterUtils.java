package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.sammy.malum.core.systems.recipe.SpiritIngredient;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared validation and component-aware input helpers for Malum recipe adapters. */
final class MalumAdapterUtils {
    static final String MOD_ID = "malum";

    private MalumAdapterUtils() {
    }

    static ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    static Map<Ingredient, Long> requirements() {
        return new LinkedHashMap<>();
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

    static boolean addSpirits(Map<Ingredient, Long> target, @Nullable List<SpiritIngredient> spirits) {
        if (spirits == null) {
            return false;
        }
        for (SpiritIngredient spirit : spirits) {
            if (spirit == null || spirit.count() <= 0) {
                return false;
            }
            ItemStack shard;
            try {
                shard = spirit.asItemStack();
            } catch (RuntimeException exception) {
                return false;
            }
            if (shard == null || shard.isEmpty() || shard.getCount() <= 0) {
                return false;
            }
            Ingredient exactShard = DataComponentIngredient.of(true, shard.copyWithCount(1));
            if (!addIngredient(target, exactShard, shard.getCount())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    static List<CountedIngredient> counted(Map<Ingredient, Long> requirements) {
        if (requirements == null) {
            return null;
        }
        List<CountedIngredient> result = new ArrayList<>(requirements.size());
        for (Map.Entry<Ingredient, Long> entry : requirements.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty()
                    || entry.getValue() == null || entry.getValue() <= 0L) {
                return null;
            }
            result.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    static List<ItemStack> distinctMatches(@Nullable List<ItemStack> stacks, @Nullable Ingredient ingredient) {
        List<ItemStack> matches = new ArrayList<>();
        if (stacks == null || ingredient == null || ingredient.isEmpty()) {
            return matches;
        }
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty() || !ingredient.test(stack)
                    || matches.stream().anyMatch(existing -> ItemStack.isSameItemSameComponents(existing, stack))) {
                continue;
            }
            matches.add(stack.copyWithCount(1));
        }
        return matches;
    }
}
