package com.sorrowmist.useless.content.recipe.adapters.ae.ae2lt;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.registry.ModItems;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.Map;

final class AELightningIngredientHelper {

    private static final int EXTREME_TO_HIGH_RATIO = 4;

    private AELightningIngredientHelper() {}

    static GenericStack createLightningKeyInput(LightningKey.Tier tier, long amount) {
        return new GenericStack(LightningKey.of(tier), Math.max(1, amount));
    }

    static boolean matchesLightning(Map<AEKey, Long> mergedKeys, LightningKey.Tier tier, long amount) {
        return mergedKeys.getOrDefault(LightningKey.of(tier), 0L) >= amount;
    }

    static boolean matchesSimulationOrAssemblyLightning(Map<Ingredient, Long> mergedInputs, Map<AEKey, Long> mergedKeys, LightningKey.Tier tier, long amount) {
        if (amount <= 0) return false;
        if (tier == LightningKey.Tier.HIGH_VOLTAGE) {
            return mergedKeys.getOrDefault(LightningKey.HIGH_VOLTAGE, 0L) >= amount;
        }
        long extreme = mergedKeys.getOrDefault(LightningKey.EXTREME_HIGH_VOLTAGE, 0L);
        if (extreme >= amount) {
            return true;
        }
        return hasLightningCollapseMatrix(mergedInputs)
                && mergedKeys.getOrDefault(LightningKey.HIGH_VOLTAGE, 0L) >= amount * EXTREME_TO_HIGH_RATIO;
    }

    static boolean matchesOverloadLightning(Map<Ingredient, Long> mergedInputs, Map<AEKey, Long> mergedKeys, LightningKey.Tier tier, long amount) {
        if (amount <= 0) return false;
        if (tier == LightningKey.Tier.HIGH_VOLTAGE) {
            return mergedKeys.getOrDefault(LightningKey.HIGH_VOLTAGE, 0L) >= amount;
        }
        long extreme = mergedKeys.getOrDefault(LightningKey.EXTREME_HIGH_VOLTAGE, 0L);
        if (extreme >= amount) {
            return true;
        }
        if (!hasLightningCollapseMatrix(mergedInputs)) {
            return false;
        }
        long remainingExtreme = amount - extreme;
        long highVoltageNeeded = remainingExtreme * EXTREME_TO_HIGH_RATIO;
        return highVoltageNeeded >= 0L && mergedKeys.getOrDefault(LightningKey.HIGH_VOLTAGE, 0L) >= highVoltageNeeded;
    }

    private static boolean hasLightningCollapseMatrix(Map<Ingredient, Long> mergedInputs) {
        return AdapterUtils.hasMatchingIngredient(mergedInputs, Ingredient.of(ModItems.LIGHTNING_COLLAPSE_MATRIX.get()));
    }
}
