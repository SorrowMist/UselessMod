package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ChemicalDissolutionRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
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

/** Item + chemical -> chemical adapter for the Chemical Dissolution Chamber. */
public final class ChemicalDissolutionRecipeAdapter implements IRecipeAdapter<ChemicalDissolutionRecipe> {
    private static final long PROCESS_TICKS = 5L * 20L;
    private static final long ENERGY_PER_TICK = 400L;

    @Override
    public Class<ChemicalDissolutionRecipe> getRecipeClass() {
        return ChemicalDissolutionRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.CHEMICAL_DISSOLUTION_CHAMBER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChemicalDissolutionRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_DISSOLUTION.value()) {
            return List.of();
        }
        ChemicalDissolutionRecipe original = holder.value();
        ItemStackIngredient itemInput = original.getItemInput();
        ChemicalStackIngredient chemicalInput = original.getChemicalInput();
        if (itemInput == null || chemicalInput == null || itemInput.hasNoMatchingInstances()
                || chemicalInput.hasNoMatchingInstances()) return List.of();

        long multiplier = original.perTickUsage() ? PROCESS_TICKS : 1L;
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack chemical : chemicalInput.getRepresentations()) {
            GenericStack inputKey = MekanismChemicalRecipeSupport.key(chemical.copyWithAmount(
                    MekanismChemicalRecipeSupport.saturatingMultiply(chemical.getAmount(), multiplier)));
            if (inputKey == null) continue;
            for (ChemicalStack output : original.getOutputDefinition()) {
                GenericStack outputKey = MekanismChemicalRecipeSupport.key(output);
                if (outputKey == null) continue;
                ResourceLocation chemicalId = chemical.getChemicalHolder().getKey().location();
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), "dissolution_"
                                + chemicalId.getNamespace() + "_" + chemicalId.getPath()),
                        MekanismChemicalRecipeSupport.items(itemInput), List.of(), List.of(inputKey),
                        List.of(), List.of(), List.of(outputKey),
                        AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                        AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<ChemicalDissolutionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ChemicalDissolutionRecipe>> result = new ArrayList<>();
        for (RecipeHolder<ChemicalDissolutionRecipe> holder : manager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_DISSOLUTION.value())) {
            ChemicalDissolutionRecipe recipe = holder.value();
            if (!MekanismChemicalRecipeSupport.matchesItem(mergedInputs, recipe.getItemInput())) continue;
            long multiplier = recipe.perTickUsage() ? PROCESS_TICKS : 1L;
            boolean match = false;
            for (ChemicalStack chemical : recipe.getChemicalInput().getRepresentations()) {
                GenericStack key = MekanismChemicalRecipeSupport.key(chemical.copyWithAmount(
                        MekanismChemicalRecipeSupport.saturatingMultiply(chemical.getAmount(), multiplier)));
                if (key != null && mergedKeys.getOrDefault(key.what(), 0L) >= key.amount()) {
                    match = true;
                    break;
                }
            }
            if (match) result.add(holder);
        }
        return result;
    }
}
