package com.sorrowmist.useless.compat.appmek;

import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.compat.jei.GenericStackJeiIngredientProvider;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.recipe_viewer.jei.ChemicalStackRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import me.ramidzkh.mekae2.ae2.MekanismKey;

/** AppMek/Mekanism bridge for native chemical ingredients in the local JEI category. */
public final class AppMekJeiChemicalCompat {
    private AppMekJeiChemicalCompat() {
    }

    public static GenericStackJeiIngredientProvider createProvider() {
        IIngredientType<ChemicalStack> chemicalType =
                IMekanismAccess.INSTANCE.jeiHelper().getChemicalStackHelper().getIngredientType();
        return new Provider(chemicalType);
    }

    private record Provider(IIngredientType<ChemicalStack> chemicalType)
            implements GenericStackJeiIngredientProvider {
        @Override
        public Ingredient resolve(GenericStack stack) {
            if (!(stack.what() instanceof MekanismKey key)) return null;
            ChemicalStack chemical = key.withAmount(Math.max(1L, stack.amount()));
            if (chemical.isEmpty()) return null;

            // JEI's default chemical renderer scales the fill against a bucket. Recipe
            // ingredients should remain visually prominent even when their amount is small.
            var renderer = new ChemicalStackRenderer(Math.max(1L, chemical.getAmount()), 16, 16);
            return new Ingredient(this.chemicalType, chemical, renderer);
        }
    }
}
