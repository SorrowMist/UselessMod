package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Common adapter behavior for Mekanism's recipe-viewer-only recipe sources. */
public abstract class MekanismSyntheticRecipeAdapter implements IRecipeAdapter<MekanismSyntheticRecipe> {
    protected abstract List<RecipeHolder<MekanismSyntheticRecipe>> createGeneratedRecipes(Level level);

    @Override
    public final Class<MekanismSyntheticRecipe> getRecipeClass() {
        return MekanismSyntheticRecipe.class;
    }

    @Override
    public final List<RecipeHolder<MekanismSyntheticRecipe>> getGeneratedRecipes(Level level) {
        return level == null ? List.of() : createGeneratedRecipes(level);
    }

    @Override
    public final List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<MekanismSyntheticRecipe> holder, Level level) {
        if (holder == null || holder.value() == null || holder.value().convertedRecipe() == null) {
            return List.of();
        }
        return List.of(holder.value().convertedRecipe());
    }

    @Override
    public final List<RecipeHolder<MekanismSyntheticRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();

        List<RecipeHolder<MekanismSyntheticRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<MekanismSyntheticRecipe> holder : getGeneratedRecipes(level)) {
            AdvancedAlloyFurnaceRecipe recipe = holder.value().convertedRecipe();
            if (MekanismChemicalRecipeSupport.matchesConvertedRecipe(
                    recipe, mergedInputs, mergedFluids, mergedKeys)) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
