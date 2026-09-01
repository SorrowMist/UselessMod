package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.yxiao233.ifeu.common.recipe.PrecisionShapedRecipe;
import net.yxiao233.ifeu.common.registry.IFEUBlocks;
import net.yxiao233.ifeu.common.registry.IFEURecipes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Extra Upgrades precision shaped recipes. */
public final class PrecisionShapedRecipeAdapter
        implements IRecipeAdapter<PrecisionShapedRecipe> {
    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<PrecisionShapedRecipe> getRecipeClass() {
        return PrecisionShapedRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(IFEUBlocks.PRECISION_CRAFTING_TABLE.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<PrecisionShapedRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        PrecisionShapedRecipe source = holder.value();
        AdvancedAlloyFurnaceRecipe converted = ExtraUpgradesCraftingRecipeAdapterSupport.precisionRecipe(
                com.sorrowmist.useless.content.recipe.AdapterUtils.convertedId(holder.id()),
                source.inputs, source.output, source.chance, getMoldItem());
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    public List<RecipeHolder<PrecisionShapedRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        RecipeType<PrecisionShapedRecipe> type = recipeType();
        if (type == null) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<PrecisionShapedRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<PrecisionShapedRecipe> holder : manager.getAllRecipesFor(type)) {
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
    private static RecipeType<PrecisionShapedRecipe> recipeType() {
        if (IFEURecipes.PRECISION_SHAPED_TYPE == null
                || IFEURecipes.PRECISION_SHAPED_TYPE.get() == null) return null;
        return (RecipeType<PrecisionShapedRecipe>) (RecipeType<?>)
                IFEURecipes.PRECISION_SHAPED_TYPE.get();
    }
}
