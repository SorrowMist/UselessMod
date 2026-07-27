package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    public static Result extract(IPatternDetails pattern, int operations, @Nullable Level level,
                                 MEStorage storage, IActionSource source, KeyCounter available,
                                 UnreturnedInputSink unreturnedInputSink) {
        return extractAll(List.of(pattern), operations, level, storage, source, available,
                unreturnedInputSink).getFirst();
    }

    /** Plans every pattern against one shared snapshot, then commits the whole round atomically. */
    public static List<Result> extractAll(List<? extends IPatternDetails> patterns, int operations,
                                          @Nullable Level level, MEStorage storage, IActionSource source,
                                          KeyCounter available,
                                          UnreturnedInputSink unreturnedInputSink) {
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(unreturnedInputSink, "unreturnedInputSink");

        KeyCounter remaining = new KeyCounter();
        remaining.addAll(available);
        KeyCounter total = new KeyCounter();
        List<PlannedExtraction> planned = new ArrayList<>(patterns.size());
        for (IPatternDetails pattern : patterns) {
            Objects.requireNonNull(pattern, "pattern");
            PlannedExtraction entry = plan(pattern, operations, level, remaining);
            planned.add(entry);
            if (entry.failure != Failure.NONE) {
                continue;
            }
            for (var consumed : entry.consumed) {
                remaining.remove(consumed.getKey(), consumed.getLongValue());
                total.add(consumed.getKey(), consumed.getLongValue());
            }
        }

        for (var entry : total) {
            long simulated = storage.extract(
                    entry.getKey(), entry.getLongValue(), Actionable.SIMULATE, source);
            if (simulated < entry.getLongValue()) {
                return storageChangedResults(planned, entry.getKey());
            }
        }

        KeyCounter extracted = new KeyCounter();
        for (var entry : total) {
            long amount = storage.extract(
                    entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
            if (amount > 0) {
                extracted.add(entry.getKey(), amount);
            }
            if (amount < entry.getLongValue()) {
                rollback(storage, source, extracted, unreturnedInputSink);
                return storageChangedResults(planned, entry.getKey());
            }
        }

        for (var entry : total) {
            available.remove(entry.getKey(), entry.getLongValue());
        }
        List<Result> results = new ArrayList<>(planned.size());
        for (PlannedExtraction entry : planned) {
            results.add(new Result(entry.failure, entry.inputs, entry.missingKey));
        }
        return List.copyOf(results);
    }

    private static List<Result> storageChangedResults(
            List<PlannedExtraction> planned, @Nullable AEKey changedKey) {
        List<Result> results = new ArrayList<>(planned.size());
        for (PlannedExtraction entry : planned) {
            results.add(entry.failure == Failure.NONE
                    ? new Result(Failure.STORAGE_CHANGED, new KeyCounter[0], changedKey)
                    : new Result(entry.failure, entry.inputs, entry.missingKey));
        }
        return List.copyOf(results);
    }

    static PlannedExtraction plan(IPatternDetails pattern, int operations,
                                  @Nullable Level level, KeyCounter available) {
        if (operations <= 0) {
            return PlannedExtraction.failure(Failure.AMOUNT_OVERFLOW, null);
        }

        KeyCounter remaining = new KeyCounter();
        remaining.addAll(available);
        KeyCounter consumed = new KeyCounter();
        IPatternDetails.IInput[] patternInputs = pattern.getInputs();
        KeyCounter[] selectedInputs = new KeyCounter[patternInputs.length];

        for (int slot = 0; slot < patternInputs.length; slot++) {
            IPatternDetails.IInput input = patternInputs[slot];
            selectedInputs[slot] = new KeyCounter();
            if (input == null) {
                continue;
            }

            long required;
            try {
                required = Math.multiplyExact(input.getMultiplier(), (long) operations);
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
                missing -= take(key, missing, remaining, selectedInputs[slot], consumed);
            }

            if (missing > 0) {
                List<AEKey> candidates = new ArrayList<>();
                for (var entry : remaining) {
                    if (entry.getLongValue() > 0 && input.isValid(entry.getKey(), level)) {
                        candidates.add(entry.getKey());
                    }
                }
                for (AEKey candidate : candidates) {
                    if (missing <= 0) {
                        break;
                    }
                    missing -= take(candidate, missing, remaining, selectedInputs[slot], consumed);
                }
            }

            if (missing > 0) {
                return PlannedExtraction.failure(Failure.MISSING_INPUT, firstPossibleKey(input));
            }
        }

        return new PlannedExtraction(Failure.NONE, selectedInputs, consumed, null);
    }

    private static long take(AEKey key, long wanted, KeyCounter remaining,
                             KeyCounter selected, KeyCounter consumed) {
        long amount = Math.min(wanted, remaining.get(key));
        if (amount <= 0) {
            return 0;
        }
        remaining.remove(key, amount);
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
}
