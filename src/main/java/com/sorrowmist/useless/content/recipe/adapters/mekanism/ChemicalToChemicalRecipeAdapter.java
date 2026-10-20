package com.sorrowmist.useless.content.recipe.adapters.mekanism;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.chemical.ChemicalToChemicalRecipe;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import com.sorrowmist.useless.compat.forge1201.recipe.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Chemical -> chemical adapter shared by the isotope centrifuge and solar activator. */
public final class ChemicalToChemicalRecipeAdapter implements IRecipeAdapter<ChemicalToChemicalRecipe<?,?,?>> {
    private static final long PROCESS_TICKS = AdapterUtils.MEKANISM_BASE_TICKS_REQUIRED;
    private static final long ENERGY_PER_TICK = 200L;

    private final RecipeType<? extends ChemicalToChemicalRecipe<?,?,?>> recipeType;
    private final ItemStack mold;
    private final String name;

    private ChemicalToChemicalRecipeAdapter(RecipeType<? extends ChemicalToChemicalRecipe<?,?,?>> recipeType,
                                            ItemStack mold, String name) {
        this.recipeType = recipeType;
        this.mold = mold.copy();
        this.name = name;
    }

    public static ChemicalToChemicalRecipeAdapter isotopicCentrifuge() {
        return new ChemicalToChemicalRecipeAdapter(
                MekanismRecipeType.CENTRIFUGING.get(),
                new ItemStack(MekanismBlocks.ISOTOPIC_CENTRIFUGE.getBlock()), "isotopic_centrifuge");
    }

    public static ChemicalToChemicalRecipeAdapter solarNeutronActivator() {
        return new ChemicalToChemicalRecipeAdapter(
                MekanismRecipeType.ACTIVATING.get(),
                new ItemStack(MekanismBlocks.SOLAR_NEUTRON_ACTIVATOR.getBlock()), "solar_neutron_activator");
    }

    @Override
    public Class<ChemicalToChemicalRecipe<?,?,?>> getRecipeClass() {
        return (Class) ChemicalToChemicalRecipe.class;
    }

    @Override
    public @Nullable ItemStack getMoldItem() {
        return mold.copy();
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<ChemicalToChemicalRecipe<?,?,?>> holder, Level level) {
        if (holder != null && NativeMekanismVariants.needsExpansion(holder.value()))
            return NativeMekanismVariants.expand(holder.value(), () -> convertAll(holder, level));
        if (holder == null || level == null || holder.value().getType() != recipeType) return List.of();
        ChemicalToChemicalRecipe<?,?,?> original = holder.value();
        ChemicalStackIngredient<?,?> input = original.getInput();
        if (input == null || input.hasNoMatchingInstances()) return List.of();

        List<AdvancedAlloyFurnaceRecipe> result = new ArrayList<>();
        for (ChemicalStack<?> inputStack : input.getRepresentations()) {
            GenericStack inputKey = MekanismChemicalRecipeSupport.key(inputStack);
            if (inputKey == null) continue;
            for (ChemicalStack<?> outputStack : original.getOutputDefinition()) {
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
    public List<RecipeHolder<ChemicalToChemicalRecipe<?,?,?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            Map<AEKey, Long> mergedKeys, @Nullable ItemStack mold) {
        if (level == null || mergedKeys.isEmpty() || !matchesMold(mold)) return List.of();
        RecipeManager manager = level.getRecipeManager();
        List<RecipeHolder<ChemicalToChemicalRecipe<?,?,?>>> result = new ArrayList<>();
        for (RecipeHolder<ChemicalToChemicalRecipe<?,?,?>> holder : com.sorrowmist.useless.compat.forge1201.recipe.RecipeManagerAccess.<mekanism.api.inventory.IgnoredIInventory,ChemicalToChemicalRecipe<?,?,?>>getAllRecipesFor(manager, recipeType)) {
            if (MekanismChemicalRecipeSupport.matchesChemical(mergedKeys, holder.value().getInput())) {
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
