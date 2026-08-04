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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Predicate;

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
        this(source, itemIdInputSlots, itemIdOutputSlots, Map.of(), registries);
    }

    public DynamicComponentPatternDetails(
            AEProcessingPattern source,
            Iterable<Integer> itemIdInputSlots,
            Iterable<Integer> itemIdOutputSlots,
            Map<Integer, ? extends InputMatcher> inputMatchers,
            HolderLookup.Provider registries) {
        super(source.getDefinition());
        this.source = Objects.requireNonNull(source, "source");
        this.definition = source.getDefinition();
        this.outputs = List.copyOf(source.getOutputs());

        IInput[] sourceInputs = source.getInputs();
        this.inputs = new IInput[sourceInputs.length];
        this.itemIdInputs = new boolean[sourceInputs.length];
        this.itemIdOutputs = new boolean[this.outputs.size()];
        InputMatcher[] matchers = new InputMatcher[sourceInputs.length];
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
        for (int slot = 0; slot < sourceInputs.length; slot++) {
            this.inputs[slot] = sourceInputs[slot];
        }

        markSlots(this.itemIdInputs, itemIdInputSlots, "input");
        markSlots(this.itemIdOutputs, itemIdOutputSlots, "output");
        this.hasInputMatchers = Arrays.stream(matchers).anyMatch(Objects::nonNull);
        for (int slot = 0; slot < this.inputs.length; slot++) {
            if (this.itemIdInputs[slot]) {
                this.inputs[slot] = new ItemIdInput(this.inputs[slot], matchers[slot]);
            }
        }

        String mode = Arrays.toString(this.itemIdInputs) + "/" + Arrays.toString(this.itemIdOutputs);
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

        KeyCounter availableInputs = new KeyCounter();
        for (KeyCounter counter : inputHolder) {
            if (counter != null) {
                availableInputs.addAll(counter);
            }
        }

        for (GenericStack sparseInput : sparseInputs) {
            if (sparseInput == null) {
                continue;
            }
            ItemIdInput itemIdInput = itemIdInputFor(sparseInput.what());
            if (itemIdInput != null && sparseInput.what() instanceof AEItemKey expectedItem) {
                pushItemIdInput(expectedItem, itemIdInput, sparseInput.amount(),
                        availableInputs, inputSink);
            } else {
                pushStrictInput(sparseInput.what(), sparseInput.amount(), availableInputs, inputSink);
            }
        }
    }

    @Nullable
    private ItemIdInput itemIdInputFor(AEKey expectedKey) {
        for (int slot = 0; slot < inputs.length; slot++) {
            if (!itemIdInputs[slot]) {
                continue;
            }
            IInput input = inputs[slot];
            if (!(input instanceof ItemIdInput itemIdInput)) {
                continue;
            }
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible != null && possible.what().equals(expectedKey)) {
                    return itemIdInput;
                }
            }
        }
        return null;
    }

    private static void pushStrictInput(
            AEKey expectedKey,
            long requiredAmount,
            KeyCounter availableInputs,
            PatternInputSink inputSink) {
        long available = availableInputs.get(expectedKey);
        if (available < requiredAmount) {
            throw new IllegalStateException("Expected at least %d of %s, but only %d was selected"
                    .formatted(requiredAmount, expectedKey, available));
        }
        inputSink.pushInput(expectedKey, requiredAmount);
        availableInputs.remove(expectedKey, requiredAmount);
    }

    private static void pushItemIdInput(
            AEItemKey expectedItem, ItemIdInput input,
            long requiredAmount,
            KeyCounter availableInputs,
            PatternInputSink inputSink) {
        long remaining = requiredAmount;
        remaining -= pushSelectedInput(expectedItem, remaining, availableInputs, inputSink,
                input::isValidWithoutLevel);
        if (remaining > 0) {
            List<AEKey> selectedKeys = new ArrayList<>();
            for (var entry : availableInputs) {
                if (entry.getLongValue() > 0) {
                    selectedKeys.add(entry.getKey());
                }
            }
            for (AEKey selectedKey : selectedKeys) {
                if (remaining <= 0) {
                    break;
                }
                if (selectedKey.equals(expectedItem)
                        || !(selectedKey instanceof AEItemKey selectedItem)
                        || selectedItem.getItem() != expectedItem.getItem()
                        || !input.isValidWithoutLevel(selectedKey)) {
                    continue;
                }
                remaining -= pushSelectedInput(selectedKey, remaining, availableInputs, inputSink,
                        input::isValidWithoutLevel);
            }
        }
        if (remaining > 0) {
            throw new IllegalStateException("Expected at least %d of %s by item id, but only %d was selected"
                    .formatted(requiredAmount, expectedItem, requiredAmount - remaining));
        }
    }

    private static long pushSelectedInput(
            AEKey key,
            long requiredAmount,
            KeyCounter availableInputs,
            PatternInputSink inputSink,
            Predicate<AEKey> allowed) {
        if (!allowed.test(key)) {
            return 0;
        }
        long selected = Math.min(requiredAmount, availableInputs.get(key));
        if (selected <= 0) {
            return 0;
        }
        inputSink.pushInput(key, selected);
        availableInputs.remove(key, selected);
        return selected;
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
                && Arrays.equals(itemIdOutputs, other.itemIdOutputs);
    }

    @Override
    public int hashCode() {
        int result = definition.hashCode();
        result = 31 * result + Arrays.hashCode(itemIdInputs);
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
        @Nullable
        private final InputMatcher matcher;

        private ItemIdInput(IInput source, @Nullable InputMatcher matcher) {
            this.source = Objects.requireNonNull(source, "source");
            this.possibleInputs = source.getPossibleInputs().clone();
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
            if (!(input instanceof AEItemKey itemKey)) {
                return false;
            }
            for (GenericStack possible : possibleInputs) {
                if (possible != null && possible.what() instanceof AEItemKey possibleItem
                        && possibleItem.getItem() == itemKey.getItem()) {
                    return matcher == null || matcher.test(input);
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
    }
}
