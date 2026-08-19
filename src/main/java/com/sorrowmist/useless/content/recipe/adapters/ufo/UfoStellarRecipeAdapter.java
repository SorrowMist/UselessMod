package com.sorrowmist.useless.content.recipe.adapters.ufo;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.StellarSimulationRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.pedroksl.ae2addonlib.recipes.IngredientStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts UFO Future Stellar Nexus (Stellar Simulation) recipes into alloy-furnace recipes.
 *
 * <p>Stellar recipes use very large amounts (millions of mB), which exceed the alloy furnace's
 * fluid tanks. All amounts are therefore scaled down proportionally so the largest fluid input
 * fits in a base furnace tank, while the input:output ratio is preserved.</p>
 */
public final class UfoStellarRecipeAdapter implements IRecipeAdapter<StellarSimulationRecipe> {

    private static final long TARGET_FLUID = 8000;

    @Override
    public Class<StellarSimulationRecipe> getRecipeClass() {
        return StellarSimulationRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return UfoAdapterUtils.item("stellar_nexus_controller");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<StellarSimulationRecipe> holder, Level level) {
        StellarSimulationRecipe source = holder == null ? null : holder.value();
        if (source == null) {
            return List.of();
        }
        double scale = fluidScale(source);

        List<ItemStack> outputs = new ArrayList<>();
        for (GenericStack output : source.getItemOutputs()) {
            if (output != null && output.what() instanceof AEItemKey key) {
                long amount = scaled(output.amount(), scale);
                outputs.add(key.toStack((int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }
        List<FluidStack> outputFluids = new ArrayList<>();
        for (GenericStack output : source.getFluidOutputs()) {
            if (output != null && output.what() instanceof AEFluidKey key) {
                long amount = scaled(output.amount(), scale);
                outputFluids.add(key.toStack((int) Math.min(Integer.MAX_VALUE, amount)));
            }
        }
        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            return List.of();
        }

        Map<Ingredient, Long> merged = new LinkedHashMap<>();
        for (IngredientStack.Item input : source.getItemInputs()) {
            if (input != null && !input.isEmpty() && input.getIngredient() != null && !input.getIngredient().isEmpty()) {
                AdapterUtils.mergeIngredient(merged, input.getIngredient(), scaled(input.getAmount(), scale));
            }
        }
        List<CountedIngredient> inputs = new ArrayList<>(merged.size());
        for (Map.Entry<Ingredient, Long> entry : merged.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        for (IngredientStack.Fluid input : source.getFluidInputs()) {
            if (input != null && !input.isEmpty() && input.getIngredient() != null) {
                inputFluids.add(new SizedFluidIngredient(input.getIngredient(), (int) scaled(input.getAmount(), scale)));
            }
        }

        // Fuel and coolant are internal Stellar Nexus consumables, not recipe ingredients.
        // Requiring them as extra fluid inputs made the recipe uncraftable for players who do
        // not keep those exotic fluids on hand. Fold their cost into FE instead, matching the
        // NeoVitae / Athanor "machine-internal fluid -> FE" convention.
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, (long) Math.max(1, source.getEnergyCost() / scale));
        energy += Math.max(0L, scaled(source.getFuelAmount(), scale));
        energy += Math.max(0L, scaled(source.getCoolantAmount(), scale));

        // The recipe output is scaled down by the same factor, so scale the crafting time as well;
        // otherwise the furnace would be hundreds of times slower than the original machine.
        int processTime = Math.max(1, (int) Math.ceil(source.getTime() / scale));

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                inputFluids,
                outputs,
                outputFluids,
                energy,
                processTime,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<StellarSimulationRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) {
            return List.of();
        }
        boolean hasItems = mergedInputs != null && !mergedInputs.isEmpty();
        boolean hasFluids = mergedFluids != null && !mergedFluids.isEmpty();
        if (!hasItems && !hasFluids) {
            return List.of();
        }

        List<RecipeHolder<StellarSimulationRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<StellarSimulationRecipe> holder : manager.getAllRecipesFor(ModRecipes.STELLAR_SIMULATION_TYPE.get())) {
            StellarSimulationRecipe source = holder.value();
            if (source == null) {
                continue;
            }
            double scale = fluidScale(source);

            Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
            for (IngredientStack.Item input : source.getItemInputs()) {
                if (input != null && !input.isEmpty() && input.getIngredient() != null && !input.getIngredient().isEmpty()) {
                    AdapterUtils.mergeIngredient(requiredItems, input.getIngredient(), scaled(input.getAmount(), scale));
                }
            }
            if (!requiredItems.isEmpty() && !AdapterUtils.matchesRequired(mergedInputs, requiredItems)) {
                continue;
            }

            List<SizedFluidIngredient> requiredFluids = new ArrayList<>();
            for (IngredientStack.Fluid input : source.getFluidInputs()) {
                if (input != null && !input.isEmpty() && input.getIngredient() != null) {
                    requiredFluids.add(new SizedFluidIngredient(input.getIngredient(), (int) scaled(input.getAmount(), scale)));
                }
            }
            if (AdapterUtils.matchesFluidIngredients(mergedFluids, requiredFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static double fluidScale(StellarSimulationRecipe source) {
        long maxFluid = 0;
        for (IngredientStack.Fluid input : source.getFluidInputs()) {
            if (input != null) {
                maxFluid = Math.max(maxFluid, input.getAmount());
            }
        }
        maxFluid = Math.max(maxFluid, source.getFuelAmount());
        maxFluid = Math.max(maxFluid, source.getCoolantAmount());
        return maxFluid > TARGET_FLUID ? (double) maxFluid / TARGET_FLUID : 1.0;
    }

    private static long scaled(long amount, double scale) {
        if (amount <= 0) {
            return 0;
        }
        return Math.max(1, (long) Math.ceil(amount / scale));
    }
}
