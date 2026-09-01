package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.ChoppingBoardRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

public final class ChoppingBoardRecipeAdapter
        extends AbstractCookeryRecipeAdapter<ChoppingBoardRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "chopping_board");

    @Override
    public Class<ChoppingBoardRecipe> getRecipeClass() {
        return ChoppingBoardRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, ChoppingBoardRecipe source, Level level) {
        return createItemRecipe(recipeId, List.of(source.getIngredient()),
                source.getResult(), source.getCutCount());
    }
}
