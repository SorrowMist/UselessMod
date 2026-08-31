package com.sorrowmist.useless.content.recipe.adapters.kaleidoscope.cookery;

import com.github.ysbbbbbb.kaleidoscopecookery.crafting.output.RandomOutput;
import com.github.ysbbbbbb.kaleidoscopecookery.crafting.recipe.MillstoneRecipe;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

public final class MillstoneRecipeAdapter extends AbstractCookeryRecipeAdapter<MillstoneRecipe> {
    private static final ResourceLocation MOLD_ID =
            ResourceLocation.fromNamespaceAndPath("kaleidoscope_cookery", "millstone");

    @Override
    public Class<MillstoneRecipe> getRecipeClass() {
        return MillstoneRecipe.class;
    }

    @Override
    protected ResourceLocation moldId() {
        return MOLD_ID;
    }

    @Override
    protected AdvancedAlloyFurnaceRecipe convertSource(
            ResourceLocation recipeId, MillstoneRecipe source, Level level) {
        List<ItemStack> outputs = source.results().stream()
                .filter(result -> result != null && !result.isEmpty())
                .map(RandomOutput::stack)
                .toList();
        return createMultiOutputItemRecipe(recipeId, List.of(source.ingredient()),
                outputs, 200);
    }
}
