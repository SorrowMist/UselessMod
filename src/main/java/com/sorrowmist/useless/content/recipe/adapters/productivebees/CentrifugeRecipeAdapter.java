package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import com.sorrowmist.useless.content.recipe.IRecipeAdapter;
import cy.jdkdigital.productivebees.common.recipe.CentrifugeRecipe;
import cy.jdkdigital.productivebees.init.ModBlocks;
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
import java.util.Optional;
import java.util.OptionalInt;

public final class CentrifugeRecipeAdapter implements IRecipeAdapter<CentrifugeRecipe> {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Class<CentrifugeRecipe> getRecipeClass() {
        return CentrifugeRecipe.class;
    }

    @Override
    public ItemStack getMoldItem() {
        return new ItemStack(ModBlocks.CENTRIFUGE.get());
    }

    @Override
    public List<AdvancedAlloyFurnaceRecipe> convertAll(RecipeHolder<CentrifugeRecipe> holder, Level level) {
        if (holder == null) {
            return List.of();
        }

        CentrifugeRecipe source = holder.value();
        if (source.ingredient == null || source.ingredient.isEmpty()) {
            return List.of();
        }

        Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ProductiveBeesAdapterUtils.scaleOutputs(
                source.getRecipeOutputs()
        );
        if (scaled.isEmpty()) {
            LOGGER.warn("Skipping unsupported Productive Bees centrifuge recipe: {}", holder.id());
            return List.of();
        }

        int operations = scaled.get().operations();
        FluidStack fluidOutput = source.getFluidOutputs().copy();
        if (!fluidOutput.isEmpty()) {
            OptionalInt fluidAmount = ExpectedOutputScaler.multiplyToInt(fluidOutput.getAmount(), operations);
            if (fluidAmount.isEmpty()) {
                LOGGER.warn("Skipping overflowing Productive Bees centrifuge fluid output: {}", holder.id());
                return List.of();
            }
            fluidOutput.setAmount(fluidAmount.getAsInt());
        }
        if (scaled.get().outputs().isEmpty() && fluidOutput.isEmpty()) {
            return List.of();
        }

        OptionalInt energy = ExpectedOutputScaler.multiplyToInt(AdapterUtils.DEFAULT_ENERGY, operations);
        OptionalInt processTime = ExpectedOutputScaler.multiplyToInt(Math.max(1, source.getProcessingTime()), operations);
        if (energy.isEmpty() || processTime.isEmpty()) {
            LOGGER.warn("Skipping overflowing Productive Bees centrifuge recipe: {}", holder.id());
            return List.of();
        }

        AdvancedAlloyFurnaceRecipe converted = new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(holder.id()),
                List.of(new CountedIngredient(source.ingredient, operations)),
                List.of(),
                scaled.get().outputs(),
                fluidOutput.isEmpty() ? List.of() : List.of(fluidOutput),
                energy.getAsInt(),
                processTime.getAsInt(),
                Ingredient.EMPTY,
                0,
                AdapterUtils.toMoldIngredient(getMoldItem()),
                AlloyFurnaceMode.NORMAL
        );
        return List.of(converted);
    }

    @Override
    public List<RecipeHolder<CentrifugeRecipe>> findMatchingRecipes(
            Level level, Map<Ingredient, Long> mergedInputs, Map<FluidStack, Long> mergedFluids,
            @Nullable ItemStack mold) {
        if (level == null || mergedInputs.isEmpty() || !matchesMold(mold)) {
            return List.of();
        }

        RecipeManager recipeManager = level.getRecipeManager();
        List<RecipeHolder<CentrifugeRecipe>> matches = new ArrayList<>();
        for (RecipeHolder<CentrifugeRecipe> holder : recipeManager.getAllRecipesFor(
                cy.jdkdigital.productivebees.init.ModRecipeTypes.CENTRIFUGE_TYPE.get())) {
            CentrifugeRecipe source = holder.value();
            if (source.ingredient == null || source.ingredient.isEmpty()) {
                continue;
            }
            Optional<ExpectedOutputScaler.ScaledOutputs> scaled = ProductiveBeesAdapterUtils.scaleOutputs(
                    source.getRecipeOutputs()
            );
            if (scaled.isPresent()
                    && AdapterUtils.hasMatchingIngredient(mergedInputs, source.ingredient, scaled.get().operations())) {
                matches.add(holder);
            }
        }
        return matches;
    }
}
