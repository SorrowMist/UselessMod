package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.overload.pattern.OverloadPatternDetails;
import com.sorrowmist.useless.compat.EapCompat;
import com.sorrowmist.useless.content.recipe.RecipeOutputConstraint;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads AE2LT's slot-local matching modes without changing global AE key semantics. */
final class AdvancedAlloyFurnacePatternPolicy {
    private AdvancedAlloyFurnacePatternPolicy() {
    }

    static List<RecipeOutputConstraint> outputConstraints(@Nullable IPatternDetails pattern) {
        if (pattern == null) {
            return List.of();
        }

        IPatternDetails original = EapCompat.unwrap(pattern);
        OverloadPatternDetails overload = overloadDetails(original);
        List<GenericStack> outputs = original.getOutputs();
        List<RecipeOutputConstraint> result = new ArrayList<>(outputs.size());
        for (int slot = 0; slot < outputs.size(); slot++) {
            GenericStack output = outputs.get(slot);
            if (output == null || output.what() == null) {
                continue;
            }
            boolean idOnly = overload != null
                    && overload.outputMode(slot) == MatchMode.ID_ONLY
                    && output.what() instanceof AEItemKey;
            result.add(idOnly
                    ? RecipeOutputConstraint.itemId(output.what())
                    : RecipeOutputConstraint.exact(output.what()));
        }
        return List.copyOf(result);
    }

    static boolean usesRecipeOutputs(@Nullable IPatternDetails pattern) {
        OverloadPatternDetails overload = overloadDetails(EapCompat.unwrap(pattern));
        if (overload == null) {
            return false;
        }
        return overload.outputs().stream().anyMatch(output -> output.matchMode() == MatchMode.ID_ONLY);
    }

    static Set<AEKey> componentInputKeys(
            @Nullable IPatternDetails pattern, @Nullable KeyCounter[] inputHolder) {
        IPatternDetails original = EapCompat.unwrap(pattern);
        OverloadPatternDetails overload = overloadDetails(original);
        if (overload == null || inputHolder == null) {
            return Set.of();
        }

        Set<AEKey> result = new LinkedHashSet<>();
        IPatternDetails.IInput[] inputs = original.getInputs();
        if (inputHolder.length == inputs.length) {
            for (int slot = 0; slot < inputs.length; slot++) {
                if (overload.inputMode(slot) != MatchMode.ID_ONLY || inputHolder[slot] == null) {
                    continue;
                }
                for (var entry : inputHolder[slot]) {
                    if (entry.getLongValue() > 0) {
                        result.add(entry.getKey());
                    }
                }
            }
            return Set.copyOf(result);
        }

        Set<Item> relaxedItems = relaxedItems(original, overload);
        for (KeyCounter counter : inputHolder) {
            if (counter == null) continue;
            for (var entry : counter) {
                if (entry.getLongValue() > 0
                        && entry.getKey() instanceof AEItemKey itemKey
                        && relaxedItems.contains(itemKey.getItem())) {
                    result.add(itemKey);
                }
            }
        }
        return Set.copyOf(result);
    }

    static Set<AEKey> componentInputKeys(
            @Nullable IPatternDetails pattern, List<ItemStack> actualInputs) {
        IPatternDetails original = EapCompat.unwrap(pattern);
        OverloadPatternDetails overload = overloadDetails(original);
        if (overload == null || actualInputs == null || actualInputs.isEmpty()) {
            return Set.of();
        }

        Set<Item> relaxedItems = relaxedItems(original, overload);

        Set<AEKey> result = new LinkedHashSet<>();
        for (ItemStack stack : actualInputs) {
            if (stack == null || stack.isEmpty() || !relaxedItems.contains(stack.getItem())) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                result.add(key);
            }
        }
        return Set.copyOf(result);
    }

    private static Set<Item> relaxedItems(
            IPatternDetails pattern, OverloadPatternDetails overload) {
        Set<Item> result = new LinkedHashSet<>();
        IPatternDetails.IInput[] inputs = pattern.getInputs();
        for (int slot = 0; slot < inputs.length; slot++) {
            if (overload.inputMode(slot) != MatchMode.ID_ONLY) {
                continue;
            }
            for (GenericStack possible : inputs[slot].getPossibleInputs()) {
                if (possible != null && possible.what() instanceof AEItemKey itemKey) {
                    result.add(itemKey.getItem());
                }
            }
        }
        return result;
    }

    @Nullable
    private static OverloadPatternDetails overloadDetails(@Nullable IPatternDetails pattern) {
        if (pattern instanceof OverloadedProviderOnlyPatternDetails overloaded) {
            return overloaded.overloadPatternDetailsView();
        }
        return null;
    }
}
