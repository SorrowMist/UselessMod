package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.yxiao233.ifeu.common.recipe.ShapedRecipe;
import net.yxiao233.ifeu.common.registry.IFEUBlocks;
import net.yxiao233.ifeu.common.registry.IFEURecipes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Extra Upgrades fluid crafting table shaped recipes. */
public final class FluidShapedRecipeAdapter implements IRecipeAdapter<ShapedRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<ShapedRecipe> getRecipeClass() {
        return ShapedRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(IFEUBlocks.FLUID_CRAFTING_TABLE.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ShapedRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        ShapedRecipe source = holder.value();
        AdvancedAlloyFurnaceRecipe converted = ExtraUpgradesCraftingRecipeAdapterSupport.fluidRecipe(
                AdapterUtils.convertedId(holder.id()), source.inputs, source.inputFluid,
                source.output, getMoldItem());
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<ShapedRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        RecipeType<ShapedRecipe> type = recipeType();
        if (type == null) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ShapedRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ShapedRecipe> holder : manager.getAllRecipesFor(type)) {
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(holder, level);
            if (!converted.isEmpty()
                    && ExtraUpgradesCraftingRecipeAdapterSupport.matches(
                    converted.getFirst(), mergedInputs, mergedFluids)) {
                matches.add(holder);
            }
        }
        return List.copyOf(matches);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static RecipeType<ShapedRecipe> recipeType() {
        if (IFEURecipes.SHAPED_TYPE == null || IFEURecipes.SHAPED_TYPE.get() == null) return null;
        return (RecipeType<ShapedRecipe>) (RecipeType<?>) IFEURecipes.SHAPED_TYPE.get();
    }
}
