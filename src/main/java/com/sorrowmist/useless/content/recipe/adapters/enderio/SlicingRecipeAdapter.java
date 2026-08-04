package com.sorrowmist.useless.content.recipe.adapters.enderio;

import com.enderio.enderio.content.machines.slicer.SlicingRecipe;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIORecipes;
import com.mojang.logging.LogUtils;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Ender IO Slice and Splice recipes to the alloy furnace. */
public final class SlicingRecipeAdapter implements IRecipeAdapter<SlicingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<SlicingRecipe> getRecipeClass() {
        return SlicingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(EIOBlocks.SLICE_AND_SPLICE.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<SlicingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        SlicingRecipe source = holder.value();
        List<CountedIngredient> inputs = counted(source.inputs());
        ItemStack output = source.output();
        if (inputs == null || inputs.isEmpty() || output == null || output.isEmpty()
                || output.getCount() <= 0 || source.energy() < 0) {
            LOGGER.warn("Skipping invalid Ender IO slicing recipe: {}", holder.id());
            return List.of();
        }
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()), inputs, List.of(), List.of(output.copy()), List.of(),
                source.energy(), AdapterUtils.DEFAULT_PROCESS_TIME, Ingredient.EMPTY, 0,
                AdapterUtils.toMoldIngredient(getMoldItem()), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<SlicingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }
        List<RecipeHolder<SlicingRecipe>> matches = new ArrayList<>();
        RecipeManager manager = level.getRecipeManager();
        for (RecipeHolder<SlicingRecipe> holder : manager.getAllRecipesFor(EIORecipes.SLICING.type().get())) {
            SlicingRecipe source = holder.value();
            List<CountedIngredient> inputs = counted(source.inputs());
            if (inputs != null && !source.output().isEmpty()
                    && AdapterUtils.matchesRequired(mergedInputs, EnderIOAdapterUtils.requirements(inputs))) {
                matches.add(holder);
            }
        }
        return matches;
    }

    private static List<CountedIngredient> counted(List<Ingredient> source) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return source.stream()
                .filter(ingredient -> ingredient != null && !ingredient.isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(
                        ingredient -> ingredient,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .map(entry -> new CountedIngredient(entry.getKey(), entry.getValue()))
                .toList();
    }
}
