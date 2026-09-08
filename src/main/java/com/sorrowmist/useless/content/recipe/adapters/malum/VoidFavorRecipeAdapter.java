package com.sorrowmist.useless.content.recipe.adapters.malum;

import com.mojang.logging.LogUtils;
import com.sammy.malum.common.recipe.VoidFavorRecipe;
import com.sammy.malum.registry.common.recipe.MalumRecipeTypes;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Malum's Weeping Well item transmutations. */
public final class VoidFavorRecipeAdapter implements IRecipeAdapter<VoidFavorRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int PROCESS_TIME = 80;

    @Override
    public Class<VoidFavorRecipe> getRecipeClass() {
        return VoidFavorRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return MalumAdapterUtils.item("void_conduit");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<VoidFavorRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        VoidFavorRecipe source = holder.value();
        if (source.input == null || source.input.isEmpty()
                || source.result == null || source.result.isEmpty()
                || source.result.getCount() <= 0) {
            LOGGER.warn("Skipping invalid Malum void favor recipe: {}", holder.id());
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.input, 1L)),
                List.of(),
                List.of(source.result.copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
        return List.of(converted);
    }

    @Override
    public List<RecipeHolder<VoidFavorRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<VoidFavorRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<VoidFavorRecipe> holder : recipeManager.getAllRecipesFor(
                MalumRecipeTypes.VOID_FAVOR.get())) {
            VoidFavorRecipe source = holder.value();
            if (source != null && source.input != null && !source.input.isEmpty()
                    && source.result != null && !source.result.isEmpty()
                    && source.result.getCount() > 0
                    && AdapterUtils.matchesRequired(
                    mergedInputs, Map.of(source.input, 1L))) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
