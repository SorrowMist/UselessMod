package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.ChemicalToChemicalRecipe;
import mekanism.api.recipes.MekanismRecipeTypes;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chemical -> chemical adapter shared by the isotope centrifuge and solar activator. */
public final class ChemicalToChemicalRecipeAdapter implements IRecipeAdapter<ChemicalToChemicalRecipe> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 200L;

    private final RecipeType<ChemicalToChemicalRecipe> recipeType;
    private final ItemStack mold;
    private final String name;

    private ChemicalToChemicalRecipeAdapter(RecipeType<ChemicalToChemicalRecipe> recipeType,
                                            ItemStack mold, String name) {
        this.recipeType = recipeType;
        this.mold = mold.copy();
        this.name = name;
    }

    public static ChemicalToChemicalRecipeAdapter isotopicCentrifuge() {
        return new ChemicalToChemicalRecipeAdapter(
                MekanismRecipeTypes.TYPE_CENTRIFUGING.value(),
                new ItemStack(MekanismBlocks.ISOTOPIC_CENTRIFUGE.get()), "isotopic_centrifuge");
    }

    public static ChemicalToChemicalRecipeAdapter solarNeutronActivator() {
        return new ChemicalToChemicalRecipeAdapter(
                MekanismRecipeTypes.TYPE_ACTIVATING.value(),
                new ItemStack(MekanismBlocks.SOLAR_NEUTRON_ACTIVATOR.get()), "solar_neutron_activator");
    }

    @Override
    public Class<ChemicalToChemicalRecipe> getRecipeClass() {
        return ChemicalToChemicalRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return mold.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChemicalToChemicalRecipe> holder, Level level) {
        if (holder == null || level == null || holder.value().getType() != recipeType) return List.of();
        ChemicalToChemicalRecipe original = holder.value();
        ChemicalStackIngredient input = original.getInput();
        if (input == null || input.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack inputStack : input.getRepresentations()) {
            GenericStack inputKey = MekanismChemicalRecipeSupport.key(inputStack);
            if (inputKey == null) continue;
            for (ChemicalStack outputStack : original.getOutputDefinition()) {
                GenericStack outputKey = MekanismChemicalRecipeSupport.key(outputStack);
                if (outputKey == null) continue;
                result.add(MekanismChemicalRecipeSupport.recipe(
                        MekanismChemicalRecipeSupport.variantId(holder.id(), name + "_"
                                + id(inputStack) + "_out_" + id(outputStack)),
                        List.of(), List.of(), List.of(inputKey), List.of(), List.of(), List.of(outputKey),
                        AdapterUtils.mekanismEnergyCost(ENERGY_PER_TICK, PROCESS_TICKS, 1L),
                        AdapterUtils.safeInt(PROCESS_TICKS), getMoldItem()));
            }
        }
        return result;
    }

    @Override
    public List<RecipeHolder<ChemicalToChemicalRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ChemicalToChemicalRecipe>> result = new ArrayList<>();
        for (RecipeHolder<ChemicalToChemicalRecipe> holder : manager.getAllRecipesFor(recipeType)) {
            if (MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, holder.value().getInput())) {
                result.add(holder);
            }
        }
        return result;
    }

    private static String id(ChemicalStack stack) {
        ResourceLocation id = stack.getChemicalHolder().getKey().location();
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }
}
