package com.sorrowmist.useless.content.recipe;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Allocates fluid supplies to sized fluid ingredients without reusing a tank amount.
 * A max-flow network is used because tag/compound ingredients can overlap.
 */
public final class FluidIngredientAllocator {
    private FluidIngredientAllocator() {
    }

    public static boolean matches(List<SizedFluidIngredient> requirements,
                                  List<FluidStack> supplies, long operations) {
        return allocate(requirements, supplies, operations) != null;
    }

    /** Matches ordinary fluid stacks plus AE fluid supplies whose amount may exceed an int. */
    public static boolean matches(List<SizedFluidIngredient> requirements,
                                  List<FluidStack> supplies, List<GenericStack> keySupplies,
                                  long operations) {
        return allocate(requirements, supplies, keySupplies, operations) != null;
    }

    /** Matches already-aggregated fluid supplies without narrowing their long amounts to int. */
    public static boolean matches(List<SizedFluidIngredient> requirements,
                                  Map<FluidStack, Long> supplies, long operations) {
        return allocate(requirements, supplies, operations) != null;
    }

    public static boolean matchesTanks(List<SizedFluidIngredient> requirements,
                                       FluidTank[] tanks, int tankCount, long operations) {
        return allocateTanks(requirements, tanks, tankCount, operations) != null;
    }

    @Nullable
    public static Allocation allocate(List<SizedFluidIngredient> requirements,
                                      List<FluidStack> supplies, long operations) {
        List<Supply> normalizedSupplies = new ArrayList<>();
        if (supplies != null) {
            for (int i = 0; i < supplies.size(); i++) {
                FluidStack stack = supplies.get(i);
                if (stack != null && !stack.isEmpty() && stack.getAmount() > 0) {
                    normalizedSupplies.add(new Supply(i, stack.getAmount(), stack.copy()));
                }
            }
        }
        return solve(normalizedSupplies, demands(requirements, operations), supplies == null ? 0 : supplies.size());
    }

    @Nullable
    public static Allocation allocate(List<SizedFluidIngredient> requirements,
                                      List<FluidStack> supplies, List<GenericStack> keySupplies,
                                      long operations) {
        List<Supply> normalizedSupplies = new ArrayList<>();
        int resultSize = supplies == null ? 0 : supplies.size();
        if (supplies != null) {
            for (int i = 0; i < supplies.size(); i++) {
                FluidStack stack = supplies.get(i);
                if (stack != null && !stack.isEmpty() && stack.getAmount() > 0) {
                    normalizedSupplies.add(new Supply(i, stack.getAmount(), stack.copy()));
                }
            }
        }
        if (keySupplies != null) {
            for (int i = 0; i < keySupplies.size(); i++) {
                GenericStack generic = keySupplies.get(i);
                if (generic == null || generic.amount() <= 0 || !(generic.what() instanceof AEFluidKey fluid)) {
                    continue;
                }
                normalizedSupplies.add(new Supply(resultSize + i, generic.amount(), fluid.toStack(1)));
            }
            resultSize += keySupplies.size();
        }
        return solve(normalizedSupplies, demands(requirements, operations), resultSize);
    }

    @Nullable
    public static Allocation allocate(List<SizedFluidIngredient> requirements,
                                      Map<FluidStack, Long> supplies, long operations) {
        List<Supply> normalizedSupplies = new ArrayList<>();
        int resultSize = supplies == null ? 0 : supplies.size();
        if (supplies != null) {
            int index = 0;
            for (Map.Entry<FluidStack, Long> entry : supplies.entrySet()) {
                FluidStack stack = entry.getKey();
                long amount = entry.getValue() == null ? 0L : entry.getValue();
                if (stack != null && !stack.isEmpty() && amount > 0L) {
                    // FluidIngredient predicates inspect identity/components, not the amount.
                    // Keeping a one-unit representative lets the allocator carry the complete
                    // aggregated long amount without creating billions of FluidStack fragments.
                    normalizedSupplies.add(new Supply(index, amount, stack.copyWithAmount(1)));
                }
                index++;
            }
        }
        return solve(normalizedSupplies, demands(requirements, operations), resultSize);
    }

    @Nullable
    public static Allocation allocateTanks(List<SizedFluidIngredient> requirements,
                                           FluidTank[] tanks, int tankCount, long operations) {
        int count = tanks == null ? 0 : Math.max(0, Math.min(tankCount, tanks.length));
        List<Supply> supplies = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FluidStack stack = tanks[i] == null ? FluidStack.EMPTY : tanks[i].getFluid();
            if (!stack.isEmpty() && stack.getAmount() > 0) {
                supplies.add(new Supply(i, stack.getAmount(), stack.copy()));
            }
        }
        return solve(supplies, demands(requirements, operations), count);
    }

    /** Returns the largest whole operation count that all requirements can support. */
    public static int maxOperations(List<SizedFluidIngredient> requirements,
                                    List<FluidStack> supplies) {
        if (requirements == null || requirements.isEmpty()) return Integer.MAX_VALUE;
        long totalAvailable = 0L;
        if (supplies != null) {
            for (FluidStack stack : supplies) {
                if (stack != null && !stack.isEmpty()) totalAvailable = saturatingAdd(totalAvailable, stack.getAmount());
            }
        }
        long perOperation = 0L;
        for (SizedFluidIngredient requirement : requirements) {
            if (requirement != null && requirement.amount() > 0) {
                perOperation = saturatingAdd(perOperation, requirement.amount());
            }
        }
        if (perOperation <= 0L) return Integer.MAX_VALUE;
        int low = 0;
        int high = totalAvailable > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (totalAvailable / perOperation);
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1L) / 2L);
            if (matches(requirements, supplies, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    public static int maxTankOperations(List<SizedFluidIngredient> requirements,
                                        FluidTank[] tanks, int tankCount) {
        if (requirements == null || requirements.isEmpty()) return Integer.MAX_VALUE;
        int count = tanks == null ? 0 : Math.max(0, Math.min(tankCount, tanks.length));
        long totalAvailable = 0L;
        for (int i = 0; i < count; i++) {
            if (tanks[i] != null) totalAvailable = saturatingAdd(totalAvailable, tanks[i].getFluidAmount());
        }
        long perOperation = 0L;
        for (SizedFluidIngredient requirement : requirements) {
            if (requirement != null && requirement.amount() > 0) {
                perOperation = saturatingAdd(perOperation, requirement.amount());
            }
        }
        if (perOperation <= 0L) return Integer.MAX_VALUE;
        int low = 0;
        int high = totalAvailable > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (totalAvailable / perOperation);
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1L) / 2L);
            if (matchesTanks(requirements, tanks, tankCount, middle)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private static List<Demand> demands(List<SizedFluidIngredient> requirements, long operations) {
        List<Demand> result = new ArrayList<>();
        if (requirements == null || operations <= 0L) return result;
        for (SizedFluidIngredient requirement : requirements) {
            if (requirement == null || requirement.amount() <= 0) continue;
            if (requirement.ingredient() == null || requirement.ingredient().isEmpty()) {
                return null;
            }
            result.add(new Demand(requirement.ingredient(), saturatingMultiply(requirement.amount(), operations)));
        }
        return result;
    }

    @Nullable
    private static Allocation solve(List<Supply> supplies, List<Demand> demands, int resultSize) {
        if (demands == null) return null;
        if (demands.isEmpty()) return new Allocation(new long[resultSize]);
        if (supplies.isEmpty()) return null;
        long totalDemand = 0L;
        for (Demand demand : demands) totalDemand = saturatingAdd(totalDemand, demand.amount());
        long totalSupply = 0L;
        for (Supply supply : supplies) totalSupply = saturatingAdd(totalSupply, supply.amount());
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
                net.neoforged.neoforge.fluids.crafting.FluidIngredient ingredient = demands.get(j).ingredient();
                if (ingredient != null && !ingredient.isEmpty() && ingredient.test(supply.stack())) {
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

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) return 0;
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    public static final class Allocation {
        private final long[] consumedBySupply;

        private Allocation(long[] consumedBySupply) {
            this.consumedBySupply = consumedBySupply;
        }

        public int supplyCount() {
            return consumedBySupply.length;
        }

        public long consumedFromSupply(int index) {
            return consumedBySupply[index];
        }
    }

    private record Supply(int originalIndex, long amount, FluidStack stack) {
    }

    private record Demand(net.neoforged.neoforge.fluids.crafting.FluidIngredient ingredient, long amount) {
    }

    private static final class FlowNetwork {
        private final List<List<FlowEdge>> graph;
        private final int[] levels;
        private final int[] nextEdges;

        private FlowNetwork(int nodeCount) {
            graph = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) graph.add(new ArrayList<>());
            levels = new int[nodeCount];
            nextEdges = new int[nodeCount];
        }

        private FlowEdge addEdge(int from, int to, long capacity) {
            FlowEdge forward = new FlowEdge(to, graph.get(to).size(), capacity);
            FlowEdge reverse = new FlowEdge(from, graph.get(from).size(), 0L);
            graph.get(from).add(forward);
            graph.get(to).add(reverse);
            return forward;
        }

        private long maxFlow(int source, int sink, long limit) {
            long flow = 0L;
            while (flow < limit && buildLevels(source, sink)) {
                Arrays.fill(nextEdges, 0);
                long pushed;
                while (flow < limit && (pushed = push(source, sink, limit - flow)) > 0) flow += pushed;
            }
            return flow;
        }

        private boolean buildLevels(int source, int sink) {
            Arrays.fill(levels, -1);
            levels[source] = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (FlowEdge edge : graph.get(node)) {
                    if (edge.capacity > 0 && levels[edge.to] < 0) {
                        levels[edge.to] = levels[node] + 1;
                        queue.addLast(edge.to);
                    }
                }
            }
            return levels[sink] >= 0;
        }

        private long push(int node, int sink, long amount) {
            if (node == sink) return amount;
            List<FlowEdge> edges = graph.get(node);
            for (; nextEdges[node] < edges.size(); nextEdges[node]++) {
                FlowEdge edge = edges.get(nextEdges[node]);
                if (edge.capacity <= 0 || levels[edge.to] != levels[node] + 1) continue;
                long pushed = push(edge.to, sink, Math.min(amount, edge.capacity));
                if (pushed <= 0) continue;
                edge.capacity -= pushed;
                graph.get(edge.to).get(edge.reverseIndex).capacity += pushed;
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
