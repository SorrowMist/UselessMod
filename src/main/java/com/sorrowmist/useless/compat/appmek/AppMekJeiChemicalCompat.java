package com.sorrowmist.useless.compat.appmek;

import com.sorrowmist.useless.compat.jei.GenericStackJeiIngredientProvider;
import mekanism.api.IMekanismAccess;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.integration.jei.IMekanismJEIHelper;
import mekanism.client.jei.ChemicalStackRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import me.ramidzkh.mekae2.ae2.MekanismKey;

/** Mekanism 10.4 has four distinct chemical JEI types, not 1.21's unified ChemicalStack. */
public final class AppMekJeiChemicalCompat {
    private AppMekJeiChemicalCompat() {}
    public static GenericStackJeiIngredientProvider createProvider() {
        return createProvider(IMekanismAccess.INSTANCE.jeiHelper());
    }
    static GenericStackJeiIngredientProvider createProvider(IMekanismJEIHelper helpers) {
        var gas = helpers.getGasStackHelper().getIngredientType();
        var infusion = helpers.getInfusionStackHelper().getIngredientType();
        var pigment = helpers.getPigmentStackHelper().getIngredientType();
        var slurry = helpers.getSlurryStackHelper().getIngredientType();
        return stack -> {
            if (!(stack.what() instanceof MekanismKey key) || stack.amount() <= 0) return null;
            ChemicalStack<?> chemical = key.withAmount(stack.amount());
            if (chemical.isEmpty()) return null;
            IIngredientType<?> type = switch (key.getForm()) {
                case MekanismKey.GAS -> gas;
                case MekanismKey.INFUSION -> infusion;
                case MekanismKey.PIGMENT -> pigment;
                case MekanismKey.SLURRY -> slurry;
                default -> null;
            };
            if (type == null || !type.getIngredientClass().isInstance(chemical)) return null;
            return new GenericStackJeiIngredientProvider.Ingredient(type, chemical,
                    new ChemicalStackRenderer<>(chemical.getAmount(), 16, 16));
        };
    }
}
