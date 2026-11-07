package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;

import java.util.List;
import java.util.Map;

final class AELightningIngredientHelper {
    private AELightningIngredientHelper() {}

    static List<GenericStack> lightningInputs(LightningKey.Tier tier, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative lightning cost");
        return amount == 0 ? List.of() : List.of(new GenericStack(LightningKey.of(tier), amount));
    }

    static com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient exactFluidInput(
            net.minecraftforge.fluids.FluidStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return new com.sorrowmist.useless.compat.forge1201.fluid.SizedFluidIngredient(
                com.sorrowmist.useless.compat.forge1201.fluid.FluidIngredient.nbt(true, stack), stack.getAmount());
    }

    // Lookup must require the same key that the converted recipe will actually consume. Native
    // high/extreme-tier substitution is not a free lookup shortcut: it needs a separate resource
    // conversion/consumption plan, which this bridge does not provide.
    static boolean matchesLightning(Map<AEKey, Long> mergedKeys, LightningKey.Tier tier, long amount) {
        return amount >= 0 && (amount == 0 || (mergedKeys != null
                && mergedKeys.getOrDefault(LightningKey.of(tier), 0L) >= amount));
    }
}
