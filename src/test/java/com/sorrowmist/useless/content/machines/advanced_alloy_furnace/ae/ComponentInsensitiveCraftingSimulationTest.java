package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.me.helpers.BaseActionSource;
import appeng.me.service.helpers.NetworkCraftingProviders;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentInsensitiveCraftingSimulationTest {
    private static final Level LEVEL = allocateLevel();

    @Test
    void dynamicParentRecursivelyPlansThreeComponentBearingChildOutputs() {
        ItemStack sword = namedStack(Items.DIAMOND_SWORD, "sword-output-components");
        ItemStack pickaxe = namedStack(Items.DIAMOND_PICKAXE, "pickaxe-output-components");
        ItemStack shovel = namedStack(Items.DIAMOND_SHOVEL, "shovel-output-components");
        ItemStack wand = namedStack(Items.NETHER_STAR, "wand-output-components");

        IPatternDetails swordPattern = dynamicPattern(
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(sword), List.of(), List.of(0));
        IPatternDetails pickaxePattern = dynamicPattern(
                List.of(new ItemStack(Items.GOLD_INGOT)), List.of(pickaxe), List.of(), List.of(0));
        IPatternDetails shovelPattern = dynamicPattern(
                List.of(new ItemStack(Items.DIAMOND)), List.of(shovel), List.of(), List.of(0));
        IPatternDetails wandPattern = dynamicPattern(
                List.of(
                        namedStack(Items.DIAMOND_SWORD, "parent-jei-sword-components"),
                        namedStack(Items.DIAMOND_PICKAXE, "parent-jei-pickaxe-components"),
                        namedStack(Items.DIAMOND_SHOVEL, "parent-jei-shovel-components")),
                List.of(wand), List.of(0, 1, 2), List.of(0));

        SimulationNetwork network = new SimulationNetwork();
        network.addProvider(List.of(wandPattern, swordPattern, pickaxePattern, shovelPattern));
        network.addStored(AEItemKey.of(Items.IRON_INGOT), 1);
        network.addStored(AEItemKey.of(Items.GOLD_INGOT), 1);
        network.addStored(AEItemKey.of(Items.DIAMOND), 1);

        ICraftingPlan plan = network.calculate(Objects.requireNonNull(GenericStack.fromItemStack(wand)));

        assertFalse(plan.simulation(), () -> "Unexpected missing inputs: " + plan.missingItems());
        assertEquals(1L, plan.patternTimes().get(wandPattern));
        assertEquals(1L, plan.patternTimes().get(swordPattern));
        assertEquals(1L, plan.patternTimes().get(pickaxePattern));
        assertEquals(1L, plan.patternTimes().get(shovelPattern));
    }

    @Test
    void overloadStyleAndLocalDynamicProvidersCanCoexistDuringRecursivePlanning() {
        ItemStack localSword = namedStack(Items.DIAMOND_SWORD, "local-sword-output");
        ItemStack overloadSword = namedStack(Items.DIAMOND_SWORD, "overload-sword-output");
        ItemStack wand = namedStack(Items.NETHER_STAR, "shared-wand-output");

        IPatternDetails localChild = dynamicPattern(
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(localSword), List.of(), List.of(0));
        IPatternDetails overloadChild = overloadStylePattern(
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(overloadSword), Set.of(), Set.of(0));
        IPatternDetails localParent = dynamicPattern(
                List.of(namedStack(Items.DIAMOND_SWORD, "local-parent-jei-input")),
                List.of(wand), List.of(0), List.of(0));
        IPatternDetails overloadParent = overloadStylePattern(
                List.of(namedStack(Items.DIAMOND_SWORD, "overload-parent-jei-input")),
                List.of(wand), Set.of(0), Set.of(0));

        SimulationNetwork network = new SimulationNetwork();
        network.addProvider(List.of(localParent, localChild));
        network.addProvider(List.of(overloadParent, overloadChild));
        network.addStored(AEItemKey.of(Items.IRON_INGOT), 1);

        ICraftingPlan plan = network.calculate(Objects.requireNonNull(GenericStack.fromItemStack(wand)));

        assertFalse(plan.simulation(), () -> "Unexpected missing inputs: " + plan.missingItems());
        assertTrue(plan.patternTimes().containsKey(localParent)
                || plan.patternTimes().containsKey(overloadParent));
        assertTrue(plan.patternTimes().containsKey(localChild)
                || plan.patternTimes().containsKey(overloadChild));
    }

    @Test
    void canonicalChildOutputWinsOverAnUnrelatedFuzzyComponentVariant() {
        ItemStack canonicalSword = namedStack(Items.DIAMOND_SWORD, "static-recipe-output");
        ItemStack jeiSword = namedStack(Items.DIAMOND_SWORD, "parent-and-child-jei-components");
        ItemStack unrelatedSword = namedStack(Items.DIAMOND_SWORD, "unrelated-child-output");
        ItemStack wand = namedStack(Items.NETHER_STAR, "canonical-parent-output");

        IPatternDetails canonicalChild = dynamicPattern(
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(jeiSword), List.of(), List.of(0));
        IPatternDetails unrelatedChild = overloadStylePattern(
                List.of(new ItemStack(Items.EMERALD)), List.of(unrelatedSword), Set.of(), Set.of(0));
        AEProcessingPattern sourceParent = processingPattern(
                List.of(jeiSword), List.of(wand));
        AEProcessingPattern canonicalParent = AdvancedAlloyFurnacePatternResolver.withCanonicalInputs(
                sourceParent, Map.of(0, canonicalSword));
        IPatternDetails parent = new DynamicComponentPatternDetails(
                canonicalParent, List.of(0), List.of(0), RegistryAccess.EMPTY);

        SimulationNetwork network = new SimulationNetwork();
        network.addProvider(List.of(parent, canonicalChild));
        network.addProvider(List.of(unrelatedChild));
        network.preferFuzzy(Objects.requireNonNull(AEItemKey.of(unrelatedSword)));
        network.addStored(AEItemKey.of(Items.IRON_INGOT), 1);

        ICraftingPlan plan = network.calculate(Objects.requireNonNull(GenericStack.fromItemStack(wand)));

        assertFalse(plan.simulation(), () -> "Unexpected missing inputs: " + plan.missingItems());
        assertEquals(1L, plan.patternTimes().get(canonicalChild));
        assertFalse(plan.patternTimes().containsKey(unrelatedChild));
    }

    @Test
    void overloadStyleExactInputIsNotRewrittenToAnAmbiguousFuzzyLookup() {
        ItemStack overloadSword = namedStack(Items.DIAMOND_SWORD, "overload-exact-output");
        ItemStack unrelatedSword = namedStack(Items.DIAMOND_SWORD, "local-unrelated-output");
        ItemStack wand = namedStack(Items.NETHER_STAR, "overload-parent-output");

        IPatternDetails overloadChild = overloadStylePattern(
                List.of(new ItemStack(Items.IRON_INGOT)), List.of(overloadSword), Set.of(), Set.of(0));
        IPatternDetails unrelatedChild = dynamicPattern(
                List.of(new ItemStack(Items.EMERALD)), List.of(unrelatedSword), List.of(), List.of(0));
        IPatternDetails overloadParent = overloadStylePattern(
                List.of(overloadSword), List.of(wand), Set.of(0), Set.of(0));

        SimulationNetwork network = new SimulationNetwork();
        network.addProvider(List.of(overloadParent, overloadChild));
        network.addProvider(List.of(unrelatedChild));
        network.preferFuzzy(Objects.requireNonNull(AEItemKey.of(unrelatedSword)));
        network.addStored(AEItemKey.of(Items.IRON_INGOT), 1);

        ICraftingPlan plan = network.calculate(Objects.requireNonNull(GenericStack.fromItemStack(wand)));

        assertFalse(plan.simulation(), () -> "Unexpected missing inputs: " + plan.missingItems());
        assertEquals(1L, plan.patternTimes().get(overloadChild));
        assertFalse(plan.patternTimes().containsKey(unrelatedChild));
    }

    private static DynamicComponentPatternDetails dynamicPattern(
            List<ItemStack> inputs,
            List<ItemStack> outputs,
            List<Integer> idOnlyInputs,
            List<Integer> idOnlyOutputs) {
        return new DynamicComponentPatternDetails(
                processingPattern(inputs, outputs),
                idOnlyInputs,
                idOnlyOutputs,
                RegistryAccess.EMPTY);
    }

    private static IPatternDetails overloadStylePattern(
            List<ItemStack> inputs,
            List<ItemStack> outputs,
            Set<Integer> idOnlyInputs,
            Set<Integer> idOnlyOutputs) {
        return new OverloadStylePatternDetails(
                processingPattern(inputs, outputs), idOnlyInputs, idOnlyOutputs);
    }

    private static AEProcessingPattern processingPattern(List<ItemStack> inputs, List<ItemStack> outputs) {
        List<GenericStack> encodedInputs = inputs.stream()
                .map(GenericStack::fromItemStack)
                .map(Objects::requireNonNull)
                .toList();
        List<GenericStack> encodedOutputs = outputs.stream()
                .map(GenericStack::fromItemStack)
                .map(Objects::requireNonNull)
                .toList();
        ItemStack definition = PatternDetailsHelper.encodeProcessingPattern(encodedInputs, encodedOutputs);
        return new AEProcessingPattern(Objects.requireNonNull(AEItemKey.of(definition)));
    }

    private static ItemStack namedStack(net.minecraft.world.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static Level allocateLevel() {
        try {
            var field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Level) ((Unsafe) field.get(null)).allocateInstance(ServerLevel.class);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class OverloadStylePatternDetails implements IPatternDetails {
        private final AEProcessingPattern source;
        private final IInput[] inputs;
        private final Set<Integer> idOnlyOutputs;

        private OverloadStylePatternDetails(
                AEProcessingPattern source,
                Set<Integer> idOnlyInputs,
                Set<Integer> idOnlyOutputs) {
            this.source = source;
            IPatternDetails.IInput[] sourceInputs = source.getInputs();
            this.inputs = new IPatternDetails.IInput[sourceInputs.length];
            System.arraycopy(sourceInputs, 0, this.inputs, 0, sourceInputs.length);
            this.idOnlyOutputs = Set.copyOf(idOnlyOutputs);
            for (int slot : idOnlyInputs) {
                this.inputs[slot] = new ItemIdInput(this.inputs[slot]);
            }
        }

        @Override
        public AEItemKey getDefinition() {
            return source.getDefinition();
        }

        @Override
        public IInput[] getInputs() {
            return inputs.clone();
        }

        @Override
        public List<GenericStack> getOutputs() {
            return source.getOutputs();
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof OverloadStylePatternDetails other
                    && getDefinition().equals(other.getDefinition())
                    && idOnlyOutputs.equals(other.idOnlyOutputs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(getDefinition(), idOnlyOutputs);
        }
    }

    private record ItemIdInput(IPatternDetails.IInput source) implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return source.getPossibleInputs();
        }

        @Override
        public long getMultiplier() {
            return source.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            if (!(input instanceof AEItemKey itemKey)) {
                return false;
            }
            for (GenericStack possible : source.getPossibleInputs()) {
                if (possible.what() instanceof AEItemKey possibleItem
                        && possibleItem.getItem() == itemKey.getItem()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            return source.getRemainingKey(template);
        }
    }

    private static final class SimulationNetwork {
        private final NetworkCraftingProviders providerIndex = new NetworkCraftingProviders();
        private final KeyCounter stored = new KeyCounter();
        @Nullable
        private AEKey preferredFuzzy;
        private final ICraftingService craftingService = createCraftingService();
        private final IStorageService storageService = createStorageService();
        private final IGrid grid = proxy(IGrid.class, (proxy, method, args) -> switch (method.getName()) {
            case "getCraftingService" -> craftingService;
            case "getStorageService" -> storageService;
            default -> defaultValue(method.getReturnType());
        });
        private final IGridNode requesterNode = proxy(IGridNode.class,
                (proxy, method, args) -> method.getName().equals("getGrid")
                        ? grid
                        : defaultValue(method.getReturnType()));
        private final ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            private final IActionSource actionSource = new BaseActionSource();

            @Override
            public IActionSource getActionSource() {
                return actionSource;
            }

            @Override
            public IGridNode getGridNode() {
                return requesterNode;
            }
        };

        void addProvider(Collection<IPatternDetails> providerPatterns) {
            ICraftingProvider provider = new ICraftingProvider() {
                @Override
                public List<IPatternDetails> getAvailablePatterns() {
                    return List.copyOf(providerPatterns);
                }

                @Override
                public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                    return false;
                }

                @Override
                public boolean isBusy() {
                    return false;
                }
            };
            IGridNode node = proxy(IGridNode.class, (proxy, method, args) -> {
                if (method.getName().equals("getService")
                        && args != null && args.length == 1 && args[0] == ICraftingProvider.class) {
                    return provider;
                }
                return defaultValue(method.getReturnType());
            });
            providerIndex.addProvider(node);
        }

        void addStored(AEKey key, long amount) {
            stored.add(Objects.requireNonNull(key), amount);
        }

        void preferFuzzy(AEKey key) {
            preferredFuzzy = Objects.requireNonNull(key);
        }

        ICraftingPlan calculate(GenericStack output) {
            CraftingCalculation calculation = new CraftingCalculation(
                    LEVEL, grid, requester, output, CalculationStrategy.REPORT_MISSING_ITEMS);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var future = executor.submit(calculation::run);
                calculation.simulateFor(1_000_000_000);
                return future.get(5, TimeUnit.SECONDS);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                executor.shutdownNow();
            }
        }

        private ICraftingService createCraftingService() {
            return new ICraftingService() {
                @Override
                public Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
                    return providerIndex.getCraftingFor(whatToCraft);
                }

                @Override
                @Nullable
                public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
                    if (preferredFuzzy != null
                            && preferredFuzzy.getPrimaryKey() == whatToCraft.getPrimaryKey()
                            && filter.matches(preferredFuzzy)) {
                        return preferredFuzzy;
                    }
                    return providerIndex.getFuzzyCraftable(whatToCraft, filter);
                }

                @Override
                public boolean canEmitFor(AEKey what) {
                    return providerIndex.canEmitFor(what);
                }

                @Override
                public Set<AEKey> getCraftables(AEKeyFilter filter) {
                    return providerIndex.getCraftables(filter);
                }

                @Override
                public java.util.concurrent.Future<ICraftingPlan> beginCraftingCalculation(
                        Level level,
                        ICraftingSimulationRequester requester,
                        AEKey what,
                        long amount,
                        CalculationStrategy strategy) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ICraftingSubmitResult submitJob(
                        ICraftingPlan job,
                        ICraftingRequester requester,
                        ICraftingCPU target,
                        boolean prioritizePower,
                        IActionSource source) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ImmutableSet<ICraftingCPU> getCpus() {
                    return ImmutableSet.of();
                }

                @Override
                public void refreshNodeCraftingProvider(IGridNode node) {
                }

                @Override
                public void addGlobalCraftingProvider(ICraftingProvider provider) {
                }

                @Override
                public void removeGlobalCraftingProvider(ICraftingProvider provider) {
                }

                @Override
                public void refreshGlobalCraftingProvider(ICraftingProvider provider) {
                }

                @Override
                public boolean isRequesting(AEKey what) {
                    return false;
                }

                @Override
                public long getRequestedAmount(AEKey what) {
                    return 0;
                }

                @Override
                public boolean isRequestingAny() {
                    return false;
                }
            };
        }

        private IStorageService createStorageService() {
            MEStorage inventory = new MEStorage() {
                @Override
                public void getAvailableStacks(KeyCounter output) {
                    output.addAll(stored);
                }

                @Override
                public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                    return Math.min(amount, stored.get(what));
                }

                @Override
                public Component getDescription() {
                    return Component.empty();
                }
            };
            return new IStorageService() {
                @Override
                public MEStorage getInventory() {
                    return inventory;
                }

                @Override
                public KeyCounter getCachedInventory() {
                    return stored;
                }

                @Override
                public void addGlobalStorageProvider(IStorageProvider provider) {
                }

                @Override
                public void removeGlobalStorageProvider(IStorageProvider provider) {
                }

                @Override
                public void refreshNodeStorageProvider(IGridNode node) {
                }

                @Override
                public void refreshGlobalStorageProvider(IStorageProvider provider) {
                }

                @Override
                public void invalidateCache() {
                }
            };
        }
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
        return 0;
    }
}
