package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.tavern;

import com.github.ysbbbbbb.kaleidoscopetavern.crafting.recipe.ShakerRecipe;
import com.sorrowmist.useless.api.enums.AlloyFurnaceMode;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.CountedIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import java.util.List;

/** Converts Kaleidoscope Tavern shaker recipes into item recipes. */
public final class ShakerRecipeAdapter extends AbstractTavernRecipeAdapter<ShakerRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_tavern", "shaker");

    @Override
    public Class<ShakerRecipe> getRecipeClass() {
        return ShakerRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, ShakerRecipe source, Level level) {
        List<CountedIngredient> inputs = AdapterUtils.mergeIngredients(
                nonEmptyIngredients(source.ingredients()));
        ItemStack output = source.result();
        if (inputs.isEmpty() || output == null || output.isEmpty()) {
            return null;
        }

        return new AdvancedAlloyFurnaceRecipe(
                AdapterUtils.convertedId(recipeId),
                inputs,
                List.of(),
                List.of(),
                List.of(output.copy()),
                List.of(),
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
