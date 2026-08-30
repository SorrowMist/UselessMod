package com.sorrowmist.useless.content.recipe.adapters.delight.casualnessdelight;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import top.tobitobi.casualnessdelight.common.crafting.DeepFryingRecipe;
import top.tobitobi.casualnessdelight.common.registry.ModItems;
import top.tobitobi.casualnessdelight.common.registry.ModRecipeTypes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts Casualness Delight deep-frying recipes. */
public final class DeepFryingRecipeAdapter implements IRecipeAdapter<DeepFryingRecipe> {
    private static final int BASE_COOK_TIME = 200;

    @Override
    public String sourceId() {
        return RecipeSourceIds.CASUALNESS_DELIGHT;
    }

    @Override
    public Class<DeepFryingRecipe> getRecipeClass() {
        return DeepFryingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return ModItems.DEEP_FRYING_PAN.get().getDefaultInstance();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DeepFryingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        DeepFryingRecipe source = holder.value();
        Ingredient input = source.getIngredients().isEmpty()
                ? Ingredient.EMPTY : source.getIngredients().getFirst();
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        if (input == null || input.isEmpty() || output == null || output.isEmpty()
                || output.getCount() <= 0) {
            return List.of();
        }

        int processTime = Math.max(1, source.getCookingTime());
        long energy = Math.max(1L,
                (long) processTime * AdapterUtils.DEFAULT_ENERGY / BASE_COOK_TIME);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<DeepFryingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || (mergedFluids != null && !mergedFluids.isEmpty())
                || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<DeepFryingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<DeepFryingRecipe> holder : recipeManager.getAllRecipesFor(
                ModRecipeTypes.DEEP_FRYING.get())) {
            Ingredient ingredient = holder.value().getIngredients().isEmpty()
                    ? Ingredient.EMPTY : holder.value().getIngredients().getFirst();
            if (ingredient != null && !ingredient.isEmpty()) {
                Map<Ingredient, Long> requirements = new LinkedHashMap<>();
                AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
                if (com.sorrowmist.useless.content.recipe.ItemIngredientAllocator
                        .matches(mergedInputs, requirements)) {
                    matches.add(holder);
                }
            }
        }
        return List.copyOf(matches);
    }
}
