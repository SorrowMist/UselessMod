package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern;

import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IBarrel;
import com.github.ysbbbbbb.kaleidoscopetavern.crafting.recipe.BarrelRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import com.sorrowmist.useless.content.recipe.LongSizedFluidIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** Converts Kaleidoscope Tavern barrel batches into bottled item recipes. */
public final class BarrelRecipeAdapter extends AbstractTavernRecipeAdapter<BarrelRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_tavern", "barrel");
    private static final int BARREL_OUTPUT_COUNT = 16;
    private static final int BREW_LEVEL_TIME_SUM = 15;

    @Override
    public Class<BarrelRecipe> getRecipeClass() {
        return BarrelRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, BarrelRecipe source, Level level) {
        ItemStack result = source.result();
        if (source.fluid() == null || result == null || result.isEmpty()) {
            return null;
        }

        List<Ingredient> batchInputs = new ArrayList<>();
        for (Ingredient ingredient : nonEmptyIngredients(source.ingredients())) {
            for (int i = 0; i < BARREL_OUTPUT_COUNT; i++) {
                batchInputs.add(ingredient);
            }
        }
        if (!AdapterUtils.isIngredientEmpty(source.carrier())) {
            for (int i = 0; i < BARREL_OUTPUT_COUNT; i++) {
                batchInputs.add(source.carrier());
            }
        }
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(batchInputs);
        if (inputs.isEmpty()) {
            return null;
        }

        int processTime = clampProcessTime(
                (long) Math.max(1, source.unitTime()) * BREW_LEVEL_TIME_SUM);
        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                inputs,
                List.of(LongSizedFluidIngredient.from(
                        new FluidStack(source.fluid(), IBarrel.MAX_FLUID_AMOUNT))),
                List.of(),
                List.of(result.copyWithCount(BARREL_OUTPUT_COUNT)),
                List.of(),
                List.of(),
                scaledEnergy(processTime),
                processTime,
                Ingredient.EMPTY,
                0,
                moldIngredients(),
                AlloyFurnaceMode.NORMAL
        );
    }
}
