package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import org.jetbrains.annotations.Nullable;

/** Runtime extension point for recipe-aware processing-pattern converters. */
public interface ProcessingPatternRecipeHolder {
    @Nullable
    AlloyFurnaceRecipeIdentity uselessMod$getRecipeIdentity();

    void uselessMod$setRecipeIdentity(@Nullable AlloyFurnaceRecipeIdentity identity);
}
