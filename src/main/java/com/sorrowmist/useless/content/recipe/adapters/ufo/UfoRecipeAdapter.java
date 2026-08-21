package com.sorrowmist.useless.content.recipe.adapters.ufo;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class UfoRecipeAdapter<T extends Recipe<?>> implements IRecipeAdapter<T> {
    protected abstract RecipeType<T> recipeType();

    protected boolean accepts(T recipe) {
        return recipe != null;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<RecipeHolder<T>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, Map<appeng.api.stacks.AEKey, Long> mergedKeys,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<T>> result = new ArrayList<>();
        for (RecipeHolder<T> holder : (List<RecipeHolder<T>>) (List)
                manager.getAllRecipesFor((RecipeType) recipeType())) {
            if (!accepts(holder.value())) {
                continue;
            }
            boolean matched = false;
            for (AdvancedAlloyFurnaceRecipe converted : convertAll(holder, level)) {
                if (UfoRecipeAdapterSupport.matches(converted, mergedInputs, mergedFluids, mergedKeys)) {
                    matched = true;
                    break;
                }
            }
            if (matched) {
                result.add(holder);
            }
        }
        return result;
    }
}
