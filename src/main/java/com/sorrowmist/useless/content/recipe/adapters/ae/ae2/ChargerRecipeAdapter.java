package com.sorrowmist.useless.content.recipe.adapters.ae.ae2;

import appeng.core.definitions.AEBlocks;
import appeng.recipes.AERecipeTypes;
import appeng.recipes.handlers.ChargerRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts AE2 charger recipes into alloy-furnace recipes. */
public final class ChargerRecipeAdapter implements IRecipeAdapter<ChargerRecipe> {
    private static final long ENERGY = 3_200L;
    private static final int PROCESS_TIME = 20;

    @Override
    public Class<ChargerRecipe> getRecipeClass() {
        return ChargerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(AEBlocks.CHARGER.asItem());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ChargerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        ChargerRecipe source = holder.value();
        Ingredient input = source.getIngredient();
        ItemStack output = source.getResultItem();
        if (AdapterUtils.isIngredientEmpty(input) || output == null || output.isEmpty()) {
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                ENERGY,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
        return List.of(converted);
    }

    @Override
    public List<RecipeHolder<ChargerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<ChargerRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ChargerRecipe> holder : level.getRecipeManager()
                .getAllRecipesFor(AERecipeTypes.CHARGER)) {
            ChargerRecipe source = holder.value();
            Ingredient input = source.getIngredient();
            if (AdapterUtils.isIngredientEmpty(input) || source.getResultItem().isEmpty()) {
                continue;
            }

            Map<Ingredient, Long> required = new LinkedHashMap<>();
            AdapterUtils.mergeIngredient(required, input, 1L);
            if (AdapterUtils.matchesRequired(mergedInputs, required)) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
