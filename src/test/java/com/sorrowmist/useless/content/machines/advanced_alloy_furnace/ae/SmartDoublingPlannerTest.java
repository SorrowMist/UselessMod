package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartDoublingPlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void leavesPatternsWithoutEligibleProvidersUnchanged() {
        IPatternDetails pattern = pattern(2L, 3L);
        Map<IPatternDetails, Long> result = SmartDoublingPlanner.rewrite(
                Map.of(pattern, 10L), ignored -> List.of(new ExternalProvider(pattern)));

        assertEquals(1, result.size());
        assertEquals(10L, result.get(pattern));
        assertSame(pattern, result.keySet().iterator().next());
    }

    @Test
    void combinesAllOperationsForOneLocalProvider() {
        IPatternDetails pattern = pattern(2L, 3L);

        Map<IPatternDetails, Long> result = SmartDoublingPlanner.rewrite(
                Map.of(pattern, 7L), ignored -> List.of(new LocalProvider(pattern)));

        assertEquals(Map.of(7L, 1L), multipliers(result));
        assertEquals(7L, representedOperations(result));
    }

    @Test
    void partitionsOperationsApproximatelyEvenlyAcrossLocalProviders() {
        IPatternDetails pattern = pattern(2L, 3L);
        List<ICraftingProvider> providers = List.of(
                new LocalProvider(pattern), new ExternalProvider(pattern),
                new LocalProvider(pattern), new LocalProvider(pattern));

        Map<IPatternDetails, Long> result = SmartDoublingPlanner.rewrite(
                Map.of(pattern, 10L), ignored -> providers);

        assertEquals(Map.of(3L, 2L, 4L, 1L), multipliers(result));
        assertEquals(10L, representedOperations(result));
        assertEquals(3, SmartDoublingPlanner.eligibleProviders(providers).size());
        assertFalse(SmartDoublingPlanner.eligibleProviders(providers)
                .contains(providers.get(1)));
    }

    @Test
    void usesOneOperationWrappersWhenProvidersOutnumberOperations() {
        IPatternDetails pattern = pattern(1L, 1L);
        List<ICraftingProvider> providers = List.of(
                new LocalProvider(pattern), new LocalProvider(pattern),
                new LocalProvider(pattern), new LocalProvider(pattern));

        Map<IPatternDetails, Long> result = SmartDoublingPlanner.rewrite(
                Map.of(pattern, 2L), ignored -> providers);

        assertEquals(Map.of(1L, 2L), multipliers(result));
        assertTrue(result.keySet().stream().allMatch(ScaledProcessingPattern.class::isInstance));
    }

    @Test
    void addsOverflowBatchesWithoutAllocatingPerOperationEntries() {
        IPatternDetails pattern = pattern(Long.MAX_VALUE, 1L);
        Map<IPatternDetails, Long> result = SmartDoublingPlanner.rewrite(
                Map.of(pattern, Long.MAX_VALUE),
                ignored -> List.of(new LocalProvider(pattern)));

        assertEquals(Map.of(1L, Long.MAX_VALUE), multipliers(result));
        assertEquals(Long.MAX_VALUE, representedOperations(result));
    }

    @Test
    void limitsBatchesForLargePossibleInputAmounts() {
        IPatternDetails pattern = pattern(1L, Long.MAX_VALUE, 1L);
        Map<IPatternDetails, Long> result = SmartDoublingPlanner.rewrite(
                Map.of(pattern, Long.MAX_VALUE),
                ignored -> List.of(new LocalProvider(pattern)));

        assertEquals(1L, SmartDoublingPatterns.maximumSafeMultiplier(pattern));
        assertEquals(Map.of(1L, Long.MAX_VALUE), multipliers(result));
        assertEquals(Long.MAX_VALUE, representedOperations(result));
    }

    @Test
    void wrapperScalesAmountsAndPersistsItsFlattenedMultiplier() {
        IPatternDetails pattern = pattern(2L, 3L);
        ScaledProcessingPattern nested = new ScaledProcessingPattern(
                new ScaledProcessingPattern(pattern, 4L), 3L);

        assertSame(pattern, nested.getOriginal());
        assertEquals(12L, nested.getOperationsPerPush());
        assertEquals(24L, nested.getInputs()[0].getMultiplier());
        assertEquals(36L, nested.getOutputs().getFirst().amount());
        assertEquals(12L, nested.getDefinition().get(
                UComponents.SMART_DOUBLING_OPERATIONS.get()));

        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        AEItemKey restoredDefinition = Objects.requireNonNull(AEItemKey.fromTag(
                registries, nested.getDefinition().toTag(registries)));
        assertEquals(12L, restoredDefinition.get(
                UComponents.SMART_DOUBLING_OPERATIONS.get()));
    }

    private static Map<Long, Long> multipliers(Map<IPatternDetails, Long> patterns) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (var entry : patterns.entrySet()) {
            long multiplier = entry.getKey() instanceof ScaledProcessingPattern scaled
                    ? scaled.getOperationsPerPush()
                    : 1L;
            result.put(multiplier, entry.getValue());
        }
        return result;
    }

    private static long representedOperations(Map<IPatternDetails, Long> patterns) {
        long result = 0L;
        for (var entry : patterns.entrySet()) {
            long multiplier = entry.getKey() instanceof ScaledProcessingPattern scaled
                    ? scaled.getOperationsPerPush()
                    : 1L;
            result = Math.addExact(result, Math.multiplyExact(multiplier, entry.getValue()));
        }
        return result;
    }

    private static IPatternDetails pattern(long inputAmount, long outputAmount) {
        return pattern(inputAmount, 1L, outputAmount);
    }

    private static IPatternDetails pattern(long inputAmount, long possibleInputAmount, long outputAmount) {
        AEItemKey definition = Objects.requireNonNull(AEItemKey.of(Items.PAPER));
        AEItemKey input = Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT));
        AEItemKey output = Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT));
        return new IPatternDetails() {
            private final IInput[] inputs = {new TestInput(input, inputAmount, possibleInputAmount)};

            @Override
            public AEItemKey getDefinition() {
                return definition;
            }

            @Override
            public IInput[] getInputs() {
                return inputs;
            }

            @Override
            public List<GenericStack> getOutputs() {
                return List.of(new GenericStack(output, outputAmount));
            }
        };
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
