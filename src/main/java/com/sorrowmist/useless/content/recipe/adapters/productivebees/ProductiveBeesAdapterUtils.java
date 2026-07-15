package com.sorrowmist.useless.content.recipe.adapters.productivebees;

import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import cy.jdkdigital.productivelib.common.recipe.TagOutputRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ProductiveBeesAdapterUtils {
    private ProductiveBeesAdapterUtils() {
    }

    static Optional<ExpectedOutputScaler.ScaledOutputs> scaleOutputs(
            Map<ItemStack, TagOutputRecipe.ChancedOutput> outputs) {
        List<ExpectedOutputScaler.WeightedItemOutput> weightedOutputs = new ArrayList<>();
        for (Map.Entry<ItemStack, TagOutputRecipe.ChancedOutput> entry : outputs.entrySet()) {
            TagOutputRecipe.ChancedOutput value = entry.getValue();
            weightedOutputs.add(new ExpectedOutputScaler.WeightedItemOutput(
                    entry.getKey(), value.min(), value.max(), value.chance()
            ));
        }
        return ExpectedOutputScaler.scale(weightedOutputs);
    }
}
