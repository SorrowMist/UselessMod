package com.sorrowmist.useless.content.recipe.adapters.create;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Create's item/fluid processing recipes into multi-machine mold recipes. */
public final class CreateProcessingRecipeAdapter implements IRecipeAdapter<ProcessingRecipe<?, ?>> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("unchecked")
    @Override
    public Class<ProcessingRecipe<?, ?>> getRecipeClass() {
        return (Class<ProcessingRecipe<?, ?>>) (Class<?>) ProcessingRecipe.class;
    }

    @Override
    @Nullable
    public ItemStack getMoldItem() {
        return null;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return CreateRecipeAdapterUtils.isCreateMachineMold(mold);
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<ProcessingRecipe<?, ?>> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        ProcessingRecipe<?, ?> source = holder.value();
        if (!CreateRecipeAdapterUtils.supportsProcessing(source)) return List.of();
        return converted(holder.id(), source);
    }

    static List<AdvancedAlloyFurnaceRecipe> converted(
            net.minecraft.resources.ResourceLocation id, ProcessingRecipe<?, ?> source) {
        AdvancedAlloyFurnaceRecipe converted = CreateRecipeAdapterUtils.convertProcessing(
                id, source, CreateRecipeAdapterUtils.processingMolds(source), LOGGER);
        return converted == null ? List.of() : List.of(converted);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<ProcessingRecipe<?, ?>>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<FluidStack, Long> mergedFluids, @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<ProcessingRecipe<?, ?>>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof ProcessingRecipe<?, ?> source)
                    || !CreateRecipeAdapterUtils.supportsProcessing(source)
                    || !CreateRecipeAdapterUtils.hasMoldForProcessing(source, mold)) {
                continue;
            }
            List<AdvancedAlloyFurnaceRecipe> converted = converted(holder.id(), source);
            if (converted.isEmpty() || !CreateRecipeAdapterUtils.matchesConverted(
                    converted.getFirst(), mergedInputs, mergedFluids)) continue;
            result.add((RecipeHolder<ProcessingRecipe<?, ?>>) (RecipeHolder<?>) holder);
        }
        return result;
    }
}
