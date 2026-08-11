package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** Plans and commits one passive pattern extraction without partially consuming network inputs. */
public final class PassivePatternInputTransaction {
    private PassivePatternInputTransaction() {
    }

    public enum Failure {
        NONE,
        MISSING_INPUT,
        AMOUNT_OVERFLOW,
        STORAGE_CHANGED
    }

    public record Result(Failure failure, KeyCounter[] inputs, @Nullable AEKey missingKey) {
        public Result {
            failure = Objects.requireNonNull(failure, "failure");
            inputs = inputs == null ? new KeyCounter[0] : inputs;
        }

        public boolean successful() {
            return failure == Failure.NONE;
        }
    }

    @FunctionalInterface
    public interface UnreturnedInputSink {
        void accept(AEKey key, long amount);
    }

    public static Result extract(IPatternDetails pattern, long operations, @Nullable Level level,
                                 MEStorage storage, Supplier<KeyCounter> cachedInventory,
                                 IActionSource source,
                                 UnreturnedInputSink unreturnedInputSink) {
        return extractAll(List.of(pattern), operations, level, storage, cachedInventory, source,
                unreturnedInputSink).getFirst();
    }

    /** Simulates and commits each pattern in order without enumerating the network for exact inputs. */
    public static List<Result> extractAll(List<? extends IPatternDetails> patterns, long operations,
                                          @Nullable Level level, MEStorage storage,
                                          Supplier<KeyCounter> cachedInventory, IActionSource source,
                                          UnreturnedInputSink unreturnedInputSink) {
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(cachedInventory, "cachedInventory");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(unreturnedInputSink, "unreturnedInputSink");

        AvailableInputIndex inputIndex = new AvailableInputIndex(cachedInventory);
        List<Result> results = new ArrayList<>(patterns.size());
        for (IPatternDetails pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            PlannedExtraction entry = plan(
                    pattern, operations, level, storage, source, inputIndex);
            if (entry.failure != Failure.NONE) {
                results.add(new Result(entry.failure, entry.inputs, entry.missingKey));
                continue;
            }
            results.add(commit(storage, source, entry, unreturnedInputSink));
        }
        return List.copyOf(results);
    }

    private static Result commit(MEStorage storage, IActionSource source,
                                 PlannedExtraction planned,
                                 UnreturnedInputSink unreturnedInputSink) {
        KeyCounter extracted = new KeyCounter();
        for (var entry : planned.consumed) {
            long amount = storage.extract(
                    entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            if (amount > 0) {
                extracted.add(entry.getKey(), amount);
            }
            if (amount < entry.getLongValue()) {
                rollback(storage, source, extracted, unreturnedInputSink);
                return new Result(Failure.STORAGE_CHANGED, new KeyCounter[0], entry.getKey());
            }
        }
        return new Result(Failure.NONE, planned.inputs, null);
    }

    private static PlannedExtraction plan(
            IPatternDetails pattern, long operations, @Nullable Level level,
            MEStorage storage, IActionSource source, AvailableInputIndex inputIndex) {
        if (operations <= 0) {
            return PlannedExtraction.failure(Failure.AMOUNT_OVERFLOW, null);
        }

        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        DynamicComponentPattern dynamic = pattern instanceof DynamicComponentPattern value ? value : null;
        List<SlotDemand> demands = new ArrayList<>();
        Map<AEKey, Long> availableByKey = new LinkedHashMap<>();
        KeyCounter[] selectedInputs = new KeyCounter[patternInputs.length];

        for (int slot = 0; slot < patternInputs.length; slot++) {
            IPatternDetails.IInput input = patternInputs[slot];
            selectedInputs[slot] = new KeyCounter();
            if (input == null) {
                continue;
            }

            long required;
            try {
                required = Math.multiplyExact(input.getMultiplier(), operations);
            } catch (ArithmeticException exception) {
                return PlannedExtraction.failure(Failure.AMOUNT_OVERFLOW, firstPossibleKey(input));
            }
            if (required < 0) {
                return PlannedExtraction.failure(Failure.AMOUNT_OVERFLOW, firstPossibleKey(input));
            }

            if (required <= 0) {
                continue;
            }

            Set<AEKey> candidates = new LinkedHashSet<>();
            GenericStack[] possibleInputs = input.getPossibleInputs();
            if (possibleInputs != null) {
                for (GenericStack possible : possibleInputs) {
                    if (possible == null || possible.what() == null) {
                        continue;
                    }
                    addCandidate(candidates, possible.what(), input, level);
                }
            }

            if (dynamic != null) {
                if (dynamic.isFluidTagInput(slot)) {
                    for (AEKey candidate : inputIndex.allFluidVariants()) {
                        addCandidate(candidates, candidate, input, level);
                    }
                } else if (dynamic.isItemIdInput(slot)) {
                    if (dynamic.isTagInput(slot)) {
                        for (AEKey candidate : inputIndex.allItemVariants()) {
                            addCandidate(candidates, candidate, input, level);
                        }
                    } else if (possibleInputs != null) {
                        for (GenericStack possible : possibleInputs) {
                            if (possible != null && possible.what() instanceof AEItemKey item) {
                                for (AEKey candidate : inputIndex.itemVariants(item)) {
                                    addCandidate(candidates, candidate, input, level);
                                }
                            }
                        }
                    }
                }
            }

            for (AEKey candidate : candidates) {
                availableByKey.computeIfAbsent(candidate, key -> Math.max(0L,
                        storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source)));
            }
            demands.add(new SlotDemand(slot, required, List.copyOf(candidates)));
        }

        KeyAllocation allocation = allocate(demands, availableByKey, patternInputs.length);
        if (allocation == null) {
            for (SlotDemand demand : demands) {
                if (demand.candidates().isEmpty()
                        || !hasAvailableCandidate(demand, availableByKey)) {
                    return PlannedExtraction.failure(Failure.MISSING_INPUT,
                            firstPossibleKey(patternInputs[demand.slot()]));
                }
            }
            return PlannedExtraction.failure(Failure.MISSING_INPUT,
                    firstPossibleKey(patternInputs[demands.getFirst().slot()]));
        }
        return new PlannedExtraction(Failure.NONE, allocation.selectedInputs(), allocation.consumed(), null);
    }

    private static void addCandidate(Set<AEKey> candidates, @Nullable AEKey candidate,
                                     IPatternDetails.IInput input, @Nullable Level level) {
        if (candidate != null && input.isValid(candidate, level)) {
            candidates.add(candidate);
        }
    }

    private static boolean hasAvailableCandidate(SlotDemand demand, Map<AEKey, Long> availableByKey) {
        for (AEKey candidate : demand.candidates()) {
            if (availableByKey.getOrDefault(candidate, 0L) > 0L) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static KeyAllocation allocate(List<SlotDemand> demands,
                                          Map<AEKey, Long> availableByKey,
                                          int slotCount) {
        if (demands.isEmpty()) {
            return new KeyAllocation(emptyCounters(slotCount), new KeyCounter());
        }

        List<KeySupply> supplies = new ArrayList<>();
        long totalDemand = 0L;
        for (SlotDemand demand : demands) {
            totalDemand = saturatingAdd(totalDemand, demand.amount());
        }
        for (Map.Entry<AEKey, Long> entry : availableByKey.entrySet()) {
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (amount > 0L) {
                supplies.add(new KeySupply(entry.getKey(), amount));
            }
        }
        long totalSupply = 0L;
        for (KeySupply supply : supplies) {
            totalSupply = saturatingAdd(totalSupply, supply.amount());
        }
        if (totalSupply < totalDemand) {
            return null;
        }

        Map<AEKey, Integer> supplyIndexes = new LinkedHashMap<>();
        for (int index = 0; index < supplies.size(); index++) {
            supplyIndexes.put(supplies.get(index).key(), index);
        }

        int source = 0;
        int supplyStart = 1;
        int demandStart = supplyStart + supplies.size();
        int sink = demandStart + demands.size();
        FlowNetwork network = new FlowNetwork(sink + 1);
        for (int index = 0; index < supplies.size(); index++) {
            network.addEdge(source, supplyStart + index, supplies.get(index).amount());
        }

        List<List<AllocationEdge>> demandEdges = new ArrayList<>(demands.size());
        for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
            SlotDemand demand = demands.get(demandIndex);
            List<AllocationEdge> edges = new ArrayList<>();
            for (AEKey candidate : demand.candidates()) {
                Integer supplyIndex = supplyIndexes.get(candidate);
                if (supplyIndex == null) continue;
                FlowEdge edge = network.addEdge(
                        supplyStart + supplyIndex, demandStart + demandIndex, totalDemand);
                edges.add(new AllocationEdge(candidate, edge, totalDemand));
            }
            demandEdges.add(edges);
            network.addEdge(demandStart + demandIndex, sink, demand.amount());
        }

        if (network.maxFlow(source, sink, totalDemand) != totalDemand) {
            return null;
        }

        KeyCounter[] selectedInputs = emptyCounters(slotCount);
        KeyCounter consumed = new KeyCounter();
        for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
            KeyCounter selected = selectedInputs[demands.get(demandIndex).slot()];
            for (AllocationEdge allocationEdge : demandEdges.get(demandIndex)) {
                long amount = allocationEdge.initialCapacity() - allocationEdge.edge().capacity;
                if (amount > 0L) {
                    selected.add(allocationEdge.key(), amount);
                    consumed.add(allocationEdge.key(), amount);
                }
            }
        }
        return new KeyAllocation(selectedInputs, consumed);
    }

    private static KeyCounter[] emptyCounters(int size) {
        KeyCounter[] counters = new KeyCounter[size];
        for (int index = 0; index < size; index++) {
            counters[index] = new KeyCounter();
        }
        return counters;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static void rollback(MEStorage storage, IActionSource source, KeyCounter extracted,
                                 UnreturnedInputSink unreturnedInputSink) {
        for (var entry : extracted) {
            long inserted = storage.insert(
                    entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            long remainder = entry.getLongValue() - Math.max(0L, inserted);
            if (remainder > 0) {
                unreturnedInputSink.accept(entry.getKey(), remainder);
            }
        }
    }

    @Nullable
    private static AEKey firstPossibleKey(IPatternDetails.IInput input) {
        for (GenericStack possible : input.getPossibleInputs()) {
            if (possible != null && possible.what() != null) {
                return possible.what();
            }
        }
        return null;
    }

    record PlannedExtraction(Failure failure, KeyCounter[] inputs,
                             KeyCounter consumed, @Nullable AEKey missingKey) {
        static PlannedExtraction failure(Failure failure, @Nullable AEKey missingKey) {
            return new PlannedExtraction(failure, new KeyCounter[0], new KeyCounter(), missingKey);
        }
    }

    private record SlotDemand(int slot, long amount, List<AEKey> candidates) {
    }

    private record KeySupply(AEKey key, long amount) {
    }

    private record AllocationEdge(AEKey key, FlowEdge edge, long initialCapacity) {
    }

    private record KeyAllocation(KeyCounter[] selectedInputs, KeyCounter consumed) {
    }

    private static final class FlowNetwork {
        private final List<List<FlowEdge>> graph;
        private final int[] levels;
        private final int[] nextEdges;

        private FlowNetwork(int nodeCount) {
            this.graph = new ArrayList<>(nodeCount);
            for (int index = 0; index < nodeCount; index++) {
                this.graph.add(new ArrayList<>());
            }
            this.levels = new int[nodeCount];
            this.nextEdges = new int[nodeCount];
        }

        private FlowEdge addEdge(int from, int to, long capacity) {
            FlowEdge forward = new FlowEdge(to, this.graph.get(to).size(), capacity);
            FlowEdge reverse = new FlowEdge(from, this.graph.get(from).size(), 0L);
            this.graph.get(from).add(forward);
            this.graph.get(to).add(reverse);
            return forward;
        }

        private long maxFlow(int source, int sink, long limit) {
            long flow = 0L;
            while (flow < limit && buildLevels(source, sink)) {
                Arrays.fill(this.nextEdges, 0);
                long pushed;
                while (flow < limit
                        && (pushed = push(source, sink, limit - flow)) > 0L) {
                    flow += pushed;
                }
            }
            return flow;
        }

        private boolean buildLevels(int source, int sink) {
            Arrays.fill(this.levels, -1);
            this.levels[source] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (FlowEdge edge : this.graph.get(node)) {
                    if (edge.capacity > 0L && this.levels[edge.to] < 0) {
                        this.levels[edge.to] = this.levels[node] + 1;
                        queue.addLast(edge.to);
                    }
                }
            }
            return this.levels[sink] >= 0;
        }

        private long push(int node, int sink, long amount) {
            if (node == sink) return amount;
            List<FlowEdge> edges = this.graph.get(node);
            for (; this.nextEdges[node] < edges.size(); this.nextEdges[node]++) {
                FlowEdge edge = edges.get(this.nextEdges[node]);
                if (edge.capacity <= 0L || this.levels[edge.to] != this.levels[node] + 1) {
                    continue;
                }
                long pushed = push(edge.to, sink, Math.min(amount, edge.capacity));
                if (pushed <= 0L) continue;
                edge.capacity -= pushed;
                this.graph.get(edge.to).get(edge.reverseIndex).capacity += pushed;
                return pushed;
            }
            return 0L;
        }
    }

    private static final class FlowEdge {
        private final int to;
        private final int reverseIndex;
        private long capacity;

        private FlowEdge(int to, int reverseIndex, long capacity) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.capacity = capacity;
        }
    }

    /** Lazily enumerates network keys only when a component-relaxed input needs variants. */
    private static final class AvailableInputIndex {
        private final Supplier<KeyCounter> cachedInventorySupplier;
        private final Map<Item, List<AEKey>> itemVariants = new IdentityHashMap<>();
        private List<AEKey> allItemVariants;
        private List<AEKey> allFluidVariants;
        private KeyCounter cachedInventory;

        private AvailableInputIndex(Supplier<KeyCounter> cachedInventorySupplier) {
            this.cachedInventorySupplier = cachedInventorySupplier;
        }

        private KeyCounter cachedInventory() {
            if (cachedInventory == null) {
                cachedInventory = Objects.requireNonNull(
                        cachedInventorySupplier.get(), "cached inventory supplier returned null");
            }
            return cachedInventory;
        }

        private List<AEKey> itemVariants(AEItemKey template) {
            return itemVariants.computeIfAbsent(template.getItem(), ignored -> {
                List<AEKey> variants = new ArrayList<>();
                for (var entry : cachedInventory().findFuzzy(template, FuzzyMode.IGNORE_ALL)) {
                    if (entry.getLongValue() > 0L) {
                        variants.add(entry.getKey());
                    }
                }
                return List.copyOf(variants);
            });
        }

        private List<AEKey> allItemVariants() {
            if (allItemVariants == null) {
                List<AEKey> variants = new ArrayList<>();
                for (var entry : cachedInventory()) {
                    if (entry.getLongValue() > 0L && entry.getKey() instanceof AEItemKey) {
                        variants.add(entry.getKey());
                    }
                }
                allItemVariants = List.copyOf(variants);
            }
            return allItemVariants;
        }

        private List<AEKey> allFluidVariants() {
            if (allFluidVariants == null) {
                List<AEKey> variants = new ArrayList<>();
                for (var entry : cachedInventory()) {
                    if (entry.getLongValue() > 0L && entry.getKey() instanceof appeng.api.stacks.AEFluidKey) {
                        variants.add(entry.getKey());
                    }
                }
                allFluidVariants = List.copyOf(variants);
            }
            return allFluidVariants;
        }
    }
}
