package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.registry.provider.definition.ProviderIdentityDescriptor;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledProcessingPattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlloyFurnaceCountedCraftingAdapterTest {
    private static final CountedCraftingTarget TARGET =
            CountedCraftingTarget.machine("test-route", "test-machine");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void publishesTargetedSafeCapacityForAnOnlineProvider() {
        TestPattern pattern = pattern(2L, 2L, 3L);
        AEItemKey input = itemKey(Items.IRON_INGOT);
        AlloyFurnaceCountedCraftingAdapter adapter = new AlloyFurnaceCountedCraftingAdapter(
                new TestProvider(pattern), () -> true, TARGET);

        List<CountedCraftingCapacity> capacities = adapter.captureCapacity(
                pattern, prototype(input, 2L), 7L);

        assertEquals(1, capacities.size());
        CountedCraftingCapacity capacity = capacities.getFirst();
        assertEquals(TARGET, capacity.target());
        assertEquals(7L, capacity.logicalCrafts().orElseThrow());
        assertEquals(7L, capacity.maximumSingleBatch().orElseThrow());
    }

    @Test
    void publishesTargetedSafeCapacityForAnAlreadyScaledPattern() {
        TestPattern basePattern = pattern(2L, 2L, 3L);
        AEItemKey input = itemKey(Items.IRON_INGOT);
        AlloyFurnaceCountedCraftingAdapter adapter = new AlloyFurnaceCountedCraftingAdapter(
                new TestProvider(basePattern), () -> true, TARGET);

        List<CountedCraftingCapacity> capacities = adapter.captureCapacity(
                new ScaledProcessingPattern(basePattern, 4L), prototype(input, 2L), 7L);

        assertEquals(1, capacities.size());
        CountedCraftingCapacity capacity = capacities.getFirst();
        assertEquals(TARGET, capacity.target());
        assertEquals(7L, capacity.logicalCrafts().orElseThrow());
        assertEquals(7L, capacity.maximumSingleBatch().orElseThrow());
    }

    @Test
    void derivesAStableMachineTargetFromTheProviderIdentity() {
        CountedCraftingTarget target = AlloyFurnaceCountedCraftingAdapter.targetFor(
                new TestIdentity("useless_mod:provider-position"));

        assertEquals(CountedCraftingTarget.machine(
                "useless_mod:provider-position", "useless_mod:provider-position"), target);
    }

    @Test
    void submitsOneScaledPatternWithoutMutatingTheOriginalPrototype() {
        TestPattern basePattern = pattern(2L, 2L, 3L);
        AEItemKey input = itemKey(Items.IRON_INGOT);
        IPatternDetails alreadyScaled = new ScaledProcessingPattern(basePattern, 4L);
        TestProvider provider = new TestProvider(basePattern);
        AlloyFurnaceCountedCraftingAdapter adapter = new AlloyFurnaceCountedCraftingAdapter(
                provider, () -> true, TARGET);
        KeyCounter[] prototype = prototype(input, 8L);

        CountedCraftingAdmission admission = adapter.prepareBatchForTarget(
                alreadyScaled, prototype, 3L, TARGET);

        assertNotNull(admission);
        assertEquals(3L, admission.count());
        assertTrue(admission.commit(prototype));
        assertTrue(admission.hasTransferredInputOwnership());
        assertEquals(1, provider.pushCalls);
        ScaledProcessingPattern submittedPattern = assertInstanceOf(
                ScaledProcessingPattern.class, provider.submittedPattern);
        assertEquals(12L, submittedPattern.getOperationsPerPush());
        KeyCounter[] submittedInputs = provider.submittedInputs;
        assertEquals(24L, submittedInputs[0].get(input));
        assertEquals(8L, prototype[0].get(input));
        assertThrows(IllegalStateException.class, () -> admission.commit(prototype));
        assertEquals(1, provider.pushCalls);
    }

    @Test
    void limitsTheBatchBeforeInputOutputOrMultiplierOverflow() {
        AEItemKey input = itemKey(Items.IRON_INGOT);

        long maximum = AlloyFurnaceCountedCraftingAdapter.maximumBatchCount(
                pattern(1L, 1L, 1L), prototype(input, Long.MAX_VALUE), 2L);

        assertEquals(1L, maximum);
        assertEquals(1L, AlloyFurnaceCountedCraftingAdapter.maximumBatchCount(
                pattern(Long.MAX_VALUE, 1L, 1L), prototype(input, 1L), 2L));
        assertEquals(1L, AlloyFurnaceCountedCraftingAdapter.maximumBatchCount(
                pattern(1L, 1L, Long.MAX_VALUE), prototype(input, 1L), 2L));
        assertEquals(1L, AlloyFurnaceCountedCraftingAdapter.maximumBatchCount(
                new ScaledProcessingPattern(pattern(1L, 1L, 1L), Long.MAX_VALUE),
                prototype(input, 1L), 2L));
    }

    @Test
    void rejectsOfflineMissingAndWrongTargetSubmissionsBeforeDispatch() {
        TestPattern pattern = pattern(1L, 1L, 1L);
        AEItemKey input = itemKey(Items.IRON_INGOT);
        KeyCounter[] prototype = prototype(input, 1L);
        TestProvider onlineProvider = new TestProvider(pattern);
        AlloyFurnaceCountedCraftingAdapter offlineAdapter = new AlloyFurnaceCountedCraftingAdapter(
                onlineProvider, () -> false, TARGET);

        assertTrue(offlineAdapter.captureCapacity(pattern, prototype, 1L).isEmpty());
        assertNull(offlineAdapter.prepareBatch(pattern, prototype, 1L));
        assertEquals(0, onlineProvider.pushCalls);

        AlloyFurnaceCountedCraftingAdapter missingPatternAdapter = new AlloyFurnaceCountedCraftingAdapter(
                new TestProvider(), () -> true, TARGET);
        assertTrue(missingPatternAdapter.captureCapacity(pattern, prototype, 1L).isEmpty());
        assertNull(missingPatternAdapter.prepareBatch(pattern, prototype, 1L));

        CountedCraftingTarget wrongTarget = CountedCraftingTarget.machine("other-route", "other-machine");
        AlloyFurnaceCountedCraftingAdapter onlineAdapter = new AlloyFurnaceCountedCraftingAdapter(
                onlineProvider, () -> true, TARGET);
        assertNull(onlineAdapter.prepareBatchForTarget(pattern, prototype, 1L, wrongTarget));
        assertEquals(0, onlineProvider.pushCalls);
    }

    @Test
    void rejectedProviderDoesNotTransferInputOwnership() {
        TestPattern pattern = pattern(1L, 1L, 1L);
        AEItemKey input = itemKey(Items.IRON_INGOT);
        TestProvider provider = new TestProvider(pattern);
        provider.accept = false;
        AlloyFurnaceCountedCraftingAdapter adapter = new AlloyFurnaceCountedCraftingAdapter(
                provider, () -> true, TARGET);
        KeyCounter[] prototype = prototype(input, 1L);

        CountedCraftingAdmission admission = adapter.prepareBatch(pattern, prototype, 2L);

        assertNotNull(admission);
        assertFalse(admission.commit(prototype));
        assertFalse(admission.hasTransferredInputOwnership());
        assertEquals(1, provider.pushCalls);
        assertEquals(1L, prototype[0].get(input));
    }

    private static TestPattern pattern(long inputMultiplier, long possibleInputAmount, long outputAmount) {
        AEItemKey definition = itemKey(Items.PAPER);
        AEItemKey input = itemKey(Items.IRON_INGOT);
        AEItemKey output = itemKey(Items.GOLD_INGOT);
        return new TestPattern(
                definition,
                new IPatternDetails.IInput[]{new TestInput(input, inputMultiplier, possibleInputAmount)},
                List.of(new GenericStack(output, outputAmount)));
    }

    private static AEItemKey itemKey(Item item) {
        AEItemKey key = AEItemKey.of(item);
        if (key == null) {
            throw new AssertionError("Registered test item must produce an AE item key");
        }
        return key;
    }

    private static KeyCounter[] prototype(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return new KeyCounter[]{counter};
    }

    private static final class TestProvider implements ICraftingProvider {
        private final List<IPatternDetails> patterns;
        private boolean accept = true;
        private int pushCalls;
        @Nullable
        private IPatternDetails submittedPattern;
        private KeyCounter[] submittedInputs = new KeyCounter[0];

        private TestProvider(IPatternDetails... patterns) {
            this.patterns = List.of(patterns);
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return patterns;
        }

        @Override
        public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            pushCalls++;
            submittedPattern = patternDetails;
            submittedInputs = inputHolder;
            return accept;
        }

        @Override
        public boolean isBusy() {
            return false;
        }
    }

    private record TestPattern(
            AEItemKey definition, IPatternDetails.IInput[] inputs, List<GenericStack> outputs) implements IPatternDetails {
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

    private record TestInput(AEKey key, long multiplier, long possibleInputAmount)
            implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, possibleInputAmount)};
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
        public @Nullable AEKey getRemainingKey(AEKey template) {
            return null;
        }
    }

    private record TestIdentity(String digest) implements PatternProviderIdentity {
        @Override
        public int version() {
            return 1;
        }

        @Override
        public @NotNull Optional<ProviderIdentityDescriptor> descriptor() {
            return Optional.empty();
        }
    }
}
