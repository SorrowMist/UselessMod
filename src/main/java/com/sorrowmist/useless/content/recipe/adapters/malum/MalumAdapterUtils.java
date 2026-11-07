package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.sammy.malum.core.systems.recipe.SpiritWithCount;
import com.sorrowmist.useless.compat.forge1201.recipe.DataComponentIngredient;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared validation and component-aware input helpers for Malum 1.6.7 recipe adapters. */
final class MalumAdapterUtils {
    static final String MOD_ID = "malum";

    private MalumAdapterUtils() {
    }

    static ItemStack item(String path) {
        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(MOD_ID, path));
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

    /**
     * Adds the spirit shards consumed by Malum. 1.6.7 stores spirits as
     * {@link SpiritWithCount}; {@link SpiritWithCount#getStack()} already carries the exact shard
     * count, so it must not be applied twice.
     */
    static boolean addSpirits(Map<Ingredient, Long> target, @Nullable List<SpiritWithCount> spirits) {
        if (target == null || spirits == null) {
            return false;
        }
        for (SpiritWithCount spirit : spirits) {
            if (spirit == null || spirit.type == null || spirit.count <= 0) {
                return false;
            }
            ItemStack shard;
            try {
                shard = spirit.getStack();
            } catch (RuntimeException exception) {
                return false;
            }
            if (shard == null || shard.isEmpty() || shard.getCount() <= 0) {
                return false;
            }
            Ingredient exactShard = DataComponentIngredient.of(true, shard.copyWithCount(1));
            if (!addIngredient(target, exactShard, spirit.count)) {
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
                    || matches.stream().anyMatch(existing -> ItemStack.isSameItemSameTags(existing, stack))) {
                continue;
            }
            matches.add(stack.copyWithCount(1));
        }
        return matches;
    }

    /** Returns the tag that Malum's spirit altar would copy onto the output for this input. */
    @Nullable
    static ItemStack altarOutput(@Nullable ItemStack staticOutput, boolean useNbtFromInput,
                                 @Nullable ItemStack primaryInput) {
        if (staticOutput == null || staticOutput.isEmpty() || staticOutput.getCount() <= 0) {
            return null;
        }
        ItemStack output = staticOutput.copy();
        if (useNbtFromInput && primaryInput != null && primaryInput.hasTag()) {
            // SpiritAltarBlockEntity replaces the output tag with the primary input tag.
            output.setTag(primaryInput.getTag().copy());
        }
        return output;
    }

    /** A stable, registry-safe item description for generated recipe ids. */
    static String itemKey(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? item.getDescriptionId() : id.toString();
    }
}
