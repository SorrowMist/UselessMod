package com.sorrowmist.useless.content.recipe.adapters.ufo;

import com.raishxn.ufo.init.ModRecipes;
import com.raishxn.ufo.recipe.DimensionalMatterAssemblerRecipe;
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

/** Converts UFO Future Dimensional Matter Assembler recipes into alloy-furnace recipes. */
public final class UfoDmaRecipeAdapter implements IRecipeAdapter<DimensionalMatterAssemblerRecipe> {

    @Override
    public Class<DimensionalMatterAssemblerRecipe> getRecipeClass() {
        return DimensionalMatterAssemblerRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return UfoAdapterUtils.item("dimensional_matter_assembler");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<DimensionalMatterAssemblerRecipe> holder, Level level) {
        DimensionalMatterAssemblerRecipe source = holder == null ? null : holder.value();
        if (source == null) {
            return List.of();
        }

        List<ItemStack> outputs = new ArrayList<>();
        for (var output : source.getItemOutputs()) {
            ItemStack stack = UfoAdapterUtils.toItemStack(output);
            if (!stack.isEmpty()) {
                outputs.add(stack);
            }
        }
        List<FluidStack> outputFluids = new ArrayList<>();
        for (var output : source.getFluidOutputs()) {
            FluidStack stack = UfoAdapterUtils.toFluidStack(output);
            if (!stack.isEmpty()) {
                outputFluids.add(stack);
            }
        }
        if (outputs.isEmpty() && outputFluids.isEmpty()) {
            return List.of();
        }

        Map<Ingredient, Long> merged = new LinkedHashMap<>();
        for (IngredientStack.Item input : source.getItemInputs()) {
            if (input != null && !input.isEmpty() && input.getIngredient() != null && !input.getIngredient().isEmpty()) {
                AdapterUtils.mergeIngredient(merged, input.getIngredient(), Math.max(1, input.getAmount()));
            }
        }
        List<CountedIngredient> inputs = new ArrayList<>(merged.size());
        for (Map.Entry<Ingredient, Long> entry : merged.entrySet()) {
            inputs.add(new CountedIngredient(entry.getKey(), entry.getValue()));
        }

        List<SizedFluidIngredient> inputFluids = new ArrayList<>();
        for (IngredientStack.Fluid input : source.getFluidInputs()) {
            if (input != null && !input.isEmpty() && input.getIngredient() != null) {
                inputFluids.add(new SizedFluidIngredient(input.getIngredient(), Math.max(1, input.getAmount())));
            }
        }

        int processTime = Math.max(1, source.getTime());
        long energy = Math.max(AdapterUtils.DEFAULT_ENERGY, source.getEnergy());

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
    public List<RecipeHolder<DimensionalMatterAssemblerRecipe>> findMatchingRecipes(
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

        List<RecipeHolder<DimensionalMatterAssemblerRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<DimensionalMatterAssemblerRecipe> holder : manager.getAllRecipesFor(ModRecipes.DMA_RECIPE_TYPE.get())) {
            DimensionalMatterAssemblerRecipe source = holder.value();
            if (source == null) {
                continue;
            }
            Map<Ingredient, Long> requiredItems = new LinkedHashMap<>();
            for (IngredientStack.Item input : source.getItemInputs()) {
                if (input != null && !input.isEmpty() && input.getIngredient() != null && !input.getIngredient().isEmpty()) {
                    AdapterUtils.mergeIngredient(requiredItems, input.getIngredient(), Math.max(1, input.getAmount()));
                }
            }
            if (!requiredItems.isEmpty() && !AdapterUtils.matchesRequired(mergedInputs, requiredItems)) {
                continue;
            }
            List<SizedFluidIngredient> requiredFluids = new ArrayList<>();
            for (IngredientStack.Fluid input : source.getFluidInputs()) {
                if (input != null && !input.isEmpty() && input.getIngredient() != null) {
                    requiredFluids.add(new SizedFluidIngredient(input.getIngredient(), Math.max(1, input.getAmount())));
                }
            }
            if (AdapterUtils.matchesFluidIngredients(mergedFluids, requiredFluids)) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
