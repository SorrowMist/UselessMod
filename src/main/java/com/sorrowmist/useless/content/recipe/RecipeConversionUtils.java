package com.sorrowmist.useless.content.recipe;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.List;

/** Isolates failures from individual external-recipe conversions. */
public final class RecipeConversionUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RecipeConversionUtils() {
    }

    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level) {
        return convert(adapter, holder, level, null, false);
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level) {
        return convertAll((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter, holder, level);
    }

    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level,
            List<ItemStack> actualInputs) {
        return convert(adapter, holder, level, actualInputs, true);
    }

    /** @deprecated Use the public API adapter type. */
    @Deprecated(forRemoval = false)
    public static List<AdvancedAlloyFurnaceRecipe> convertAll(
            IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level,
            List<ItemStack> actualInputs) {
        return convertAll((com.sorrowmist.useless.api.recipe.IRecipeAdapter<?>) adapter,
                holder, level, actualInputs);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<AdvancedAlloyFurnaceRecipe> convert(
            com.sorrowmist.useless.api.recipe.IRecipeAdapter<?> adapter, RecipeHolder<?> holder, Level level,
            List<ItemStack> actualInputs, boolean useActualInputs) {
        if (adapter == null || holder == null) {
            return List.of();
        }

        try {
            com.sorrowmist.useless.api.recipe.IRecipeAdapter rawAdapter = adapter;
            List<AdvancedAlloyFurnaceRecipe> converted = useActualInputs
                    ? rawAdapter.convertAll((RecipeHolder) holder, level, actualInputs)
                    : rawAdapter.convertAll((RecipeHolder) holder, level);
            return converted == null ? List.of() : converted;
        } catch (RuntimeException exception) {
            LOGGER.warn("Skipping failed recipe conversion: adapter={}, recipe={}",
                    adapter.getClass().getName(), holder.id(), exception);
            return List.of();
        }
    }
}
