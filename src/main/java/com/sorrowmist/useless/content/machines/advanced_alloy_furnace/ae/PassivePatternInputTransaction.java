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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

        KeyCounter consumed = new KeyCounter();
        Map<AEKey, Long> simulatedAmounts = new HashMap<>();
        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        KeyCounter[] selectedInputs = new KeyCounter[patternInputs.length];
        DynamicComponentPattern dynamic = pattern instanceof DynamicComponentPattern value ? value : null;

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

            long missing = required;
            for (GenericStack possible : input.getPossibleInputs()) {
                if (possible == null || possible.what() == null || missing <= 0) {
                    continue;
                }
                AEKey key = possible.what();
                if (!input.isValid(key, level)) {
                    continue;
                }
                missing -= takeSimulated(
                        key, missing, storage, source, simulatedAmounts,
                        selectedInputs[slot], consumed);
            }

            if (missing > 0) {
                missing = takeAdditionalCandidates(
                        dynamic, slot, input, missing, level,
                        storage, source, simulatedAmounts,
                        selectedInputs[slot], consumed, inputIndex);
            }

            if (missing > 0) {
                return PlannedExtraction.failure(Failure.MISSING_INPUT, firstPossibleKey(input));
            }
        }

        return new PlannedExtraction(Failure.NONE, selectedInputs, consumed, null);
    }

    private static long takeAdditionalCandidates(
            @Nullable DynamicComponentPattern dynamic,
            int slot,
            IPatternDetails.IInput input,
            long missing,
            @Nullable Level level,
            MEStorage storage,
            IActionSource source,
            Map<AEKey, Long> simulatedAmounts,
            KeyCounter selected,
            KeyCounter consumed,
            AvailableInputIndex inputIndex) {
        if (dynamic == null || !dynamic.isItemIdInput(slot)) {
            return missing;
        }
        for (GenericStack possible : input.getPossibleInputs()) {
            if (possible == null || !(possible.what() instanceof AEItemKey possibleItem)) {
                continue;
            }
            Iterable<AEKey> candidates = dynamic.isTagInput(slot)
                    ? inputIndex.allItemVariants()
                    : inputIndex.itemVariants(possibleItem);
            for (AEKey candidate : candidates) {
                if (missing <= 0) {
                    return 0L;
                }
                if (input.isValid(candidate, level)) {
                    missing -= takeSimulated(
                            candidate, missing, storage, source, simulatedAmounts,
                        selected, consumed);
                }
            }
        }
        return missing;
    }

    private static long takeSimulated(
            AEKey key, long wanted, MEStorage storage, IActionSource source,
            Map<AEKey, Long> simulatedAmounts, KeyCounter selected, KeyCounter consumed) {
        long available = simulatedAmounts.computeIfAbsent(key, candidate -> Math.max(0L,
                storage.extract(candidate, Long.MAX_VALUE, Actionable.SIMULATE, source)));
        long amount = Math.min(wanted, Math.max(0L, available - consumed.get(key)));
        if (amount <= 0) {
            return 0;
        }
        selected.add(key, amount);
        consumed.add(key, amount);
        return amount;
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

    /** Lazily enumerates network keys only when a component-relaxed input needs variants. */
    private static final class AvailableInputIndex {
        private final Supplier<KeyCounter> cachedInventorySupplier;
        private final Map<Item, List<AEKey>> itemVariants = new IdentityHashMap<>();
        private List<AEKey> allItemVariants;
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
    }
}
