package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern;

import com.github.ysbbbbbb.kaleidoscopetavern.crafting.recipe.PressingTubRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/** Converts Kaleidoscope Tavern pressing-tub recipes into fluid outputs. */
public final class PressingTubRecipeAdapter
        extends AbstractTavernRecipeAdapter<PressingTubRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_tavern", "pressing_tub");

    @Override
    public Class<PressingTubRecipe> getRecipeClass() {
        return PressingTubRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, PressingTubRecipe source, Level level) {
        Ingredient input = source.getIngredient();
        int fluidAmount = source.getFluidAmount();
        if (input == null || input.isEmpty() || source.getFluid() == null || fluidAmount <= 0) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                List.of(new CountedIngredient(input, 1L)),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FluidStack(source.getFluid(), fluidAmount)),
                List.of(),
                AdapterUtils.DEFAULT_ENERGY,
                AdapterUtils.DEFAULT_PROCESS_TIME,
                Ingredient.EMPTY,
                0,
                moldIngredients(),
                AlloyFurnaceMode.NORMAL
        );
    }
}
