package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.NucleosynthesizingRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
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

/** Native-duration adapter for the Antiprotonic Nucleosynthesizer. */
public final class NucleosynthesizingRecipeAdapter implements IRecipeAdapter<NucleosynthesizingRecipe> {
    private static final long ENERGY_PER_TICK = 100_000L;

    @Override
    public Class<NucleosynthesizingRecipe> getRecipeClass() {
        return NucleosynthesizingRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.ANTIPROTONIC_NUCLEOSYNTHESIZER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<NucleosynthesizingRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_NUCLEOSYNTHESIZING.value()) {
            return List.of();
        }
        NucleosynthesizingRecipe original = holder.value();
        ItemStackIngredient itemInput = original.getItemInput();
        ChemicalStackIngredient chemicalInput = original.getChemicalInput();
        if (MekanismChemicalRecipeSupport.item(itemInput) == null || chemicalInput == null
                || chemicalInput.hasNoMatchingInstances() || original.getOutputDefinition().isEmpty()) return List.of();

        long duration = Math.max(1L, original.getDuration());
        long multiplier = original.perTickUsage() ? duration : 1L;
        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack chemical : chemicalInput.getRepresentations()) {
            GenericStack inputKey = MekanismChemicalRecipeSupport.key(chemical.copyWithAmount(
                    MekanismChemicalRecipeSupport.saturatingMultiply(chemical.getAmount(), multiplier)));
            if (inputKey == null) continue;
            for (ItemStack output : original.getOutputDefinition()) {
                if (output.isEmpty()) continue;
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), "nucleosynthesizing_"
                                + id(chemical)), MekanismChemicalRecipeSupport.items(itemInput), List.of(),
                        List.of(inputKey), List.of(output.copy()), List.of(), List.of(),
                        MekanismChemicalRecipeSupport.saturatingMultiply(ENERGY_PER_TICK, duration),
                        AdapterUtils.safeInt(duration), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<NucleosynthesizingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<NucleosynthesizingRecipe>> result = new ArrayList<>();
        for (RecipeHolder<NucleosynthesizingRecipe> holder : manager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_NUCLEOSYNTHESIZING.value())) {
            NucleosynthesizingRecipe recipe = holder.value();
            if (!MekanismChemicalRecipeSupport.matchesItem(mergedInputs, recipe.getItemInput())) continue;
            long multiplier = recipe.perTickUsage() ? Math.max(1L, recipe.getDuration()) : 1L;
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

    private static String id(ChemicalStack stack) {
        var id = stack.getChemicalHolder().getKey().location();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
