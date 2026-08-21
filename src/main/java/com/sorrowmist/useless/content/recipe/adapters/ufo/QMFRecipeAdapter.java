package com.sorrowmist.useless.content.recipe.adapters.ufo;

import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.QMFRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.raishxn.ufo.block.MultiblockBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Converts UFO's standalone Quantum Matter Fabricator recipes. */
public final class QMFRecipeAdapter extends UfoRecipeAdapter<QMFRecipe> {
    @Override
    public Class<QMFRecipe> getRecipeClass() {
        return QMFRecipe.class;
    }

    @Override
    protected RecipeType<QMFRecipe> recipeType() {
        return ModRecipes.QMF_TYPE.get();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MultiblockBlocks.QUANTUM_MATTER_FABRICATOR_CONTROLLER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<QMFRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        QMFRecipe source = holder.value();
        if (source.getTime() <= 0) {
            return List.of();
        }

        List<CountedIngredient> items = UfoRecipeAdapterSupport.itemInputs(
                source.getItemInputs(), QMFRecipe.QMFRecipeIngredient::ingredient,
                QMFRecipe.QMFRecipeIngredient::amount);
        List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> fluids =
                UfoRecipeAdapterSupport.concreteFluidInputs(
                        source.getFluidInputs(), QMFRecipe.QMFFluidIngredient::fluid,
                        QMFRecipe.QMFFluidIngredient::amount);
        if (fluids == null) {
            return List.of();
        }
        List<appeng.api.stacks.GenericStack> chemicals = UfoRecipeAdapterSupport.chemicalInputs(
                source.getChemicalInputs(), QMFRecipe.QMFChemicalIngredient::chemicalId,
                QMFRecipe.QMFChemicalIngredient::amount);
        if (chemicals == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> outputFluids = new ArrayList<>();
        List<appeng.api.stacks.GenericStack> keyOutputs = new ArrayList<>();
        ItemStack output = source.getResultItem();
        UfoRecipeAdapterSupport.addItemOutput(outputs, keyOutputs, output,
                output == null ? 0L : output.getCount());
        if ((items.isEmpty() && fluids.isEmpty() && chemicals.isEmpty())
                || (outputs.isEmpty() && outputFluids.isEmpty() && keyOutputs.isEmpty())) {
            return List.of();
        }

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), items, fluids, chemicals,
                outputs, outputFluids, keyOutputs,
                UfoRecipeAdapterSupport.energy(source.getEnergy()), source.getTime(),
                Ingredient.EMPTY, 0, AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL));
    }
}
