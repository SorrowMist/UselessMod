package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.recipe.data.EntityData;
import com.buuz135.industrial.recipe.data.EntityIngredient;
import com.hrznstudio.titanium._impl.TagConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Shared helpers for the two laser drill adapters.
 *
 * <p>Industrial Foregoing picks a laser drill result by lens colour and weighted random. The alloy
 * furnace is deterministic, so the adapters group every source recipe by its catalyst lens (plus the
 * entity requirement, when there is one) and emit a single recipe producing all of that group's
 * results at once.</p>
 */
final class LaserDrillAdapterSupport {

    private LaserDrillAdapterSupport() {
    }

    /** Stable key for an ingredient, built from the sorted ids of the items it accepts. */
    static String ingredientKey(@Nullable Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return "";
        List<String> ids = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (stack.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (id != null) ids.add(id.toString());
        }
        if (ids.isEmpty()) return "";
        Collections.sort(ids);
        return String.join("+", ids);
    }

    /** Turns a group key into a path usable inside a {@link ResourceLocation}. */
    static String sanitize(String key) {
        StringBuilder builder = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            builder.append(c == ':' || c == '/' || c == '+' || c == '|' ? '_' : c);
        }
        return builder.toString();
    }

    /**
     * Returns the entity a recipe requires, or {@code null} when there is no requirement or the
     * requirement is a tag that cannot be narrowed down to a single type. A requirement that cannot
     * be represented is dropped rather than discarding the whole recipe.
     */
    @Nullable
    static EntityType<?> requiredEntity(@Nullable Optional<EntityData> entityData) {
        if (entityData == null || entityData.isEmpty()) return null;
        EntityData data = entityData.get();
        EntityIngredient ingredient = data == null ? null : data.getEntity();
        return ingredient == null || !ingredient.isType() ? null : ingredient.getType();
    }

    /** Returns the spawn egg mold for an entity, or an empty ingredient when it has no spawn egg. */
    static Ingredient spawnEggMold(@Nullable EntityType<?> entityType) {
        if (entityType == null) return Ingredient.EMPTY;
        SpawnEggItem egg = SpawnEggItem.byId(entityType);
        return egg == null ? Ingredient.EMPTY : Ingredient.of(egg);
    }

    /**
     * Picks the item a laser drill recipe would actually hand out, mirroring
     * {@code OreLaserBaseTile#executeRecipe}: Titanium's configured mod preference first, otherwise
     * the first matching stack.
     */
    static ItemStack representativeOutput(@Nullable SizedIngredient output) {
        if (output == null) return ItemStack.EMPTY;
        ItemStack[] items = output.getItems();
        if (items == null || items.length == 0) return ItemStack.EMPTY;

        ItemStack chosen = preferredStack(items);
        if (chosen.isEmpty()) {
            for (ItemStack candidate : items) {
                if (!candidate.isEmpty()) {
                    chosen = candidate;
                    break;
                }
            }
        }
        if (chosen.isEmpty()) return ItemStack.EMPTY;

        ItemStack copy = chosen.copy();
        copy.setCount(Math.max(1, output.count()));
        return copy;
    }

    private static ItemStack preferredStack(ItemStack[] items) {
        try {
            for (String modid : TagConfig.ITEM_PREFERENCE) {
                for (ItemStack stack : items) {
                    if (stack.isEmpty()) continue;
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    if (id != null && id.getNamespace().equals(modid)) {
                        return stack;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Titanium internals are not part of a stable API; fall back to the first stack.
        }
        return ItemStack.EMPTY;
    }
}
