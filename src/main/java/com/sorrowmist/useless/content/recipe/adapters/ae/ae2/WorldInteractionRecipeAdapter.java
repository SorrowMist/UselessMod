package com.sorrowmist.useless.content.recipe.adapters.ae.ae2;

import appeng.recipes.AERecipeTypes;
import appeng.recipes.transform.TransformCircumstance;
import appeng.recipes.transform.TransformRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts AE2 and addon in-world transform recipes using the corresponding environment mold. */
public final class WorldInteractionRecipeAdapter implements IRecipeAdapter<TransformRecipe> {
    @Override
    public Class<TransformRecipe> getRecipeClass() {
        return TransformRecipe.class;
    }

    /** Each transform circumstance selects its own mold, so this adapter uses the fallback index. */
    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return mold != null && !mold.isEmpty()
                && (mold.is(Items.WATER_BUCKET)
                || mold.is(Items.LAVA_BUCKET)
                || mold.is(Items.TNT));
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<TransformRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }

        TransformRecipe source = holder.value();
        ItemStack mold = moldFor(source.getCircumstance());
        ItemStack output = source.getResultItem();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.getIngredients());
        if (mold.isEmpty() || output == null || output.isEmpty() || inputs.isEmpty()) {
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                List.of(AdapterUtils.toMoldIngredient(mold)),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<TransformRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || !matchesMold(mold)) {
            return List.of();
        }

        List<RecipeHolder<TransformRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<TransformRecipe> holder : level.getRecipeManager().getAllRecipesFor(
                AERecipeTypes.TRANSFORM)) {
            TransformRecipe source = holder.value();
            ItemStack requiredMold = moldFor(source.getCircumstance());
            if (requiredMold.isEmpty() || !mold.is(requiredMold.getItem())) {
                continue;
            }

            Map<Ingredient, Long> requirements = ingredientRequirements(source);
            if (!requirements.isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs, requirements)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static Map<Ingredient, Long> ingredientRequirements(TransformRecipe recipe) {
        Map<Ingredient, Long> requirements = new LinkedHashMap<>();
        if (recipe == null) {
            return requirements;
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (!AdapterUtils.isIngredientEmpty(ingredient)) {
                AdapterUtils.mergeIngredient(requirements, ingredient, 1L);
            }
        }
        return requirements;
    }

    private static ItemStack moldFor(@Nullable TransformCircumstance circumstance) {
        if (circumstance == null) {
            return ItemStack.EMPTY;
        }
        if (circumstance.isExplosion()) {
            return Items.TNT.getDefaultInstance();
        }
        if (circumstance.isFluid(Fluids.WATER)) {
            return Items.WATER_BUCKET.getDefaultInstance();
        }
        if (circumstance.isFluid(Fluids.LAVA)) {
            return Items.LAVA_BUCKET.getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }
}
