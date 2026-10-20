package com.sorrowmist.useless.compat.ironsspellbooks;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.recipe.RecipeSourceIds;
import com.sorrowmist.useless.content.recipe.adapters.ironsspellbooks.AlchemistCauldronRecipeAdapter;
import com.sorrowmist.useless.content.recipe.adapters.ironsspellbooks.AlchemistCauldronDynamicRecipeAdapter;

/** Registers Iron's Spells 'n Spellbooks recipe compatibility. */
public final class IronsSpellbooksRecipeCompatLoader {
    private IronsSpellbooksRecipeCompatLoader() {
    }

    public static void register() {
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new AlchemistCauldronRecipeAdapter(), RecipeSourceIds.IRONS_SPELLBOOKS);
        AlloyFurnaceRecipeManager.getInstance().registerAdapter(
                new AlchemistCauldronDynamicRecipeAdapter(), RecipeSourceIds.IRONS_SPELLBOOKS);
    }
}
