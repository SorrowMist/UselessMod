package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.recipes.FluidToFluidRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Fluid -> fluid adapter for the Thermal Evaporation Controller. */
public final class FluidToFluidRecipeAdapter implements IRecipeAdapter<FluidToFluidRecipe> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 50L;

    @Override
    public Class<FluidToFluidRecipe> getRecipeClass() {
        return FluidToFluidRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.THERMAL_EVAPORATION_CONTROLLER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<FluidToFluidRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_EVAPORATING.value()) {
            return List.of();
        }
        FluidStackIngredient input = holder.value().getInput();
        if (input == null || input.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> fluidInputs =
                MekanismChemicalRecipeSupport.fluidIngredients(input);
        for (FluidStack output : holder.value().getOutputDefinition()) {
            if (output.isEmpty()) continue;
            result.add(MekanismChemicalRecipeSupport.recipe(
                    MekanismChemicalRecipeSupport.variantId(holder.id(), "evaporating_out_" + fluidId(output)),
                    List.of(), fluidInputs, List.of(), List.of(), List.of(output.copy()), List.of(),
                    AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                    AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
        }
        return result;
    }

    @Override
    public List<RecipeHolder<FluidToFluidRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedFluids.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<FluidToFluidRecipe>> result = new ArrayList<>();
        for (RecipeHolder<FluidToFluidRecipe> holder : manager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_EVAPORATING.value())) {
            if (MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, holder.value().getInput())) {
                result.add(holder);
            }
        }
        return result;
    }

    private static String fluidId(FluidStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
