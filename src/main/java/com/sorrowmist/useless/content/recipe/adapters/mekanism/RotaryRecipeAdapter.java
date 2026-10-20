package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
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
        return new ItemStack(MekanismBlocks.ROTARY_CONDENSENTRATOR.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<RotaryRecipe> holder, Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeType.ROTARY.get()) {
            return List.of();
        }
        RotaryRecipe original = holder.value();
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();

        if (original.hasFluidToGas()) {
            FluidStackIngredient fluidInput = original.getFluidInput();
            if (fluidInput != null && !fluidInput.hasNoMatchingInstances()) {
                List<com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient> fluidInputs =
                        MekanismChemicalRecipeSupport.fluidIngredients(fluidInput);
                for (ChemicalStack<?> chemical : original.getGasOutputDefinition()) {
                    GenericStack output = MekanismChemicalRecipeSupport.key(chemical);
                    if (output == null) continue;
                    result.add(MekanismChemicalRecipeSupport.recipe(
                            MekanismChemicalRecipeSupport.variantId(holder.id(), "fluid_to_chemical_" + id(chemical)),
                            List.of(), fluidInputs, List.of(), List.of(), List.of(), List.of(output),
                            AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                            AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
                }
            }
        }

        if (original.hasGasToFluid()) {
            ChemicalStackIngredient<?,?> chemicalInput = original.getGasInput();
            if (chemicalInput != null && !chemicalInput.hasNoMatchingInstances()) {
                for (ChemicalStack<?> chemical : chemicalInput.getRepresentations()) {
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
        for (RecipeHolder<RotaryRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,RotaryRecipe>getAllRecipesFor(manager, MekanismRecipeType.ROTARY.get())) {
            RotaryRecipe recipe = holder.value();
            boolean fluidMatch = recipe.hasFluidToGas() && !mergedFluids.isEmpty()
                    && MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, recipe.getFluidInput());
            boolean chemicalMatch = recipe.hasGasToFluid() && !mergedKeys.isEmpty()
                    && MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, recipe.getGasInput());
            if (fluidMatch || chemicalMatch) result.add(holder);
        }
        return result;
    }

    private static String id(ChemicalStack<?> stack) {
        ResourceLocation id = stack.getTypeRegistryName();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static String fluidId(FluidStack stack) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
