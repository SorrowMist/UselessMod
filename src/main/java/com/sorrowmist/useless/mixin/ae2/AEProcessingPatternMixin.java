package com.sorrowmist.useless.mixin.ae2;

import appeng.crafting.pattern.AEProcessingPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ProcessingPatternRecipeHolder;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Reserves a runtime recipe binding for a future processing-pattern converter. */
@Mixin(value = AEProcessingPattern.class, remap = false)
public abstract class AEProcessingPatternMixin implements ProcessingPatternRecipeHolder {
    @Unique
    @Nullable
    private AlloyFurnaceRecipeIdentity uselessMod$recipeIdentity;

    @Override
    @Nullable
    public AlloyFurnaceRecipeIdentity uselessMod$getRecipeIdentity() {
        return uselessMod$recipeIdentity;
    }

    @Override
    public void uselessMod$setRecipeIdentity(@Nullable AlloyFurnaceRecipeIdentity identity) {
        this.uselessMod$recipeIdentity = identity;
    }
}
