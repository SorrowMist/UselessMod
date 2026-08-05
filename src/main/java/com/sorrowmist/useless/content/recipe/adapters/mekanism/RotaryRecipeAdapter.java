package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
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

/** Splits Rotary Condensentrator's two directions into independent recipes. */
public final class RotaryRecipeAdapter implements IRecipeAdapter<RotaryRecipe> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 50L;

    @Override
    public Class<RotaryRecipe> getRecipeClass() {
        return RotaryRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.ROTARY_CONDENSENTRATOR.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<RotaryRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_ROTARY.value()) {
            return List.of();
        }
        RotaryRecipe original = holder.value();
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (original.hasFluidToChemical()) {
            FluidStackIngredient fluidInput = original.getFluidInput();
            if (fluidInput != null && !fluidInput.hasNoMatchingInstances()) {
                for (FluidStack fluid : MekanismChemicalRecipeSupport.fluidRepresentations(fluidInput)) {
                    for (ChemicalStack chemical : original.getChemicalOutputDefinition()) {
                        GenericStack output = MekanismChemicalRecipeSupport.key(chemical);
                        if (output == null) continue;
                        ResourceLocation fluidId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid());
                        result.add(MekanismChemicalRecipeSupport.recipe(
                                MekanismChemicalRecipeSupport.variantId(holder.id(), "fluid_to_chemical_"
                                        + fluidId.getNamespace() + "_" + fluidId.getPath() + "_" + id(chemical)),
                                List.of(), List.of(fluid), List.of(), List.of(), List.of(), List.of(output),
                                AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                                AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
                    }
                }
            }
        }

        if (original.hasChemicalToFluid()) {
            ChemicalStackIngredient chemicalInput = original.getChemicalInput();
            if (chemicalInput != null && !chemicalInput.hasNoMatchingInstances()) {
                for (ChemicalStack chemical : chemicalInput.getRepresentations()) {
                    GenericStack input = MekanismChemicalRecipeSupport.key(chemical);
                    if (input == null) continue;
                    for (FluidStack fluid : original.getFluidOutputDefinition()) {
                        FluidStack output = fluid.copy();
                        result.add(MekanismChemicalRecipeSupport.recipe(
                                MekanismChemicalRecipeSupport.variantId(holder.id(), "chemical_to_fluid_"
                                        + id(chemical) + "_" + fluidId(output)), List.of(), List.of(), List.of(input),
                                List.of(), List.of(output), List.of(),
                                AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                                AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<RotaryRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<RotaryRecipe>> result = new ArrayList<>();
        for (RecipeHolder<RotaryRecipe> holder : manager.getAllRecipesFor(MekanismRecipeTypes.TYPE_ROTARY.value())) {
            RotaryRecipe recipe = holder.value();
            boolean fluidMatch = recipe.hasFluidToChemical() && !mergedFluids.isEmpty()
                    && MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, recipe.getFluidInput());
            boolean chemicalMatch = recipe.hasChemicalToFluid() && !mergedKeys.isEmpty()
                    && MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, recipe.getChemicalInput());
            if (fluidMatch || chemicalMatch) result.add(holder);
        }
        return result;
    }

    private static String id(ChemicalStack stack) {
        ResourceLocation id = stack.getChemicalHolder().getKey().location();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static String fluidId(FluidStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
