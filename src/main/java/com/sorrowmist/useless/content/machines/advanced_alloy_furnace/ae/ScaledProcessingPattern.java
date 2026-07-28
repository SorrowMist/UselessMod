package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One processing-pattern push representing multiple operations. */
public final class ScaledProcessingPattern implements IPatternDetails {
    private final IPatternDetails original;
    private final long operationsPerPush;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    public ScaledProcessingPattern(IPatternDetails pattern, long operationsPerPush) {
        SmartDoublingPatterns.Resolved resolved = SmartDoublingPatterns.resolve(pattern);
        this.original = resolved.pattern();
        this.operationsPerPush = SmartDoublingPatterns.multiplyExactPositive(
                resolved.operationsPerPush(), operationsPerPush, "smart-doubling multiplier");
        if (this.operationsPerPush > SmartDoublingPatterns.maximumSafeMultiplier(this.original)) {
            throw new IllegalArgumentException("smart-doubling multiplier would overflow a pattern amount");
        }

        this.definition = SmartDoublingPatterns.executionDefinition(this.original, this.operationsPerPush);
        IInput[] originalInputs = this.original.getInputs();
        this.inputs = new IInput[originalInputs.length];
        for (int index = 0; index < originalInputs.length; index++) {
            this.inputs[index] = new ScaledInput(originalInputs[index], this.operationsPerPush);
        }

        List<GenericStack> scaledOutputs = new ArrayList<>(this.original.getOutputs().size());
        for (GenericStack output : this.original.getOutputs()) {
            if (output != null) {
                scaledOutputs.add(new GenericStack(
                        output.what(), Math.multiplyExact(output.amount(), this.operationsPerPush)));
            }
        }
        this.outputs = List.copyOf(scaledOutputs);
    }

    public IPatternDetails getOriginal() {
        return original;
    }

    public long getOperationsPerPush() {
        return operationsPerPush;
    }

    @Override
    public AEItemKey getDefinition() {
        return definition;
    }

    @Override
    public IInput[] getInputs() {
        return inputs.clone();
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return original.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        for (KeyCounter counter : inputHolder) {
            if (counter == null) {
                continue;
            }
            for (var input : counter) {
                inputSink.pushInput(input.getKey(), input.getLongValue());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        return object == this || object instanceof ScaledProcessingPattern other
                && operationsPerPush == other.operationsPerPush
                && original.equals(other.original);
    }

    @Override
    public int hashCode() {
        return 31 * original.hashCode() + Long.hashCode(operationsPerPush);
    }

    @Override
    public String toString() {
        return "ScaledProcessingPattern[operationsPerPush=" + operationsPerPush
                + ", original=" + original + ']';
    }

    private record ScaledInput(IInput original, long operationsPerPush) implements IInput {
        private ScaledInput {
            Objects.requireNonNull(original, "original");
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return original.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return Math.multiplyExact(original.getMultiplier(), operationsPerPush);
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return original.isValid(input, level);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return original.getRemainingKey(template);
        }
    }
}
