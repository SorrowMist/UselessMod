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
import java.util.Set;

/** One processing-pattern push representing multiple operations. */
public class ScaledProcessingPattern implements IPatternDetails, ScaledPattern {
    private final IPatternDetails original;
    private final long operationsPerPush;
    private final AEItemKey definition;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;

    /**
     * 不带 {@link Level} 的重载：只认「原样返还」的可复用输入，带耐久受损的返还物（注魔水晶这类）
     * 判不出来。会走这条路的调用方都应改成带 {@code level} 的那个。
     */
    public ScaledProcessingPattern(IPatternDetails pattern, long operationsPerPush) {
        this(pattern, operationsPerPush, null);
    }

    /**
     * @param pattern           被包装的原始样板
     * @param operationsPerPush 本次推送代表的原始操作次数，恒为正
     * @param level             判定「可复用输入」用的关卡。<b>由本模组自己的机器提供</b>
     *                          （`AlloyFurnaceCountedCraftingAdapter#providerLevel` /
     *                          `SmartDoublingPlanner` 从供应器方块实体取），
     *                          <b>不从 AE2 网格上取</b> —— 那条路要在 `CraftingService` 上挂 mixin，
     *                          会静默顶掉 OmniSequence 的注入。{@code null} 时退回保守判据
     */
    public ScaledProcessingPattern(IPatternDetails pattern, long operationsPerPush,
                                   @Nullable Level level) {
        SmartDoublingPatterns.Resolved resolved = SmartDoublingPatterns.resolve(pattern);
        this.original = resolved.pattern();
        this.operationsPerPush = SmartDoublingPatterns.multiplyExactPositive(
                resolved.operationsPerPush(), operationsPerPush, "smart-doubling multiplier");
        if (this.operationsPerPush > SmartDoublingPatterns.maximumSafeMultiplier(this.original)) {
            throw new IllegalArgumentException("smart-doubling multiplier would overflow a pattern amount");
        }

        this.definition = SmartDoublingPatterns.executionDefinition(this.original, this.operationsPerPush);
        IInput[] originalInputs = this.original.getInputs();
        Set<Integer> reusableSlots =
                ReusablePatternInputs.reusableRemainders(this.original, level).keySet();
        this.inputs = new IInput[originalInputs.length];
        for (int index = 0; index < originalInputs.length; index++) {
            this.inputs[index] = new ScaledInput(originalInputs[index], this.operationsPerPush,
                    reusableSlots.contains(index));
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

    @Override
    public IPatternDetails getOriginal() {
        return original;
    }

    @Override
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
        if (object == this) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        ScaledProcessingPattern other = (ScaledProcessingPattern) object;
        return operationsPerPush == other.operationsPerPush
                && original.equals(other.original);
    }

    @Override
    public int hashCode() {
        return 31 * original.hashCode() + Long.hashCode(operationsPerPush);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[operationsPerPush=" + operationsPerPush
                + ", original=" + original + ']';
    }

    private record ScaledInput(IInput original, long operationsPerPush, boolean reusable) implements IInput {
        private ScaledInput {
            Objects.requireNonNull(original, "original");
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return original.getPossibleInputs();
        }

        /**
         * 可复用输入（注魔水晶这类）整批只需要 1 份：AE2 的合成计划对它只算 1 份
         * （见 {@link ReusablePatternInputs}），这里放大就抽不出材料、一次推送都发不出去。
         * 其余输入照旧 ×倍率，AE2 才会一次抽出 N 份、把 N 份预期产物写进 CPU 的 {@code waitingFor}。
         */
        @Override
        public long getMultiplier() {
            long base = original.getMultiplier();
            return reusable ? base : Math.multiplyExact(base, operationsPerPush);
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
