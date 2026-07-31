package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartDoublingPlansTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rewritesEcoStylePlanAndPreservesPlanMetadata() {
        TestPattern pattern = new TestPattern(2L, 3L);
        KeyCounter used = counter(AEItemKey.of(Items.IRON_INGOT), 8_192L);
        KeyCounter emitted = counter(AEItemKey.of(Items.REDSTONE), 4L);
        KeyCounter missing = counter(AEItemKey.of(Items.DIAMOND), 2L);
        GenericStack finalOutput = new GenericStack(
                Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT)), 12_288L);
        CraftingPlan original = new CraftingPlan(
                finalOutput, 98_765L, false, true,
                used, emitted, missing, Map.of(pattern, 4_096L));

        ICraftingPlan rewritten = SmartDoublingPlans.rewriteForSubmission(
                original, ignored -> List.of(new LocalProvider(pattern)));

        assertNotSame(original, rewritten);
        assertSame(finalOutput, rewritten.finalOutput());
        assertEquals(98_765L, rewritten.bytes());
        assertEquals(false, rewritten.simulation());
        assertEquals(true, rewritten.multiplePaths());
        assertSame(used, rewritten.usedItems());
        assertSame(emitted, rewritten.emittedItems());
        assertSame(missing, rewritten.missingItems());
        assertEquals(4_096L, representedOperations(rewritten.patternTimes()));
        assertEquals(representedInputs(original.patternTimes()), representedInputs(rewritten.patternTimes()));
        assertEquals(representedOutputs(original.patternTimes()), representedOutputs(rewritten.patternTimes()));

        var entry = rewritten.patternTimes().entrySet().iterator().next();
        ScaledProcessingPattern scaled = assertInstanceOf(ScaledProcessingPattern.class, entry.getKey());
        assertEquals(4_096L, scaled.getOperationsPerPush());
        assertEquals(1L, entry.getValue());
        assertThrows(UnsupportedOperationException.class, rewritten.patternTimes()::clear);
    }

    @Test
    void partitionsAcrossLocalProvidersAndDoesNotScaleTwice() {
        TestPattern pattern = new TestPattern(1L, 1L);
        CraftingPlan original = plan(pattern, 10L);
        List<ICraftingProvider> providers = List.of(
                new LocalProvider(pattern),
                new ExternalProvider(pattern),
                new LocalProvider(pattern),
                new LocalProvider(pattern));

        ICraftingPlan first = SmartDoublingPlans.rewriteForSubmission(original, ignored -> providers);

        assertEquals(Map.of(3L, 2L, 4L, 1L), multipliers(first.patternTimes()));
        assertEquals(10L, representedOperations(first.patternTimes()));
        assertSame(first, SmartDoublingPlans.rewriteForSubmission(first, ignored -> providers));
    }

    @Test
    void leavesPlanWithoutLocalProvidersUntouched() {
        TestPattern pattern = new TestPattern(1L, 1L);
        CraftingPlan original = plan(pattern, 1_000L);

        ICraftingPlan result = SmartDoublingPlans.rewriteForSubmission(
                original, ignored -> List.of(new ExternalProvider(pattern)));

        assertSame(original, result);
    }

    @Test
    void scalesDynamicComponentPatternsFromEcoPlanner() {
        DynamicTestPattern pattern = new DynamicTestPattern(1L, 1L);
        CraftingPlan original = plan(pattern, 25L);

        ICraftingPlan result = SmartDoublingPlans.rewriteForSubmission(
                original, ignored -> List.of(new LocalProvider(pattern)));

        ScaledProcessingPattern scaled = assertInstanceOf(
                ScaledProcessingPattern.class, result.patternTimes().keySet().iterator().next());
        assertSame(pattern, SmartDoublingPatterns.unwrap(scaled));
        DynamicPatternExecution.Resolved dynamic = Objects.requireNonNull(
                DynamicPatternExecution.resolve(scaled));
        assertSame(pattern, dynamic.pattern());
        assertEquals(25L, dynamic.copies());
    }

    @Test
    void retainsAllOperationsWhenAmountsLimitMultiplierToOne() {
        TestPattern pattern = new TestPattern(Long.MAX_VALUE, 1L);
        CraftingPlan original = plan(pattern, Long.MAX_VALUE);

        ICraftingPlan result = SmartDoublingPlans.rewriteForSubmission(
                original, ignored -> List.of(new LocalProvider(pattern)));

        assertEquals(Long.MAX_VALUE, representedOperations(result.patternTimes()));
        assertEquals(Map.of(1L, Long.MAX_VALUE), multipliers(result.patternTimes()));
    }

    private static CraftingPlan plan(IPatternDetails pattern, long operations) {
        return new CraftingPlan(
                pattern.getPrimaryOutput(), 1L, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, operations));
    }

    private static KeyCounter counter(@Nullable AEKey key, long amount) {
        KeyCounter result = new KeyCounter();
        if (key != null) {
            result.add(key, amount);
        }
        return result;
    }

    private static Map<Long, Long> multipliers(Map<IPatternDetails, Long> patterns) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (var entry : patterns.entrySet()) {
            result.put(SmartDoublingPatterns.operationsPerPush(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static long representedOperations(Map<IPatternDetails, Long> patterns) {
        long result = 0L;
        for (var entry : patterns.entrySet()) {
            result = Math.addExact(result, Math.multiplyExact(
                    SmartDoublingPatterns.operationsPerPush(entry.getKey()), entry.getValue()));
        }
        return result;
    }

    private static long representedInputs(Map<IPatternDetails, Long> patterns) {
        long result = 0L;
        for (var entry : patterns.entrySet()) {
            for (IPatternDetails.IInput input : entry.getKey().getInputs()) {
                GenericStack possible = input.getPossibleInputs()[0];
                result = Math.addExact(result, Math.multiplyExact(
                        Math.multiplyExact(possible.amount(), input.getMultiplier()), entry.getValue()));
            }
        }
        return result;
    }

    private static long representedOutputs(Map<IPatternDetails, Long> patterns) {
        long result = 0L;
        for (var entry : patterns.entrySet()) {
            for (GenericStack output : entry.getKey().getOutputs()) {
                result = Math.addExact(result, Math.multiplyExact(output.amount(), entry.getValue()));
            }
        }
        return result;
    }

    private static class TestPattern implements IPatternDetails {
        private final AEItemKey definition = Objects.requireNonNull(AEItemKey.of(Items.PAPER));
        private final IInput[] inputs;
        private final List<GenericStack> outputs;

        private TestPattern(long inputMultiplier, long outputAmount) {
            AEItemKey input = Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT));
            AEItemKey output = Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT));
            this.inputs = new IInput[]{new TestInput(input, inputMultiplier)};
            this.outputs = List.of(new GenericStack(output, outputAmount));
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
    }

    private static final class DynamicTestPattern extends TestPattern implements DynamicComponentPattern {
        private DynamicTestPattern(long inputMultiplier, long outputAmount) {
            super(inputMultiplier, outputAmount);
        }

        @Override
        public String dynamicPatternIdentity() {
            return "test:eco_submission";
        }

        @Override
        public boolean isItemIdInput(int slot) {
            return slot == 0;
        }

        @Override
        public boolean isItemIdOutput(int slot) {
            return slot == 0;
        }

        @Override
        public boolean usesDynamicOutputs() {
            return true;
        }
    }

    private record TestInput(AEKey key, long multiplier) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, 1L)};
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return key.equals(input);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private static class ExternalProvider implements ICraftingProvider {
        private final IPatternDetails pattern;

        private ExternalProvider(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(pattern);
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            return false;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private static final class LocalProvider extends ExternalProvider
            implements SmartDoublingCraftingProvider {
        private LocalProvider(IPatternDetails pattern) {
            super(pattern);
        }
    }
}
