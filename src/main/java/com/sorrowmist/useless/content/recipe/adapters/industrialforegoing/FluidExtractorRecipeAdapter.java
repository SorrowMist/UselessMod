package com.sorrowmist.useless.content.recipe.adapters.industrialforegoing;

import com.buuz135.industrial.config.machine.core.FluidExtractorConfig;
import com.buuz135.industrial.module.ModuleCore;
import com.buuz135.industrial.recipe.FluidExtractorRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts block-targeted fluid extractor recipes into deterministic item batches. */
public final class FluidExtractorRecipeAdapter implements IRecipeAdapter<FluidExtractorRecipe> {
    private static final long LATEX_PER_SOURCE_UNIT = 125L;

    @Override
    public String sourceId() {
        return RecipeSourceIds.INDUSTRIAL_FOREGOING;
    }

    @Override
    public Class<FluidExtractorRecipe> getRecipeClass() {
        return FluidExtractorRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModuleCore.FLUID_EXTRACTOR.getBlock());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<FluidExtractorRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        FluidExtractorRecipe source = holder.value();
        if (source.input == null || source.input.isEmpty()
                || source.output == null || source.output.isEmpty()
                || source.output.getAmount() <= 0) {
            return List.of();
        }

        FluidStack output = source.output.copy();
        if (source.outputsLatex()) {
            int amount = IndustrialForegoingRecipeAdapterUtils.scaledLatexAmount(
                    source.output, LATEX_PER_SOURCE_UNIT);
            if (amount <= 0) return List.of();
            output.setAmount(amount);
        }

        int processTime = IndustrialForegoingRecipeAdapterUtils.positive(
                FluidExtractorConfig.maxProgress);
        return List.of(new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.input, 1L)), List.of(), List.of(),
                List.of(), List.of(output), List.of(),
                Math.max(1L, FluidExtractorConfig.powerPerOperation), processTime,
                Ingredient.EMPTY, 0,
                List.of(AdapterUtils.toMoldIngredient(getMoldItem())), AlloyFurnaceMode.NORMAL));
    }

    @Override
    public List<RecipeHolder<FluidExtractorRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || mergedInputs == null || mergedInputs.isEmpty()
                || (mergedFluids != null && !mergedFluids.isEmpty())
                || !matchesMold(mold)) {
            return List.of();
        }

        RecipeType<FluidExtractorRecipe> type = extractorType();
        if (type == null) return List.of();
        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<FluidExtractorRecipe>> customMatches = new ArrayList<>();
        List<RecipeHolder<FluidExtractorRecipe>> defaultMatches = new ArrayList<>();
        for (RecipeHolder<FluidExtractorRecipe> holder : recipeManager.getAllRecipesFor(type)) {
            FluidExtractorRecipe source = holder.value();
            if (source == null || source.input == null || source.input.isEmpty()
                    || source.output == null || source.output.isEmpty()) continue;
            Map<Ingredient, Long> required = new LinkedHashMap<>();
            AdapterUtils.mergeIngredient(required, source.input, 1L);
            if (!AdapterUtils.matchesRequired(mergedInputs, required)) continue;
            if (source.defaultRecipe) defaultMatches.add(holder);
            else customMatches.add(holder);
        }
        return customMatches.isEmpty() ? List.copyOf(defaultMatches) : List.copyOf(customMatches);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static RecipeType<FluidExtractorRecipe> extractorType() {
        if (ModuleCore.FLUID_EXTRACTOR_TYPE == null
                || ModuleCore.FLUID_EXTRACTOR_TYPE.get() == null) return null;
        return (RecipeType<FluidExtractorRecipe>) (RecipeType<?>)
                ModuleCore.FLUID_EXTRACTOR_TYPE.get();
    }
}
