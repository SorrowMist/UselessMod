package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Item-only pattern view that keeps AE amounts as long values. */
public record PatternStackView(List<GenericStack> inputs, List<GenericStack> outputs) {
    public PatternStackView {
        inputs = List.copyOf(inputs == null ? List.of() : inputs);
        outputs = List.copyOf(outputs == null ? List.of() : outputs);
    }

    @Nullable
    public static PatternStackView fromPattern(IPatternDetails pattern) {
        if (pattern == null) return null;

        List<GenericStack> inputs = new ArrayList<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            if (input == null || input.getMultiplier() <= 0L) return null;
            AEItemKey key = firstItemKey(input.getPossibleInputs());
            if (key == null) return null;
            inputs.add(new GenericStack(key, input.getMultiplier()));
        }

        List<GenericStack> outputs = new ArrayList<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null || output.amount() <= 0L || !(output.what() instanceof AEItemKey)) {
                return null;
            }
            outputs.add(output);
        }
        return new PatternStackView(inputs, outputs);
    }

    public static PatternStackView fromLegacy(List<ItemStack> inputs, List<ItemStack> outputs) {
        return new PatternStackView(toGeneric(inputs), toGeneric(outputs));
    }

    public List<ItemStack> inputRepresentatives() {
        return representatives(inputs);
    }

    public List<ItemStack> outputRepresentatives() {
        return representatives(outputs);
    }

    private static List<GenericStack> toGeneric(List<ItemStack> stacks) {
        if (stacks == null) return List.of();
        List<GenericStack> result = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isEmpty()) continue;
            GenericStack generic = GenericStack.fromItemStack(stack);
            if (generic != null) result.add(generic);
        }
        return result;
    }

    private static List<ItemStack> representatives(List<GenericStack> stacks) {
        List<ItemStack> result = new ArrayList<>(stacks.size());
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() instanceof AEItemKey itemKey && stack.amount() > 0L) {
                result.add(itemKey.toStack(1));
            }
        }
        return List.copyOf(result);
    }

    @Nullable
    private static AEItemKey firstItemKey(GenericStack[] possibleInputs) {
        if (possibleInputs == null) return null;
        for (GenericStack possible : possibleInputs) {
            if (possible != null && possible.what() instanceof AEItemKey itemKey) {
                return itemKey;
            }
        }
        return null;
    }
}
