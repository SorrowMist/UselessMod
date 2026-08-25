package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingPlan;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.me.helpers.BaseActionSource;
import appeng.me.service.CraftingService;
import com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider;
import com.sorrowmist.useless.mixin.ae2.CraftingSimulationStateAccessor;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartDoublingCraftingPlanMixinTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rewritesAePlanAndFiltersScaledProvidersToLocalMachines() {
        IPatternDetails pattern = pattern();
        LocalProvider firstLocal = new LocalProvider(pattern);
        ExternalProvider external = new ExternalProvider(pattern);
        LocalProvider secondLocal = new LocalProvider(pattern);
        TestCraftingEnvironment environment = createCraftingEnvironment();
        CraftingService service = environment.service();
        service.addGlobalCraftingProvider(firstLocal);
        service.addGlobalCraftingProvider(external);
        service.addGlobalCraftingProvider(secondLocal);

        PlanningState state = new PlanningState();
        ((CraftingSimulationStateAccessor) (Object) state).uselessMod$getCrafts().put(pattern, 5L);
        CraftingCalculation calculation = new CraftingCalculation(
                null, environment.grid(), requester(), pattern.getPrimaryOutput(),
                CalculationStrategy.REPORT_MISSING_ITEMS);

        CraftingPlan plan = CraftingSimulationState.buildCraftingPlan(state, calculation, 1L);

        assertEquals(2, plan.patternTimes().size());
        assertEquals(5L, representedOperations(plan.patternTimes()));
        assertEquals(List.of(2L, 3L), plan.patternTimes().keySet().stream()
                .map(patternDetails -> assertInstanceOf(
                        ScaledProcessingPattern.class, patternDetails).getOperationsPerPush())
                .sorted()
                .toList());
        for (IPatternDetails scaled : plan.patternTimes().keySet()) {
            assertInstanceOf(ScaledProcessingPattern.class, scaled);
            List<ICraftingProvider> providers = toList(service.getProviders(scaled));
            assertEquals(List.of(firstLocal, secondLocal), providers);
            assertFalse(providers.contains(external));
            assertTrue(providers.stream().allMatch(
                    SmartDoublingCraftingProvider.class::isInstance));
        }
    }

    private static TestCraftingEnvironment createCraftingEnvironment() {
        KeyCounter cachedInventory = new KeyCounter();
        IStorageService storage = proxy(IStorageService.class, (ignored, method, arguments) -> {
            if (method.getName().equals("getCachedInventory")) {
                return cachedInventory;
            }
            if (method.getName().equals("getInventory")) {
                return null;
            }
            return defaultValue(method.getReturnType());
        });
        IEnergyService energy = proxy(IEnergyService.class,
                (ignored, method, arguments) -> defaultValue(method.getReturnType()));
        AtomicReference<CraftingService> reference = new AtomicReference<>();
        IGrid grid = proxy(IGrid.class, (ignored, method, arguments) -> switch (method.getName()) {
            case "getStorageService" -> storage;
            case "getEnergyService" -> energy;
            case "getCraftingService" -> reference.get();
            case "getMachines" -> Set.of();
            default -> defaultValue(method.getReturnType());
        });
        CraftingService service = new CraftingService(grid, storage, energy);
        reference.set(service);
        return new TestCraftingEnvironment(service, grid);
    }

    private static ICraftingSimulationRequester requester() {
        return new ICraftingSimulationRequester() {
            private final IActionSource actionSource = new BaseActionSource();

            @Override
            public IActionSource getActionSource() {
                return actionSource;
            }

            @Override
            public IGridNode getGridNode() {
                return null;
            }
        };
    }

    private static long representedOperations(Map<IPatternDetails, Long> patterns) {
        long operations = 0L;
        for (var entry : patterns.entrySet()) {
            ScaledProcessingPattern scaled = assertInstanceOf(
                    ScaledProcessingPattern.class, entry.getKey());
            operations = Math.addExact(operations,
                    Math.multiplyExact(scaled.getOperationsPerPush(), entry.getValue()));
        }
        return operations;
    }

    private static List<ICraftingProvider> toList(Iterable<ICraftingProvider> providers) {
        List<ICraftingProvider> result = new ArrayList<>();
        providers.forEach(result::add);
        return result;
    }

    private static IPatternDetails pattern() {
        AEItemKey definition = Objects.requireNonNull(AEItemKey.of(Items.PAPER));
        AEItemKey input = Objects.requireNonNull(AEItemKey.of(Items.IRON_INGOT));
        AEItemKey output = Objects.requireNonNull(AEItemKey.of(Items.GOLD_INGOT));
        return new IPatternDetails() {
            private final IInput[] inputs = {new TestInput(input)};

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
                return List.of(new GenericStack(output, 1L));
            }
        };
    }

    private record TestInput(AEKey key) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, 1L)};
        }

        @Override
        public long getMultiplier() {
            return 1L;
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

    private static final class PlanningState extends CraftingSimulationState {
        @Override
        protected long simulateExtractParent(AEKey what, long amount) {
            return 0L;
        }

        @Override
        protected Iterable<AEKey> findFuzzyParent(AEKey input) {
            return List.of();
        }
    }

    private record TestCraftingEnvironment(CraftingService service, IGrid grid) {
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }
}
