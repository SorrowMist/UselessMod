package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.FlexPotRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public final class FlexPotRecipeAdapter extends AbstractCookeryRecipeAdapter<FlexPotRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "pot");

    @Override
    public Class<FlexPotRecipe> getRecipeClass() {
        return FlexPotRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, FlexPotRecipe source, Level level) {
        return createItemRecipe(recipeId, source.ingredients(), source.carrier(),
                source.result(), source.time());
    }
}
