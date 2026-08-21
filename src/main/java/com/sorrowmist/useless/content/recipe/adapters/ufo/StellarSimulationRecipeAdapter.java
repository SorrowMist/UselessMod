package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.GenericStack;
import com.raishxn.ufo.block.MultiblockBlocks;
import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.StellarSimulationRecipe;
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

/** Converts UFO Stellar Simulation recipes, including fuel and coolant fluids. */
public final class StellarSimulationRecipeAdapter
        extends UfoRecipeAdapter<StellarSimulationRecipe> {
    @Override
    public Class<StellarSimulationRecipe> getRecipeClass() {
        return StellarSimulationRecipe.class;
    }

    @Override
    protected RecipeType<StellarSimulationRecipe> recipeType() {
        return ModRecipes.STELLAR_SIMULATION_TYPE.get();
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return new ItemStack(MultiblockBlocks.STELLAR_NEXUS_CONTROLLER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<StellarSimulationRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        StellarSimulationRecipe source = holder.value();
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
        SizedFluidIngredient fuel = UfoRecipeAdapterSupport.namedFluid(
                source.getFuelFluid(), source.getFuelAmount());
        if (source.getFuelAmount() > 0L && fuel == null) {
            return List.of();
        }
        if (fuel != null) {
            fluids = append(fluids, fuel);
        }
        if (source.getCoolantAmount() > 0L) {
            if (source.getCoolantAmount() > Integer.MAX_VALUE) {
                return List.of();
            }
            fluids = append(fluids, UfoRecipeAdapterSupport.coolant(source.getCoolantAmount()));
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
                UfoRecipeAdapterSupport.energy(source.getEnergyCost()), source.getTime(),
                Ingredient.EMPTY, 0, AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL));
    }

    private static List<SizedFluidIngredient> append(
            List<SizedFluidIngredient> fluids, SizedFluidIngredient additional) {
        List<SizedFluidIngredient> result = new ArrayList<>(fluids);
        result.add(additional);
        return List.copyOf(result);
    }
}
