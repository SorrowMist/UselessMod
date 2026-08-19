package com.sorrowmist.useless.content.recipe.adapters.oritech;

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
import rearth.oritech.init.recipes.OritechRecipe;
import rearth.oritech.init.recipes.OritechRecipeType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts one Oritech machine's recipes into alloy-furnace recipes.
 *
 * <p>Oritech uses a single {@link OritechRecipe} class across every machine, so this adapter is
 * instantiated once per machine. Both {@link #convertAll} and {@link #findMatchingRecipes} verify
 * the recipe's {@link OritechRecipeType} to keep each machine's recipes under its own mold.</p>
 *
 * <p>Fluid-only recipes (e.g. the refinery) are supported: their fluid inputs/outputs are kept and
 * the recipe is valid even when the item result list is empty.</p>
 */
public final class OritechMachineRecipeAdapter implements IRecipeAdapter<OritechRecipe> {

    /** 1 Oritech recipe tick is mapped to 20 FE of base energy. */
    private static final long TIME_TO_FE = 20L;

    private final OritechRecipeType recipeType;
    private final ItemStack moldItem;

    public OritechMachineRecipeAdapter(OritechRecipeType recipeType, String moldBlockPath) {
        this.recipeType = recipeType;
        this.moldItem = OritechAdapterUtils.item(moldBlockPath);
    }

    @Override
    public Class<OritechRecipe> getRecipeClass() {
        return OritechRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return moldItem;
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<OritechRecipe> holder, Level level) {
        OritechRecipe source = holder == null ? null : holder.value();
        if (source == null || !isOwnRecipe(source)) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (ItemStack output : source.getResults()) {
            if (output != null && !output.isEmpty() && output.getCount() > 0) {
                outputs.add(output.copy());
            }
        }

        List<FluidStack> outputFluids = new ArrayList<>();
        if (source.getFluidOutputs() != null) {
            for (dev.architectury.fluid.FluidStack output : source.getFluidOutputs()) {
                FluidStack converted = OritechAdapterUtils.toNeoForgeOutput(output);
                if (!converted.isEmpty()) {
                    outputFluids.add(converted);
                }
            }
        }

        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            return List.of();
        }

        List<CountedIngredient> inputs = new ArrayList<>();
        Map<Ingredient, Long> mergedInputs = new LinkedHashMap<>();
        for (Ingredient ingredient : source.getInputs()) {
            if (ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(mergedInputs, ingredient, 1L);
            }
        }
        for (Map.Entry<Ingredient, Long> entry : mergedInputs.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        SizedFluidIngredient fluidInput = OritechAdapterUtils.toNeoForgeInput(source.getFluidInput());
        if (fluidInput != null) {
            inputFluids.add(fluidInput);
        }

        int processTime = Math.max(1, source.getTime());
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, (long) source.getTime() * TIME_TO_FE);

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
    public List<RecipeHolder<OritechRecipe>> findMatchingRecipes(
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

        List<RecipeHolder<OritechRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<OritechRecipe> holder : manager.getAllRecipesFor(this.recipeType)) {
            OritechRecipe source = holder.value();
            if (source == null || !isOwnRecipe(source)) {
                continue;
            }

            Map<Ingredient, Long> requiredItems = requirements(source);
            if (requiredItems != null && !AdapterUtils.matchesRequired(mergedInputs, requiredItems)) {
                continue;
            }

            List<SizedFluidIngredient> requiredFluids = new ArrayList<>();
            SizedFluidIngredient fluidInput = OritechAdapterUtils.toNeoForgeInput(source.getFluidInput());
            if (fluidInput != null) {
                requiredFluids.add(fluidInput);
            }
            if (AdapterUtils.matchesFluidIngredients(mergedFluids, requiredFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private boolean isOwnRecipe(OritechRecipe source) {
        OritechRecipeType type = source.getOriType();
        if (type == null) {
            return false;
        }
        if (type == this.recipeType) {
            return true;
        }
        ResourceLocation sourceId = type.getIdentifier();
        ResourceLocation ownId = this.recipeType.getIdentifier();
        return sourceId != null && sourceId.equals(ownId);
    }

    @Nullable
    private static Map<Ingredient, Long> requirements(OritechRecipe source) {
        Map<Ingredient, Long> required = new LinkedHashMap<>();
        for (Ingredient ingredient : source.getInputs()) {
            if (ingredient != null && !ingredient.isEmpty()) {
                AdapterUtils.mergeIngredient(required, ingredient, 1L);
            }
        }
        return required.isEmpty() ? null : required;
    }
}
