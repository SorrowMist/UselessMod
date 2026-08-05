package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ChemicalCrystallizerRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
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

/** Chemical -> item adapter for the Chemical Crystallizer. */
public final class ChemicalCrystallizerRecipeAdapter implements IRecipeAdapter<ChemicalCrystallizerRecipe> {
    private static final long PROCESS_TICKS = 10L * 20L;
    private static final long ENERGY_PER_TICK = 400L;

    @Override
    public Class<ChemicalCrystallizerRecipe> getRecipeClass() {
        return ChemicalCrystallizerRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.CHEMICAL_CRYSTALLIZER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChemicalCrystallizerRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_CRYSTALLIZING.value()) {
            return List.of();
        }
        ChemicalCrystallizerRecipe original = holder.value();
        ChemicalStackIngredient input = original.getInput();
        if (input == null || input.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack chemical : input.getRepresentations()) {
            GenericStack inputKey = MekanismChemicalRecipeSupport.key(chemical);
            if (inputKey == null) continue;
            for (ItemStack output : original.getOutputDefinition()) {
                if (output.isEmpty()) continue;
                ResourceLocation chemicalId = chemical.getChemicalHolder().getKey().location();
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), "crystallizing_"
                                + chemicalId.getNamespace() + "_" + chemicalId.getPath()),
                        List.of(), List.of(), List.of(inputKey), List.of(output.copy()), List.of(), List.of(),
                        AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                        AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<ChemicalCrystallizerRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ChemicalCrystallizerRecipe>> result = new ArrayList<>();
        for (RecipeHolder<ChemicalCrystallizerRecipe> holder : manager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_CRYSTALLIZING.value())) {
            if (MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, holder.value().getInput())) {
                result.add(holder);
            }
        }
        return result;
    }
}
