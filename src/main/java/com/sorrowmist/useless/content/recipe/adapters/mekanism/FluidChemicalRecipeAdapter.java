package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.chemical.FluidChemicalToChemicalRecipe;
import mekanism.common.recipe.MekanismRecipeType;
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

/** Fluid + chemical -> chemical adapter for the Chemical Washer. */
public final class FluidChemicalRecipeAdapter implements IRecipeAdapter<FluidChemicalToChemicalRecipe<?,?,?>> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 200L;

    @Override
    public Class<FluidChemicalToChemicalRecipe<?,?,?>> getRecipeClass() {
        return (Class) FluidChemicalToChemicalRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.CHEMICAL_WASHER.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<FluidChemicalToChemicalRecipe<?,?,?>> holder,
                                                       Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeType.WASHING.get()) {
            return List.of();
        }
        FluidChemicalToChemicalRecipe<?,?,?> original = holder.value();
        FluidStackIngredient fluidInput = original.getFluidInput();
        ChemicalStackIngredient<?,?> chemicalInput = original.getChemicalInput();
        if (fluidInput == null || chemicalInput == null || fluidInput.hasNoMatchingInstances()
                || chemicalInput.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient> fluidInputs =
                MekanismChemicalRecipeSupport.fluidIngredients(fluidInput);
        for (ChemicalStack<?> chemical : chemicalInput.getRepresentations()) {
            GenericStack chemicalKey = MekanismChemicalRecipeSupport.key(chemical);
            if (chemicalKey == null) continue;
            for (ChemicalStack<?> output : original.getOutputDefinition()) {
                GenericStack outputKey = MekanismChemicalRecipeSupport.key(output);
                if (outputKey == null) continue;
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), "washer_" + id(chemical)
                                + "_out_" + id(output)), List.of(), fluidInputs, List.of(chemicalKey),
                        List.of(), List.of(), List.of(outputKey),
                        AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                        AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<FluidChemicalToChemicalRecipe<?,?,?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedFluids.isEmpty() || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<FluidChemicalToChemicalRecipe<?,?,?>>> result = new ArrayList<>();
        for (RecipeHolder<FluidChemicalToChemicalRecipe<?,?,?>> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,FluidChemicalToChemicalRecipe<?,?,?>>getAllRecipesFor(manager,
                MekanismRecipeType.WASHING.get())) {
            FluidChemicalToChemicalRecipe<?,?,?> recipe = holder.value();
            if (MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, recipe.getFluidInput())
                    && MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, recipe.getChemicalInput())) {
                result.add(holder);
            }
        }
        return result;
    }

    private static String id(ChemicalStack<?> stack) {
        ResourceLocation id = stack.getTypeRegistryName();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
