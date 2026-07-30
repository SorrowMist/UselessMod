package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/** Resolves how many base recipe operations one manually scaled AE pattern represents. */
final class ManualPatternOperationResolver {
    private ManualPatternOperationResolver() {
    }

    static Resolution resolve(AdvancedAlloyFurnaceRecipe recipe, IPatternDetails pattern) {
        if (recipe == null || pattern == null || pattern.getOutputs().isEmpty()) {
            return Resolution.invalid();
        }

        // Smart-doubling already carries its operation count separately in the AE task pipeline.
        if (SmartDoublingPatterns.operationsPerPush(pattern) > 1L) {
            return Resolution.valid(1L);
        }

        DynamicComponentPattern dynamic = pattern instanceof DynamicComponentPattern value ? value : null;
        long multiplier = 0L;
        List<GenericStack> outputs = pattern.getOutputs();
        for (int slot = 0; slot < outputs.size(); slot++) {
            GenericStack output = outputs.get(slot);
            if (output == null || output.what() == null || output.amount() <= 0L) {
                return Resolution.invalid();
            }

            boolean itemIdOnly = dynamic != null && dynamic.isItemIdOutput(slot);
            long recipeAmount = matchingRecipeOutputAmount(recipe, output.what(), itemIdOnly);
            if (recipeAmount <= 0L || output.amount() % recipeAmount != 0L) {
                return Resolution.invalid();
            }

            long candidate = output.amount() / recipeAmount;
            if (candidate <= 0L || multiplier != 0L && multiplier != candidate) {
                return Resolution.invalid();
            }
            multiplier = candidate;
        }

        return multiplier <= 0L ? Resolution.invalid() : Resolution.valid(multiplier);
    }

    private static long matchingRecipeOutputAmount(
            AdvancedAlloyFurnaceRecipe recipe, AEKey expected, boolean itemIdOnly) {
        long amount = 0L;
        for (ItemStack output : recipe.outputs()) {
            AEItemKey key = AEItemKey.of(output);
            if (matches(expected, key, itemIdOnly)) {
                amount = saturatingAdd(amount, output.getCount());
            }
        }
        for (FluidStack output : recipe.outputFluids()) {
            AEFluidKey key = AEFluidKey.of(output);
            if (matches(expected, key, false)) {
                amount = saturatingAdd(amount, output.getAmount());
            }
        }
        for (GenericStack output : recipe.keyOutputs()) {
            if (output != null && output.what() != null && output.amount() > 0L
                    && matches(expected, output.what(), itemIdOnly)) {
                amount = saturatingAdd(amount, output.amount());
            }
        }
        return amount;
    }

    private static boolean matches(AEKey expected, AEKey candidate, boolean itemIdOnly) {
        if (expected == null || candidate == null) {
            return false;
        }
        if (!itemIdOnly) {
            return expected.equals(candidate);
        }
        return expected instanceof AEItemKey expectedItem
                && candidate instanceof AEItemKey candidateItem
                && expectedItem.getItem() == candidateItem.getItem();
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    record Resolution(boolean valid, long operationsPerPattern) {
        static Resolution valid(long operationsPerPattern) {
            return new Resolution(true, Math.max(1L, operationsPerPattern));
        }

        static Resolution invalid() {
            return new Resolution(false, 1L);
        }
    }
}
