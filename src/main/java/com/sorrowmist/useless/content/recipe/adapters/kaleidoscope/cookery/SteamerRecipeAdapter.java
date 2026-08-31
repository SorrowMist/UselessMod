package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.SteamerRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;

public final class SteamerRecipeAdapter extends AbstractCookeryRecipeAdapter<SteamerRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "steamer");

    @Override
    public Class<SteamerRecipe> getRecipeClass() {
        return SteamerRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, SteamerRecipe source, Level level) {
        return createItemRecipe(recipeId, List.of(source.getIngredient()),
                source.getResult(), source.getCookTick());
    }
}
