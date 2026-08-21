package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsTooltip;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Local AE pattern view for recipes whose output may carry runtime components.
 * Only the explicitly marked slots use item-id matching; all other slots retain
 * the source AE2 semantics.
 */
public class DynamicComponentPatternDetails extends AEProcessingPattern implements DynamicComponentPattern {
    private final AEItemKey definition;
    private final AEProcessingPattern source;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;
    private final boolean[] itemIdInputs;
    private final Map<Integer, List<TagKey<Item>>> tagInputTags;
    private final Map<Integer, List<TagKey<Fluid>>> fluidTagInputTags;
    private final boolean[] itemIdOutputs;
    private final boolean hasInputMatchers;
    private final String identity;

    @FunctionalInterface
    public interface InputMatcher {
        boolean test(AEKey input);
    }

    public DynamicComponentPatternDetails(
            AEProcessingPattern source,
            Iterable<Integer> itemIdInputSlots,
            Iterable<Integer> itemIdOutputSlots,
            HolderLookup.Provider registries) {
        this(source, itemIdInputSlots, itemIdOutputSlots, Map.of(), Map.of(), Map.of(), Map.of(), registries);
    }

    public DynamicComponentPatternDetails(
            AEProcessingPattern source,
            Iterable<Integer> itemIdInputSlots,
            Iterable<Integer> itemIdOutputSlots,
            Map<Integer, ? extends InputMatcher> inputMatchers,
            HolderLookup.Provider registries) {
        this(source, itemIdInputSlots, itemIdOutputSlots, Map.of(), Map.of(), inputMatchers, Map.of(), registries);
    }

    public DynamicComponentPatternDetails(
            AEProcessingPattern source,
            Iterable<Integer> itemIdInputSlots,
            Iterable<Integer> itemIdOutputSlots,
            Map<Integer, ? extends Iterable<TagKey<Item>>> tagInputTags,
            Map<Integer, ? extends InputMatcher> inputMatchers,
            HolderLookup.Provider registries) {
        this(source, itemIdInputSlots, itemIdOutputSlots, tagInputTags, Map.of(), inputMatchers, Map.of(), registries);
    }

    public DynamicComponentPatternDetails(
            AEProcessingPattern source,
            Iterable<Integer> itemIdInputSlots,
            Iterable<Integer> itemIdOutputSlots,
            Map<Integer, ? extends Iterable<TagKey<Item>>> tagInputTags,
            Map<Integer, ? extends Iterable<TagKey<Fluid>>> fluidTagInputTags,
            Map<Integer, ? extends InputMatcher> inputMatchers,
            Map<Integer, ? extends InputMatcher> fluidInputMatchers,
            HolderLookup.Provider registries) {
        super(source.getDefinition());
        this.source = Objects.requireNonNull(source, "source");
        this.definition = source.getDefinition();
        this.outputs = List.copyOf(source.getOutputs());

        IInput[] sourceInputs = source.getInputs();
        this.inputs = new IInput[sourceInputs.length];
        this.itemIdInputs = new boolean[sourceInputs.length];
        this.itemIdOutputs = new boolean[this.outputs.size()];
        this.tagInputTags = normalizeTagInputTags(tagInputTags, sourceInputs.length);
        this.fluidTagInputTags = normalizeFluidTagInputTags(fluidTagInputTags, sourceInputs.length);
        InputMatcher[] matchers = new InputMatcher[sourceInputs.length];
        InputMatcher[] fluidMatchers = new InputMatcher[sourceInputs.length];
        if (inputMatchers != null) {
            for (Map.Entry<Integer, ? extends InputMatcher> entry : inputMatchers.entrySet()) {
                Integer slot = entry.getKey();
                if (slot == null || slot < 0 || slot >= matchers.length) {
                    throw new IllegalArgumentException(
                            "Input matcher slot is outside the processing pattern: " + slot);
                }
                matchers[slot] = Objects.requireNonNull(entry.getValue(), "input matcher");
            }
        }
        if (fluidInputMatchers != null) {
            for (Map.Entry<Integer, ? extends InputMatcher> entry : fluidInputMatchers.entrySet()) {
                Integer slot = entry.getKey();
                if (slot == null || slot < 0 || slot >= fluidMatchers.length) {
                    throw new IllegalArgumentException(
                            "Fluid input matcher slot is outside the processing pattern: " + slot);
                }
                fluidMatchers[slot] = Objects.requireNonNull(entry.getValue(), "fluid input matcher");
            }
        }
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            this.inputs[slot] = sourceInputs[slot];
        }

        markSlots(this.itemIdInputs, itemIdInputSlots, "input");
        markSlots(this.itemIdOutputs, itemIdOutputSlots, "output");
        for (int slot : this.tagInputTags.keySet()) {
            this.itemIdInputs[slot] = true;
        }
        this.hasInputMatchers = Arrays.stream(matchers).anyMatch(Objects::nonNull)
                || Arrays.stream(fluidMatchers).anyMatch(Objects::nonNull)
                || !this.tagInputTags.isEmpty()
                || !this.fluidTagInputTags.isEmpty();
        for (int slot = 0; slot < this.inputs.length; slot++) {
            if (this.itemIdInputs[slot]) {
                this.inputs[slot] = new ItemIdInput(this.inputs[slot], this.tagInputTags.get(slot), matchers[slot]);
            } else if (this.fluidTagInputTags.containsKey(slot)) {
                this.inputs[slot] = new FluidTagInput(this.inputs[slot],
                        this.fluidTagInputTags.get(slot), fluidMatchers[slot]);
            }
        }

        String mode = Arrays.toString(this.itemIdInputs) + "/" + this.tagInputTags
                + "/" + this.fluidTagInputTags + "/" + Arrays.toString(this.itemIdOutputs);
        this.identity = "useless_mod:dynamic_component|modes=" + mode
                + "|definition_sha256=" + definitionFingerprint(definition, registries);
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
        return source.supportsPushInputsToExternalInventory();
    }

    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        if (inputHolder == null) {
            return;
        }

        List<GenericStack> sparseInputs = source.getSparseInputs();
        if (sparseInputs.size() == inputs.length && !hasInputMatchers) {
            for (KeyCounter counter : inputHolder) {
                if (counter == null) continue;
                for (var entry : counter) {
                    inputSink.pushInput(entry.getKey(), entry.getLongValue());
                }
            }
            return;
        }

        pushSparseInputs(sparseInputs, inputHolder, inputSink);
    }

    private void pushSparseInputs(List<GenericStack> sparseInputs,
                                  KeyCounter[] inputHolder,
                                  PatternInputSink inputSink) {
        List<PushDemand> demands = new ArrayList<>();
        IPatternDetails.IInput[] sourceInputs = source.getInputs();
        for (GenericStack sparseInput : sparseInputs) {
            if (sparseInput == null) {
                continue;
            }
            AEKey expectedKey = sparseInput.what();
            if (expectedKey == null || sparseInput.amount() <= 0L) {
                throw new IllegalStateException("Processing pattern contains an invalid sparse input");
            }

            int sourceSlot = sourceInputSlot(sourceInputs, expectedKey);
            IInput dynamicInput = sourceSlot >= 0 && sourceSlot < inputs.length
                    ? dynamicInput(inputs[sourceSlot]) : null;
            demands.add(new PushDemand(expectedKey, sparseInput.amount(), dynamicInput));
        }

        List<PushSupply> supplies = new ArrayList<>();
        KeyCounter availableInputs = new KeyCounter();
        for (KeyCounter counter : inputHolder) {
            if (counter != null) {
                availableInputs.addAll(counter);
            }
        }
        for (var entry : availableInputs) {
            if (entry.getKey() != null && entry.getLongValue() > 0L) {
                supplies.add(new PushSupply(entry.getKey(), entry.getLongValue()));
            }
        }

        PushAllocation allocation = allocatePushInputs(supplies, demands);
        if (allocation == null) {
            throw new IllegalStateException("Selected inputs cannot satisfy the dynamic processing pattern");
        }

        for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
            for (PushEdge allocationEdge : allocation.edges().get(demandIndex)) {
                long amount = allocationEdge.initialCapacity() - allocationEdge.edge().capacity;
                if (amount > 0L) {
                    inputSink.pushInput(allocationEdge.key(), amount);
                }
            }
        }
    }

    @Nullable
    private static IInput dynamicInput(IInput input) {
        return input instanceof ItemIdInput || input instanceof FluidTagInput ? input : null;
    }

    private static int sourceInputSlot(IPatternDetails.IInput[] sourceInputs, AEKey expectedKey) {
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            IPatternDetails.IInput input = sourceInputs[slot];
            if (input == null) continue;
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible != null && expectedKey.equals(possible.what())) {
                    return slot;
                }
            }
        }
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            IPatternDetails.IInput input = sourceInputs[slot];
            if (input != null && input.isValid(expectedKey, null)) {
                return slot;
            }
        }
        return -1;
    }

    @Nullable
    private static PushAllocation allocatePushInputs(List<PushSupply> supplies, List<PushDemand> demands) {
        if (demands.isEmpty()) {
            return new PushAllocation(List.of());
        }
        if (supplies.isEmpty()) {
            return null;
        }

        long totalDemand = 0L;
        for (PushDemand demand : demands) {
            totalDemand = saturatingAdd(totalDemand, demand.amount());
        }
        long totalSupply = 0L;
        for (PushSupply supply : supplies) {
            totalSupply = saturatingAdd(totalSupply, supply.amount());
        }
        if (totalSupply < totalDemand) {
            return null;
        }

        int sourceNode = 0;
        int supplyStart = 1;
        int demandStart = supplyStart + supplies.size();
        int sinkNode = demandStart + demands.size();
        PushFlowNetwork network = new PushFlowNetwork(sinkNode + 1);
        List<List<PushEdge>> allocationEdges = new ArrayList<>(demands.size());
        for (int supplyIndex = 0; supplyIndex < supplies.size(); supplyIndex++) {
            network.addEdge(sourceNode, supplyStart + supplyIndex, supplies.get(supplyIndex).amount());
        }
        for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
            PushDemand demand = demands.get(demandIndex);
            List<PushEdge> edges = new ArrayList<>();
            for (int supplyIndex = 0; supplyIndex < supplies.size(); supplyIndex++) {
                PushSupply supply = supplies.get(supplyIndex);
                if (demand.accepts(supply.key())) {
                    PushFlowEdge edge = network.addEdge(
                            supplyStart + supplyIndex, demandStart + demandIndex, totalDemand);
                    edges.add(new PushEdge(supply.key(), edge, totalDemand));
                }
            }
            allocationEdges.add(edges);
            network.addEdge(demandStart + demandIndex, sinkNode, demand.amount());
        }

        if (network.maxFlow(sourceNode, sinkNode, totalDemand) != totalDemand) {
            return null;
        }
        return new PushAllocation(allocationEdges);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private record PushSupply(AEKey key, long amount) {
    }

    private record PushDemand(AEKey expectedKey, long amount, @Nullable IInput dynamicInput) {
        private boolean accepts(AEKey key) {
            return dynamicInput != null ? dynamicInput.isValid(key, null) : expectedKey.equals(key);
        }
    }

    private record PushEdge(AEKey key, PushFlowEdge edge, long initialCapacity) {
    }

    private record PushAllocation(List<List<PushEdge>> edges) {
    }

    private static final class PushFlowNetwork {
        private final List<List<PushFlowEdge>> graph;
        private final int[] levels;
        private final int[] nextEdges;

        private PushFlowNetwork(int nodeCount) {
            graph = new ArrayList<>(nodeCount);
            for (int i = 0; i < nodeCount; i++) graph.add(new ArrayList<>());
            levels = new int[nodeCount];
            nextEdges = new int[nodeCount];
        }

        private PushFlowEdge addEdge(int from, int to, long capacity) {
            PushFlowEdge forward = new PushFlowEdge(to, graph.get(to).size(), capacity);
            PushFlowEdge reverse = new PushFlowEdge(from, graph.get(from).size(), 0L);
            graph.get(from).add(forward);
            graph.get(to).add(reverse);
            return forward;
        }

        private long maxFlow(int source, int sink, long limit) {
            long flow = 0L;
            while (flow < limit && buildLevels(source, sink)) {
                Arrays.fill(nextEdges, 0);
                long pushed;
                while (flow < limit && (pushed = push(source, sink, limit - flow)) > 0L) {
                    flow += pushed;
                }
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
                for (PushFlowEdge edge : graph.get(node)) {
                    if (edge.capacity > 0L && levels[edge.to] < 0) {
                        levels[edge.to] = levels[node] + 1;
                        queue.addLast(edge.to);
                    }
                }
            }
            return levels[sink] >= 0;
        }

        private long push(int node, int sink, long amount) {
            if (node == sink) return amount;
            List<PushFlowEdge> edges = graph.get(node);
            for (; nextEdges[node] < edges.size(); nextEdges[node]++) {
                PushFlowEdge edge = edges.get(nextEdges[node]);
                if (edge.capacity <= 0L || levels[edge.to] != levels[node] + 1) continue;
                long pushed = push(edge.to, sink, Math.min(amount, edge.capacity));
                if (pushed <= 0L) continue;
                edge.capacity -= pushed;
                graph.get(edge.to).get(edge.reverseIndex).capacity += pushed;
                return pushed;
            }
            return 0L;
        }
    }

    private static final class PushFlowEdge {
        private final int to;
        private final int reverseIndex;
        private long capacity;

        private PushFlowEdge(int to, int reverseIndex, long capacity) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.capacity = capacity;
        }
    }

    @Override
    public PatternDetailsTooltip getTooltip(Level level, TooltipFlag flags) {
        return source.getTooltip(level, flags);
    }

    @Override
    public String dynamicPatternIdentity() {
        return identity;
    }

    @Override
    public boolean isItemIdInput(int slot) {
        return slot >= 0 && slot < itemIdInputs.length && itemIdInputs[slot];
    }

    @Override
    public boolean isTagInput(int slot) {
        return tagInputTags.containsKey(slot);
    }

    @Override
    public boolean isFluidTagInput(int slot) {
        return fluidTagInputTags.containsKey(slot);
    }

    @Override
    public boolean isItemIdOutput(int slot) {
        return slot >= 0 && slot < itemIdOutputs.length && itemIdOutputs[slot];
    }

    @Override
    public boolean usesDynamicOutputs() {
        for (boolean itemIdOutput : itemIdOutputs) {
            if (itemIdOutput) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj
                || obj instanceof DynamicComponentPatternDetails other
                && definition.equals(other.definition)
                && Arrays.equals(itemIdInputs, other.itemIdInputs)
                && tagInputTags.equals(other.tagInputTags)
                && fluidTagInputTags.equals(other.fluidTagInputTags)
                && Arrays.equals(itemIdOutputs, other.itemIdOutputs);
    }

    @Override
    public int hashCode() {
        int result = definition.hashCode();
        result = 31 * result + Arrays.hashCode(itemIdInputs);
        result = 31 * result + tagInputTags.hashCode();
        result = 31 * result + fluidTagInputTags.hashCode();
        return 31 * result + Arrays.hashCode(itemIdOutputs);
    }

    static String definitionFingerprint(AEItemKey definition, HolderLookup.Provider registries) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(registries, "registries");
        JsonElement encoded = AEItemKey.CODEC.encodeStart(
                registries.createSerializationContext(JsonOps.INSTANCE), definition).getOrThrow();
        byte[] canonical = canonicalize(encoded).toString().getBytes(StandardCharsets.UTF_8);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void markSlots(boolean[] target, Iterable<Integer> slots, String side) {
        if (slots == null) {
            return;
        }
        for (Integer slot : slots) {
            if (slot == null || slot < 0 || slot >= target.length) {
                throw new IllegalArgumentException("Dynamic " + side + " slot is outside the pattern: " + slot);
            }
            target[slot] = true;
        }
    }

    private static Map<Integer, List<TagKey<Item>>> normalizeTagInputTags(
            Map<Integer, ? extends Iterable<TagKey<Item>>> source, int inputCount) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<Integer, List<TagKey<Item>>> result = new java.util.TreeMap<>();
        for (Map.Entry<Integer, ? extends Iterable<TagKey<Item>>> entry : source.entrySet()) {
            Integer slot = entry.getKey();
            if (slot == null || slot < 0 || slot >= inputCount) {
                throw new IllegalArgumentException("Tag input slot is outside the processing pattern: " + slot);
            }
            java.util.LinkedHashSet<TagKey<Item>> tags = new java.util.LinkedHashSet<>();
            Iterable<TagKey<Item>> values = entry.getValue();
            if (values != null) {
                for (TagKey<Item> tag : values) {
                    if (tag != null) tags.add(tag);
                }
            }
            if (!tags.isEmpty()) result.put(slot, List.copyOf(tags));
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, List<TagKey<Fluid>>> normalizeFluidTagInputTags(
            Map<Integer, ? extends Iterable<TagKey<Fluid>>> source, int inputCount) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<Integer, List<TagKey<Fluid>>> result = new java.util.TreeMap<>();
        for (Map.Entry<Integer, ? extends Iterable<TagKey<Fluid>>> entry : source.entrySet()) {
            Integer slot = entry.getKey();
            if (slot == null || slot < 0 || slot >= inputCount) {
                throw new IllegalArgumentException("Fluid tag input slot is outside the processing pattern: " + slot);
            }
            java.util.LinkedHashSet<TagKey<Fluid>> tags = new java.util.LinkedHashSet<>();
            Iterable<TagKey<Fluid>> values = entry.getValue();
            if (values != null) {
                for (TagKey<Fluid> tag : values) {
                    if (tag != null) tags.add(tag);
                }
            }
            if (!tags.isEmpty()) result.put(slot, List.copyOf(tags));
        }
        return Map.copyOf(result);
    }

    private static JsonElement canonicalize(JsonElement element) {
        if (element.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var entry : element.getAsJsonObject().entrySet()) {
                sorted.put(entry.getKey(), entry.getValue());
            }
            JsonObject result = new JsonObject();
            for (var entry : sorted.entrySet()) {
                result.add(entry.getKey(), canonicalize(entry.getValue()));
            }
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                result.add(canonicalize(child));
            }
            return result;
        }
        return element.deepCopy();
    }

    private static final class ItemIdInput implements IInput {
        private final IInput source;
        private final GenericStack[] possibleInputs;
        private final List<TagKey<Item>> tags;
        @Nullable
        private final InputMatcher matcher;

        private ItemIdInput(IInput source, List<TagKey<Item>> tags, @Nullable InputMatcher matcher) {
            this.source = Objects.requireNonNull(source, "source");
            this.possibleInputs = source.getPossibleInputs().clone();
            this.tags = tags == null ? List.of() : tags;
            this.matcher = matcher;
        }

        private boolean isTagInput() {
            return !tags.isEmpty();
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs.clone();
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
            if (!tags.isEmpty() && tags.stream().noneMatch(tag -> isTagMember(tag, itemKey.getItem()))) {
                return false;
            }
            if (!tags.isEmpty()) {
                return matcher == null || matcher.test(input);
            }
            if (matcher != null) {
                return matcher.test(input);
            }
            for (GenericStack possible : possibleInputs) {
                if (possible != null && possible.what() instanceof AEItemKey possibleItem
                        && possibleItem.getItem() == itemKey.getItem()) {
                    return true;
                }
            }
            return false;
        }

        private boolean isValidWithoutLevel(AEKey input) {
            return isValid(input, null);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            if (!isValidWithoutLevel(template)) {
                return null;
            }
            AEKey direct = source.getRemainingKey(template);
            if (direct != null) {
                return direct;
            }
            if (template instanceof AEItemKey itemKey) {
                for (GenericStack possible : possibleInputs) {
                    if (possible != null && possible.what() instanceof AEItemKey possibleItem
                            && possibleItem.getItem() == itemKey.getItem()) {
                        AEKey remaining = source.getRemainingKey(possible.what());
                        if (remaining != null) {
                            return remaining;
                        }
                    }
                }
            }
            return null;
        }

        private static boolean isTagMember(TagKey<Item> tag, Item item) {
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                if (holder.value() == item) return true;
            }
            return false;
        }
    }

    private static final class FluidTagInput implements IInput {
        private final IInput source;
        private final GenericStack[] possibleInputs;
        private final List<TagKey<Fluid>> tags;
        @Nullable
        private final InputMatcher matcher;

        private FluidTagInput(IInput source, List<TagKey<Fluid>> tags, @Nullable InputMatcher matcher) {
            this.source = Objects.requireNonNull(source, "source");
            this.possibleInputs = source.getPossibleInputs().clone();
            this.tags = tags == null ? List.of() : tags;
            this.matcher = matcher;
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs.clone();
        }

        @Override
        public long getMultiplier() {
            return source.getMultiplier();
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            if (!(input instanceof appeng.api.stacks.AEFluidKey fluidKey)) return false;
            FluidStack stack = fluidKey.toStack(1);
            if (tags.stream().noneMatch(stack::is)) return false;
            return matcher == null || matcher.test(input);
        }

        private boolean isValidWithoutLevel(AEKey input) {
            return isValid(input, null);
        }

        @Override
        @Nullable
        public AEKey getRemainingKey(AEKey template) {
            if (!isValidWithoutLevel(template)) return null;
            AEKey direct = source.getRemainingKey(template);
            if (direct != null) return direct;
            AEKey uniform = null;
            boolean found = false;
            for (GenericStack possible : possibleInputs) {
                if (possible == null || !(possible.what() instanceof appeng.api.stacks.AEFluidKey)) {
                    continue;
                }
                AEKey remaining = source.getRemainingKey(possible.what());
                if (remaining == null) {
                    // A null remainder is also a meaningful candidate result. It cannot be
                    // combined with a non-null remainder for a dynamic fluid slot.
                    return null;
                }
                if (!found) {
                    uniform = remaining;
                    found = true;
                } else if (!uniform.equals(remaining)) {
                    return null;
                }
            }
            return found ? uniform : null;
        }
    }
}
