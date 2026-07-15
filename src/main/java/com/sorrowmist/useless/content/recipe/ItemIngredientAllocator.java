package com.sorrowmist.useless.content.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Count-aware allocator for item ingredient requirements.
 * <p>
 * A single input amount can only satisfy one requirement. This is important for
 * overlapping tags and component-sensitive custom ingredients, where independent
 * per-ingredient counting can otherwise use the same stack more than once.
 */
public final class ItemIngredientAllocator {
    private ItemIngredientAllocator() {
    }

    public static boolean matches(List<CountedIngredient> requirements, List<ItemStack> inputs, long operations) {
        return allocate(requirements, inputs, operations) != null;
    }

    /**
     * Allocates concrete input stacks to all requirements.
     *
     * @return the amount consumed from each input list entry, or {@code null} when no valid allocation exists
     */
    @Nullable
    public static Allocation allocate(List<CountedIngredient> requirements, List<ItemStack> inputs, long operations) {
        List<ItemStack> safeInputs = inputs == null ? List.of() : inputs;
        List<Supply> supplies = new ArrayList<>();
        for (int i = 0; i < safeInputs.size(); i++) {
            ItemStack stack = safeInputs.get(i);
            if (stack == null || stack.isEmpty() || stack.getCount() <= 0) continue;
            supplies.add(new Supply(i, stack.getCount(), ingredient -> ingredient != null && ingredient.test(stack)));
        }

        List<Demand> demands = new ArrayList<>();
        long multiplier = Math.max(0L, operations);
        if (requirements != null) {
            for (CountedIngredient requirement : requirements) {
                if (requirement == null || requirement.count() <= 0) continue;
                long amount = saturatingMultiply(requirement.count(), multiplier);
                if (amount > 0) {
                    demands.add(new Demand(requirement.ingredient(), amount));
                }
            }
        }
        return solve(supplies, demands, safeInputs.size());
    }

    /** Matches adapter input maps without flattening overlapping ingredient requirements. */
    public static boolean matches(Map<Ingredient, Long> available, Map<Ingredient, Long> required) {
        List<Supply> supplies = new ArrayList<>();
        if (available != null) {
            int index = 0;
            for (Map.Entry<Ingredient, Long> entry : available.entrySet()) {
                Ingredient input = entry.getKey();
                long amount = entry.getValue() == null ? 0L : entry.getValue();
                if (input != null && amount > 0) {
                    supplies.add(new Supply(index, amount, requirement -> ingredientCanSupply(input, requirement)));
                }
                index++;
            }
        }

        List<Demand> demands = new ArrayList<>();
        if (required != null) {
            for (Map.Entry<Ingredient, Long> entry : required.entrySet()) {
                long amount = entry.getValue() == null ? 0L : entry.getValue();
                if (amount > 0) {
                    demands.add(new Demand(entry.getKey(), amount));
                }
            }
        }
        return solve(supplies, demands, available == null ? 0 : available.size()) != null;
    }

    /** Returns the largest whole-operation count supported by the supplied item stacks. */
    public static int maxOperations(List<CountedIngredient> requirements, List<ItemStack> inputs) {
        long demandPerOperation = 0L;
        if (requirements != null) {
            for (CountedIngredient requirement : requirements) {
                if (requirement != null && requirement.count() > 0) {
                    demandPerOperation = saturatingAdd(demandPerOperation, requirement.count());
                }
            }
        }
        if (demandPerOperation == 0) return Integer.MAX_VALUE;

        long totalAvailable = 0L;
        if (inputs != null) {
            for (ItemStack stack : inputs) {
                if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                    totalAvailable = saturatingAdd(totalAvailable, stack.getCount());
                }
            }
        }

        long upperLong = totalAvailable / demandPerOperation;
        int low = 0;
        int high = upperLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) upperLong;
        while (low < high) {
            int middle = low + (high - low + 1) / 2;
            if (matches(requirements, inputs, middle)) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static boolean ingredientCanSupply(Ingredient input, Ingredient requirement) {
        if (requirement == null) return false;
        if (input == requirement || input.equals(requirement)) return true;
        for (ItemStack representative : input.getItems()) {
            if (!representative.isEmpty() && requirement.test(representative)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Allocation solve(List<Supply> supplies, List<Demand> demands, int resultSize) {
        if (demands.isEmpty()) return new Allocation(new long[resultSize]);
        if (supplies.isEmpty()) return null;

        long totalDemand = 0L;
        for (Demand demand : demands) {
            totalDemand = saturatingAdd(totalDemand, demand.amount());
        }
        long totalSupply = 0L;
        for (Supply supply : supplies) {
            totalSupply = saturatingAdd(totalSupply, supply.amount());
        }
        if (totalSupply < totalDemand) return null;

        int source = 0;
        int supplyStart = 1;
        int demandStart = supplyStart + supplies.size();
        int sink = demandStart + demands.size();
        FlowNetwork network = new FlowNetwork(sink + 1);
        List<FlowEdge> supplyEdges = new ArrayList<>(supplies.size());

        for (int i = 0; i < supplies.size(); i++) {
            supplyEdges.add(network.addEdge(source, supplyStart + i, supplies.get(i).amount()));
        }
        for (int i = 0; i < supplies.size(); i++) {
            Supply supply = supplies.get(i);
            for (int j = 0; j < demands.size(); j++) {
                if (supply.matches().test(demands.get(j).ingredient())) {
                    network.addEdge(supplyStart + i, demandStart + j, totalDemand);
                }
            }
        }
        for (int i = 0; i < demands.size(); i++) {
            network.addEdge(demandStart + i, sink, demands.get(i).amount());
        }

        if (network.maxFlow(source, sink, totalDemand) != totalDemand) return null;

        long[] consumed = new long[resultSize];
        for (int i = 0; i < supplies.size(); i++) {
            Supply supply = supplies.get(i);
            consumed[supply.originalIndex()] = supply.amount() - supplyEdges.get(i).capacity;
        }
        return new Allocation(consumed);
    }

    private static long saturatingAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }

    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0 || multiplier <= 0) return 0;
        if (amount > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return amount * multiplier;
    }

    public static final class Allocation {
        private final long[] consumedByInput;

        private Allocation(long[] consumedByInput) {
            this.consumedByInput = consumedByInput;
        }

        public int inputCount() {
            return this.consumedByInput.length;
        }

        public long consumedFromInput(int inputIndex) {
            return this.consumedByInput[inputIndex];
        }
    }

    private record Supply(int originalIndex, long amount, Predicate<Ingredient> matches) {
    }

    private record Demand(Ingredient ingredient, long amount) {
    }

    private static final class FlowNetwork {
        private final List<List<FlowEdge>> graph;
        private final int[] levels;
        private final int[] nextEdges;

        private FlowNetwork(int nodeCount) {
            this.graph = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) {
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
            while (flow < limit && this.buildLevels(source, sink)) {
                Arrays.fill(this.nextEdges, 0);
                long pushed;
                while (flow < limit && (pushed = this.push(source, sink, limit - flow)) > 0) {
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
                    if (edge.capacity > 0 && this.levels[edge.to] < 0) {
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
                if (edge.capacity <= 0 || this.levels[edge.to] != this.levels[node] + 1) continue;
                long pushed = this.push(edge.to, sink, Math.min(amount, edge.capacity));
                if (pushed <= 0) continue;
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
}
