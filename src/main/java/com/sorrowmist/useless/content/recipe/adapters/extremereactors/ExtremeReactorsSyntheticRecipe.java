package com.sorrowmist.useless.content.recipe.adapters.extremereactors;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.zerono.mods.zerocore.lib.recipe.ModRecipe;

/** Holder payload for Extreme Reactors mappings that are not RecipeManager recipes. */
public final class ExtremeReactorsSyntheticRecipe extends ModRecipe {
    private final AdvancedAlloyFurnaceRecipe convertedRecipe;

    public ExtremeReactorsSyntheticRecipe(AdvancedAlloyFurnaceRecipe convertedRecipe) {
        // ZeroCore 1.20.1 ModRecipe has no no-arg constructor. Its native recipe ID must
        // agree with the holder ID so runtime lookups and encoded patterns stay stable.
        super(java.util.Objects.requireNonNull(convertedRecipe, "convertedRecipe").id());
        this.convertedRecipe = convertedRecipe;
    }

    public AdvancedAlloyFurnaceRecipe convertedRecipe() {
        return convertedRecipe;
    }
}
