package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeIdentity;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface mixed into AE2's {@code PatternEncodingLogic} so a pattern encoding terminal can
 * remember which alloy-furnace recipe the player picked in JEI.
 *
 * <p>The recipe has to be recorded at click time rather than rediscovered at encode time: a JEI
 * recipe carries its mold, while an encoded processing pattern does not, and several recipes can
 * share the same inputs and outputs while differing only in their mold. Searching the catalog for a
 * match therefore cannot tell which one the player meant; the click can.
 *
 * @see com.sorrowmist.useless.mixin.ae2.PatternEncodingLogicMixin
 */
public interface PendingOmniversalPatternHolder {
    @Nullable
    AlloyFurnaceRecipeIdentity uselessMod$getPendingOmniversalRecipe();

    void uselessMod$setPendingOmniversalRecipe(@Nullable AlloyFurnaceRecipeIdentity identity);
}
