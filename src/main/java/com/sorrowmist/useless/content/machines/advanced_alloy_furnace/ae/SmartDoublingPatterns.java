package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Creates, restores and resolves this mod's smart-doubling pattern wrappers. */
public final class SmartDoublingPatterns {
    private SmartDoublingPatterns() {
    }

    public static ScaledProcessingPattern scale(IPatternDetails pattern, long operationsPerPush) {
        return new ScaledProcessingPattern(pattern, operationsPerPush);
    }

    public static IPatternDetails unwrap(IPatternDetails pattern) {
        return resolve(pattern).pattern();
    }

    public static long operationsPerPush(IPatternDetails pattern) {
        return resolve(pattern).operationsPerPush();
    }

    /**
     * Resolves the output-derived manual multiplier carried by the unwrapped processing pattern.
     * Zero means the pattern cannot be proven to represent an exact number of recipe operations.
     */
    public static long manualOperationsPerPattern(
            AdvancedAlloyFurnaceRecipe recipe,
            IPatternDetails pattern) {
        ManualPatternOperationResolver.Resolution resolution =
                ManualPatternOperationResolver.resolve(recipe, unwrap(pattern));
        return resolution.valid() ? resolution.operationsPerPattern() : 0L;
    }

    public static Resolved resolve(IPatternDetails pattern) {
        Objects.requireNonNull(pattern, "pattern");
        IPatternDetails current = pattern;
        long operations = 1L;
        while (current instanceof ScaledProcessingPattern scaled) {
            operations = multiplyExactPositive(
                    operations, scaled.getOperationsPerPush(), "nested smart-doubling multiplier");
            current = scaled.getOriginal();
        }
        return new Resolved(current, operations);
    }

    public static long maximumSafeMultiplier(IPatternDetails pattern) {
        IPatternDetails original = unwrap(pattern);
        long maximum = Long.MAX_VALUE;
        for (IPatternDetails.IInput input : original.getInputs()) {
            if (input != null && input.getMultiplier() > 0L) {
                maximum = Math.min(maximum, Long.MAX_VALUE / input.getMultiplier());
                GenericStack[] possibleInputs = input.getPossibleInputs();
                if (possibleInputs != null) {
                    for (GenericStack possibleInput : possibleInputs) {
                        if (possibleInput != null && possibleInput.amount() > 0L) {
                            maximum = Math.min(maximum,
                                    Long.MAX_VALUE / input.getMultiplier() / possibleInput.amount());
                        }
                    }
                }
            }
        }
        for (GenericStack output : original.getOutputs()) {
            if (output != null && output.amount() > 0L) {
                maximum = Math.min(maximum, Long.MAX_VALUE / output.amount());
            }
        }
        return Math.max(1L, maximum);
    }

    @Nullable
    public static Long definitionOperations(AEItemKey definition) {
        return definition == null ? null : definition.get(UComponents.SMART_DOUBLING_OPERATIONS.get());
    }

    @Nullable
    public static IPatternDetails restore(AEItemKey definition, Level level) {
        Long operations = definitionOperations(definition);
        if (operations == null) {
            return null;
        }
        if (operations <= 0L || level == null) {
            return null;
        }

        ItemStack baseStack = definition.toStack();
        baseStack.remove(UComponents.SMART_DOUBLING_OPERATIONS.get());
        AEItemKey baseDefinition = AEItemKey.of(baseStack);
        if (baseDefinition == null) {
            return null;
        }

        IPatternDetails decoded = AdvancedAlloyFurnacePatternResolver.decode(
                baseDefinition.toStack(), level);
        if (decoded == null) {
            return null;
        }
        if (operations > maximumSafeMultiplier(decoded)) {
            return null;
        }
        return scale(decoded, operations);
    }

    static AEItemKey executionDefinition(IPatternDetails original, long operationsPerPush) {
        ItemStack definition = original.getDefinition().toStack();
        definition.remove(UComponents.SMART_DOUBLING_OPERATIONS.get());
        definition.set(UComponents.SMART_DOUBLING_OPERATIONS.get(), operationsPerPush);
        return Objects.requireNonNull(AEItemKey.of(definition), "scaled pattern definition");
    }

    static long multiplyExactPositive(long left, long right, String description) {
        if (left <= 0L || right <= 0L) {
            throw new IllegalArgumentException(description + " must be positive");
        }
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(description + " exceeds the long range", exception);
        }
    }

    public record Resolved(IPatternDetails pattern, long operationsPerPush) {
        public Resolved {
            Objects.requireNonNull(pattern, "pattern");
            if (operationsPerPush <= 0L) {
                throw new IllegalArgumentException("operationsPerPush must be positive");
            }
        }
    }
}
