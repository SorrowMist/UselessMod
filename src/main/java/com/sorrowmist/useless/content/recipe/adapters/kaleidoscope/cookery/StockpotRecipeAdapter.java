package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.StockpotRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class StockpotRecipeAdapter extends AbstractStockpotRecipeAdapter<StockpotRecipe> {
    @Override
    public Class<StockpotRecipe> getRecipeClass() {
        return StockpotRecipe.class;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, StockpotRecipe source, Level level) {
        return createStockpotRecipe(recipeId, source.ingredients(), source.soupBase(),
                source.result(), source.time(), source.carrier());
    }
}
