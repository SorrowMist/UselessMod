package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.GenericStack;
import com.raishxn.ufo.block.ModBlocks;
import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.DimensionalMatterAssemblerRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;

import java.util.ArrayList;
import java.util.List;

/** Converts UFO Dimensional Matter Assembler recipes. */
public final class DimensionalMatterAssemblerRecipeAdapter
        extends UfoRecipeAdapter<DimensionalMatterAssemblerRecipe> {
    @Override
    public Class<DimensionalMatterAssemblerRecipe> getRecipeClass() {
        return DimensionalMatterAssemblerRecipe.class;
    }

    @Override
    protected RecipeType<DimensionalMatterAssemblerRecipe> recipeType() {
        return ModRecipes.DMA_RECIPE_TYPE.get();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.DIMENSIONAL_MATTER_ASSEMBLER_BLOCK.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<DimensionalMatterAssemblerRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        DimensionalMatterAssemblerRecipe source = holder.value();
        if (source.getTime() <= 0) {
            return List.of();
        }

        List<CountedIngredient> items = UfoRecipeAdapterSupport.itemInputs(
                source.getItemInputs(), IngredientStack.Item::getIngredient,
                value -> value.getAmount());
        List<SizedFluidIngredient> fluids = UfoRecipeAdapterSupport.fluidInputs(
                source.getFluidInputs(), IngredientStack.Fluid::getIngredient,
                value -> value.getAmount());
        if (fluids == null) {
            return List.of();
        }
        List<GenericStack> keyOutputs = new ArrayList<>();
        UfoRecipeAdapterSupport.addGenericOutputs(keyOutputs, source.getItemOutputs());
        UfoRecipeAdapterSupport.addGenericOutputs(keyOutputs, source.getFluidOutputs());
        if ((items.isEmpty() && fluids.isEmpty()) || keyOutputs.isEmpty()) {
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), items, fluids, List.of(),
                List.of(), List.of(), keyOutputs,
                UfoRecipeAdapterSupport.energy(source.getEnergy()), source.getTime(),
                Ingredient.EMPTY, 0, AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL));
    }
}
