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
import net.yxiao233.ifeu.common.recipe.ShapelessRecipe;
import net.yxiao233.ifeu.common.registry.IFEUBlocks;
import net.yxiao233.ifeu.common.registry.IFEURecipes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Extra Upgrades fluid crafting table shapeless recipes. */
public final class FluidShapelessRecipeAdapter implements IRecipeAdapter<ShapelessRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<ShapelessRecipe> getRecipeClass() {
        return ShapelessRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(IFEUBlocks.FLUID_CRAFTING_TABLE.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ShapelessRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        ShapelessRecipe source = holder.value();
        AdvancedAlloyFurnaceRecipe converted = ExtraUpgradesCraftingRecipeAdapterSupport.fluidRecipe(
                AdapterUtils.convertedId(holder.id()), source.inputs, source.inputFluid,
                source.output, getMoldItem());
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<ShapelessRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        RecipeType<ShapelessRecipe> type = recipeType();
        if (type == null) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ShapelessRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<ShapelessRecipe> holder : manager.getAllRecipesFor(type)) {
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
    private static RecipeType<ShapelessRecipe> recipeType() {
        if (IFEURecipes.SHAPELESS_TYPE == null
                || IFEURecipes.SHAPELESS_TYPE.get() == null) return null;
        return (RecipeType<ShapelessRecipe>) (RecipeType<?>) IFEURecipes.SHAPELESS_TYPE.get();
    }
}
