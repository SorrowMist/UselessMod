package com.sorrowmist.useless.content.recipe.adapters.create;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts Create fan blasting without registering smoking or smelting recipes. */
public final class CreateBlastingRecipeAdapter implements IRecipeAdapter<BlastingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<BlastingRecipe> getRecipeClass() {
        return BlastingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean matchesMold(@Nullable ItemStack mold) {
        return CreateRecipeAdapterUtils.isCreateMold(mold, "encased_fan");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(
            RecipeHolder<BlastingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) return List.of();
        BlastingRecipe source = holder.value();
        if (source.getType() != RecipeType.BLASTING) return List.of();
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(source.getIngredients());
        ItemStack output = source.getResultItem(level == null ? null : level.registryAccess());
        Ingredient fanMold = CreateRecipeAdapterUtils.blockMold(
                CreateRecipeAdapterUtils.createBlock("encased_fan"));
        if (inputs.isEmpty() || output.isEmpty() || output.getCount() <= 0 || fanMold.isEmpty()) {
            LOGGER.warn("Skipping invalid Create blasting recipe: {}", holder.id());
            return List.of();
        }
        int declaredTime = source.getCookingTime();
        if (declaredTime < 0) {
            LOGGER.warn("Skipping Create blasting recipe {} with a negative processing time", holder.id());
            return List.of();
        }
        int processTime = declaredTime > 0 ? declaredTime : AdapterUtils.DEFAULT_PROCESS_TIME;
        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                processTime,
                Ingredient.EMPTY,
                0,
                List.of(fanMold),
                AlloyFurnaceMode.NORMAL);
        return List.of(converted);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RecipeHolder<BlastingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs,
            Map<net.neoforged.neoforge.fluids.FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold)) return List.of();
        List<RecipeHolder<BlastingRecipe>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof BlastingRecipe blasting)
                    || blasting.getType() != RecipeType.BLASTING) continue;
            RecipeHolder<BlastingRecipe> typed = (RecipeHolder<BlastingRecipe>) (RecipeHolder<?>) holder;
            List<AdvancedAlloyFurnaceRecipe> converted = convertAll(typed, level);
            if (!converted.isEmpty() && CreateRecipeAdapterUtils.matchesConverted(
                    converted.getFirst(), mergedInputs, mergedFluids)) result.add(typed);
        }
        return result;
    }
}
