package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.yxiao233.ifeu.common.recipe.PrecisionShapelessRecipe;
import net.yxiao233.ifeu.common.registry.IFEUBlocks;
import net.yxiao233.ifeu.common.registry.IFEURecipes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Extra Upgrades precision shapeless recipes. */
public final class PrecisionShapelessRecipeAdapter
        implements IRecipeAdapter<PrecisionShapelessRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<PrecisionShapelessRecipe> getRecipeClass() {
        return PrecisionShapelessRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(IFEUBlocks.PRECISION_CRAFTING_TABLE.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<PrecisionShapelessRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        PrecisionShapelessRecipe source = holder.value();
        AdvancedAlloyFurnaceRecipe converted = ExtraUpgradesCraftingRecipeAdapterSupport.precisionRecipe(
                com.sorrowmist.useless.content.recipe.AdapterUtils.convertedId(holder.id()),
                source.inputs, source.output, source.chance, getMoldItem());
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<PrecisionShapelessRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        RecipeType<PrecisionShapelessRecipe> type = recipeType();
        if (type == null) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<PrecisionShapelessRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<PrecisionShapelessRecipe> holder : manager.getAllRecipesFor(type)) {
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
    private static RecipeType<PrecisionShapelessRecipe> recipeType() {
        if (IFEURecipes.PRECISION_SHAPELESS_TYPE == null
                || IFEURecipes.PRECISION_SHAPELESS_TYPE.get() == null) return null;
        return (RecipeType<PrecisionShapelessRecipe>) (RecipeType<?>)
                IFEURecipes.PRECISION_SHAPELESS_TYPE.get();
    }
}
