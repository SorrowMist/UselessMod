package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.PressurizedReactionRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
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

/** Item + fluid + chemical -> item and/or chemical adapter for PRCs. */
public final class PressurizedReactionRecipeAdapter implements IRecipeAdapter<PressurizedReactionRecipe> {
    private static final long BASE_ENERGY_PER_TICK = 5L;

    @Override
    public Class<PressurizedReactionRecipe> getRecipeClass() {
        return PressurizedReactionRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return new ItemStack(MekanismBlocks.PRESSURIZED_REACTION_CHAMBER.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<PressurizedReactionRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != MekanismRecipeTypes.TYPE_REACTION.value()) {
            return List.of();
        }
        PressurizedReactionRecipe original = holder.value();
        ItemStackIngredient itemInput = original.getInputSolid();
        FluidStackIngredient fluidInput = original.getInputFluid();
        ChemicalStackIngredient chemicalInput = original.getInputChemical();
        if (itemInput == null || fluidInput == null || chemicalInput == null
                || itemInput.hasNoMatchingInstances() || fluidInput.hasNoMatchingInstances()
                || chemicalInput.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> fluidInputs =
                MekanismChemicalRecipeSupport.fluidIngredients(fluidInput);
        for (ChemicalStack chemical : chemicalInput.getRepresentations()) {
            GenericStack chemicalKey = MekanismChemicalRecipeSupport.key(chemical);
            if (chemicalKey == null) continue;
            for (PressurizedReactionRecipe.PressurizedReactionRecipeOutput output
                    : original.getOutputDefinition()) {
                List<GenericStack> chemicalOutputs = new ArrayList<>();
                if (!output.chemical().isEmpty()) {
                    GenericStack outputKey = MekanismChemicalRecipeSupport.key(output.chemical());
                    if (outputKey != null) chemicalOutputs.add(outputKey);
                }
                if (output.item().isEmpty() && chemicalOutputs.isEmpty()) continue;

                String suffix = "reaction_" + id(chemical) + "_out";
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), suffix),
                        MekanismChemicalRecipeSupport.items(itemInput), fluidInputs, List.of(chemicalKey),
                        output.item().isEmpty() ? List.of() : List.of(output.item().copy()), List.of(),
                        chemicalOutputs, reactionEnergy(original), original.getDuration(), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<PressurizedReactionRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || mergedFluids.isEmpty() || mergedKeys.isEmpty()
                || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<PressurizedReactionRecipe>> result = new ArrayList<>();
        for (RecipeHolder<PressurizedReactionRecipe> holder : manager.getAllRecipesFor(
                MekanismRecipeTypes.TYPE_REACTION.value())) {
            PressurizedReactionRecipe recipe = holder.value();
            if (MekanismChemicalRecipeSupport.matchesItem(mergedInputs, recipe.getInputSolid())
                    && MekanismChemicalRecipeSupport.matchesFluid(mergedFluids, recipe.getInputFluid())
                    && MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, recipe.getInputChemical())) {
                result.add(holder);
            }
        }
        return result;
    }

    private static long reactionEnergy(PressurizedReactionRecipe recipe) {
        long energyPerTick = MekanismChemicalRecipeSupport.saturatingAdd(
                BASE_ENERGY_PER_TICK, recipe.getEnergyRequired());
        return MekanismChemicalRecipeSupport.saturatingMultiply(energyPerTick, recipe.getDuration());
    }

    private static String id(ChemicalStack stack) {
        var id = stack.getChemicalHolder().getKey().location();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static String fluidId(FluidStack stack) {
        var id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
