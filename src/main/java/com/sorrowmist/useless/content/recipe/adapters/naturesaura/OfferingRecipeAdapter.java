package com.sorrowmist.useless.content.recipe.adapters.naturesaura;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import de.ellpeck.naturesaura.Helper;
import de.ellpeck.naturesaura.recipes.OfferingRecipe;
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

/** Converts offerings as one fixed sixteen-operation batch with one calling item. */
public final class OfferingRecipeAdapter implements IRecipeAdapter<OfferingRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int OFFERING_BATCH_SIZE = 16;

    @Override
    public Class<OfferingRecipe> getRecipeClass() {
        return OfferingRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return NaturesAuraAdapterUtils.item("offering_table");
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<OfferingRecipe> holder, Level level) {
        if (holder == null || holder.value() == null) {
            return List.of();
        }
        Converted converted = convertData(holder.value());
        if (converted == null) {
            LOGGER.warn("Skipping invalid Nature's Aura offering recipe: {}", holder.id());
            return List.of();
        }
        return List.of(createRecipe(holder, converted));
    }

    @Override
    public List<RecipeHolder<OfferingRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || !matchesMold(mold) || mergedInputs == null || mergedInputs.isEmpty()) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<OfferingRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<OfferingRecipe> holder : recipeManager.getAllRecipesFor(
                de.ellpeck.naturesaura.recipes.ModRecipes.OFFERING_TYPE)) {
            Converted converted = convertData(holder.value());
            if (converted != null && AdapterUtils.matchesRequired(mergedInputs, converted.requirements())) {
                matches.add(holder);
            }
        }
        return matches;
    }

    @Nullable
    private static Converted convertData(@Nullable OfferingRecipe source) {
        if (source == null || source.input == null || source.input.isEmpty() || source.startItem == null
                || source.startItem.isEmpty()) {
            return null;
        }

        ItemStack output = NaturesAuraAdapterUtils.multipliedOutput(source.output, OFFERING_BATCH_SIZE);
        int sourceInputCount = Helper.getIngredientAmount(source.input);
        if (output == null || sourceInputCount <= 0) {
            return null;
        }

        long inputCount;
        try {
            inputCount = Math.multiplyExact((long) sourceInputCount, OFFERING_BATCH_SIZE);
        } catch (ArithmeticException exception) {
            return null;
        }

        Map<Ingredient, Long> requirements = NaturesAuraAdapterUtils.requirements();
        if (!NaturesAuraAdapterUtils.addIngredient(requirements, source.input, inputCount)
                || !NaturesAuraAdapterUtils.addIngredient(requirements, source.startItem, 1L)) {
            return null;
        }
        List<CountedIngredient> inputs = NaturesAuraAdapterUtils.counted(requirements);
        if (inputs.isEmpty()) {
            return null;
        }
        return new Converted(inputs, requirements, output);
    }

    private AdvancedAlloyFurnaceRecipe createRecipe(RecipeHolder<OfferingRecipe> holder, Converted converted) {
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                converted.inputs(),
                List.of(),
                List.of(converted.output().copy()),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
    }

    private record Converted(
            List<CountedIngredient> inputs,
            Map<Ingredient, Long> requirements,
            ItemStack output) {
    }
}
