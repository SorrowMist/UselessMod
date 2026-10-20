package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;

/** Holder payload for Extreme Reactors mappings that are not RecipeManager recipes. */
public final class ExtremeReactorsSyntheticRecipe extends ModRecipe {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public ExtremeReactorsSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
        this.convertedRecipe = convertedRecipe;
    }

    public AdvancedAlloyFurnaceRecipe convertedRecipe() {
        return convertedRecipe;
    }
}
