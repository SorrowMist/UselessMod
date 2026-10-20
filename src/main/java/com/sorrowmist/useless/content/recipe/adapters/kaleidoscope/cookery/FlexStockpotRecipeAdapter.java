package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexStockpotRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class FlexStockpotRecipeAdapter extends AbstractStockpotRecipeAdapter<FlexStockpotRecipe> {
    @Override
    public Class<FlexStockpotRecipe> getRecipeClass() {
        return FlexStockpotRecipe.class;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, FlexStockpotRecipe source, Level level) {
        return createStockpotRecipe(recipeId, source.ingredients(), source.soupBase(),
                source.result(), source.time(), source.carrier());
    }
}
