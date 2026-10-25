package com.sorrowmist.useless.compat.jei;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.jemi.JemiRecipe;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Retains the exact JEI selection when AE2's native EMI encoder wins handler selection. */
public final class OmniversalEmiSelection {
    private static final ResourceLocation CATEGORY = new ResourceLocation("useless_mod", "advanced_alloy_furnace");

    private OmniversalEmiSelection() { }

    @Nullable
    public static AlloyFurnaceRecipeCatalog.Entry recipe(EmiRecipe recipe) {
        // Do not infer a mold from ordinary machine pages or ambiguous input/output matches.
        // JEMI retains the selected catalog entry, including its source and fingerprint.
        if (recipe == null || recipe.getCategory() == null
                || !CATEGORY.equals(recipe.getCategory().getId())) return null;
        return recipe instanceof JemiRecipe<?> imported
                && imported.recipe instanceof AlloyFurnaceRecipeCatalog.Entry entry ? entry : null;
    }
}
