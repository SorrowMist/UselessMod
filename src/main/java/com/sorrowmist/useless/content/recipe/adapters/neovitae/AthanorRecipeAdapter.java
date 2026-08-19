package com.sorrowmist.useless.content.recipe.adapters.neovitae;

import com.breakinblocks.neovitae.common.recipe.NVRecipes;
import com.breakinblocks.neovitae.common.recipe.athanor.AthanorRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts NeoVitae Athanor (alchemical furnace) recipes into alloy-furnace recipes.
 *
 * <p>The athanor combines a tool, item inputs, an optional input fluid and optional spiritus with
 * guaranteed and chance outputs. The alloy furnace is deterministic, so only the guaranteed item
 * outputs are produced; chance outputs are intentionally dropped. Spiritus costs are paid as FE.</p>
 */
public final class AthanorRecipeAdapter implements IRecipeAdapter<AthanorRecipe> {

    /** 1 spiritus point is mapped to 1000 FE. */
    private static final double SPIRITUS_TO_FE = 1000D;
    /** 1 mB of Essentia Vitae (life essence) is mapped to 1 FE. */
    private static final double EV_MB_TO_FE = 1D;
    private static final int PROCESS_TIME = 200;

    @Override
    public Class<AthanorRecipe> getRecipeClass() {
        return AthanorRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return NeoVitaeAdapterUtils.item("athanor");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<AthanorRecipe> holder, Level level) {
        AthanorRecipe source = holder == null ? null : holder.value();
        if (source == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack output : source.getGuaranteedOutput()) {
            if (output != null && !output.isEmpty() && output.getCount() > 0) {
                outputs.add(output.copy());
            }
        }
        if (outputs.isEmpty()) {
            // Chance-only recipes cannot be represented deterministically.
            return List.of();
        }

        List<CountedIngredient> inputs = new ArrayList<>();
        if (source.getTool() != null && !source.getTool().isEmpty()) {
            inputs.add(new CountedIngredient(source.getTool(), 1));
        }
        Map<Ingredient, Long> mergedInputs = new LinkedHashMap<>();
        for (Ingredient ingredient : source.getInputs()) {
            if (ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(mergedInputs, ingredient, 1L);
            }
        }
        for (Map.Entry<Ingredient, Long> entry : mergedInputs.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<SizedFluidIngredient> inputFluids = effectiveInputFluids(source);
        List<FluidStack> outputFluids = new ArrayList<>();
        source.getOutputFluid().ifPresent(fluid -> {
            if (!fluid.isEmpty()) {
                outputFluids.add(fluid.copy());
            }
        });

        double spiritusCost = source.getSpiritusCosts() == null ? 0D
                : source.getSpiritusCosts().values().stream().mapToDouble(Double::doubleValue).sum();
        long essentiaVitaeCost = Math.round(essentiaVitaeAmount(source) * EV_MB_TO_FE);
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY,
                (long) Math.ceil(spiritusCost * SPIRITUS_TO_FE) + essentiaVitaeCost);

        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                inputFluids,
                outputs,
                outputFluids,
                energy,
                PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        ));
    }

    @Override
    public List<RecipeHolder<AthanorRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }
        List<RecipeHolder<AthanorRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<AthanorRecipe> holder : manager.getAllRecipesFor(NVRecipes.ATHANOR_TYPE.get())) {
            AthanorRecipe source = holder.value();
            if (source == null) {
                continue;
            }
            Map<Ingredient, Long> required = requirements(source);
            if (required == null || !AdapterUtils.matchesRequired(mergedInputs, required)) {
                continue;
            }
            List<SizedFluidIngredient> requiredFluids = effectiveInputFluids(source);
            if (AdapterUtils.matchesFluidIngredients(mergedFluids, requiredFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Map<Ingredient, Long> requirements(AthanorRecipe source) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        if (source.getTool() != null && !source.getTool().isEmpty()) {
            AdapterUtils.mergeIngredient(required, source.getTool(), 1L);
        }
        for (Ingredient ingredient : source.getInputs()) {
            if (ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(required, ingredient, 1L);
            }
        }
        return required.isEmpty() ? null : required;
    }

    /**
     * Returns the recipe's fluid inputs with Essentia Vitae removed. Essentia Vitae is paid as FE
     * energy (see {@link #essentiaVitaeAmount}), while ordinary fluids such as water are kept.
     */
    private static List<SizedFluidIngredient> effectiveInputFluids(AthanorRecipe source) {
        if (source.getInputFluid().isEmpty()) {
            return List.of();
        }
        SizedFluidIngredient fluid = source.getInputFluid().get();
        if (NeoVitaeAdapterUtils.isEssentiaVitae(fluid.ingredient())) {
            return List.of();
        }
        return List.of(fluid);
    }

    /** Returns the amount (mB) of Essentia Vitae required by the recipe, or 0 when none. */
    private static long essentiaVitaeAmount(AthanorRecipe source) {
        if (source.getInputFluid().isEmpty()) {
            return 0L;
        }
        SizedFluidIngredient fluid = source.getInputFluid().get();
        return NeoVitaeAdapterUtils.isEssentiaVitae(fluid.ingredient()) ? fluid.amount() : 0L;
    }
}
