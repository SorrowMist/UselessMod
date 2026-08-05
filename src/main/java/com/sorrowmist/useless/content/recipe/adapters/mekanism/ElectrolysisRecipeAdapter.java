package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ElectrolysisRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
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

/** Fluid -> two chemical outputs adapter for the Electrolytic Separator. */
public final class ElectrolysisRecipeAdapter implements IRecipeAdapter<ElectrolysisRecipe> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 200L;

    @Override
    public Class<ElectrolysisRecipe> getRecipeClass() {
        return ElectrolysisRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.ELECTROLYTIC_SEPARATOR.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ElectrolysisRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_SEPARATING.value()) {
            return List.of();
        }
        ElectrolysisRecipe original = holder.value();
        FluidStackIngredient input = original.getInput();
        if (input == null || input.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (FluidStack fluid : MekanismChemicalRecipeSupport.fluidRepresentations(input)) {
            ResourceLocation fluidId = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            for (ElectrolysisRecipe.ElectrolysisRecipeOutput output : original.getOutputDefinition()) {
                GenericStack left = MekanismChemicalRecipeSupport.key(output.left());
                GenericStack right = MekanismChemicalRecipeSupport.key(output.right());
                if (left == null || right == null) continue;
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), "separating_"
                                + fluidId.getNamespace() + "_" + fluidId.getPath()),
                        List.of(), List.of(fluid), List.of(), List.of(), List.of(), List.of(left, right),
                        AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK,
                                PROCESS_TICKS, Math.max(1L, original.getEnergyMultiplier())),
                        AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<ElectrolysisRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<appeng.api.stacks.AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedFluids.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ElectrolysisRecipe>> result = new ArrayList<>();
        for (RecipeHolder<ElectrolysisRecipe> holder : manager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_SEPARATING.value())) {
            if (MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, holder.value().getInput())) {
                result.add(holder);
            }
        }
        return result;
    }
}
