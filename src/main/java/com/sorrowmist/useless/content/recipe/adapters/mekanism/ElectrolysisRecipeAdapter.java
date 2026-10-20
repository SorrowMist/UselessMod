package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.recipes.ElectrolysisRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.common.registries.MekanismBlocks;
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
        return new ItemStack(MekanismBlocks.ELECTROLYTIC_SEPARATOR.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ElectrolysisRecipe> holder, Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeType.SEPARATING.get()) {
            return List.of();
        }
        ElectrolysisRecipe original = holder.value();
        FluidStackIngredient input = original.getInput();
        if (input == null || input.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient> fluidInputs =
                MekanismChemicalRecipeSupport.fluidIngredients(input);
        for (ElectrolysisRecipe.ElectrolysisRecipeOutput output : original.getOutputDefinition()) {
            GenericStack left = MekanismChemicalRecipeSupport.key(output.left());
            GenericStack right = MekanismChemicalRecipeSupport.key(output.right());
            if (left == null || right == null) continue;
            result.add(MekanismChemicalRecipeSupport.recipe(
                    MekanismChemicalRecipeSupport.variantId(holder.id(), "separating"),
                    List.of(), fluidInputs, List.of(), List.of(), List.of(), List.of(left, right),
                    MekanismChemicalRecipeSupport.energyCost(ENERGY_PER_TICK,PROCESS_TICKS,original.getEnergyMultiplier()),
                    AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
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
        for (RecipeHolder<ElectrolysisRecipe> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,ElectrolysisRecipe>getAllRecipesFor(manager,
                MekanismRecipeType.SEPARATING.get())) {
            if (MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, holder.value().getInput())) {
                result.add(holder);
            }
        }
        return result;
    }
}
