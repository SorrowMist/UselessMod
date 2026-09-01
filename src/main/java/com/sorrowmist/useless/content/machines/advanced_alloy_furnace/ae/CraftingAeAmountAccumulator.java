package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates exact AE amounts without materializing item or fluid stacks for numeric chunks.
 */
final class CraftingAeAmountAccumulator {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> amounts =
            new Object2ObjectLinkedOpenHashMap<>();

    static CraftingAeAmountAccumulator fromCounters(KeyCounter[] counters) {
        CraftingAeAmountAccumulator accumulator = new CraftingAeAmountAccumulator();
        accumulator.add(counters);
        return accumulator;
    }

    void add(KeyCounter[] counters) {
        if (counters == null) {
            return;
        }
        for (KeyCounter counter : counters) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                add(entry.getKey(), entry.getLongValue());
            }
        }
    }

    static CraftingAeAmountAccumulator fromGenericStacks(Iterable<GenericStack> stacks) {
        CraftingAeAmountAccumulator accumulator = new CraftingAeAmountAccumulator();
        for (GenericStack stack : stacks) {
            accumulator.add(stack);
        }
        return accumulator;
    }

    void add(GenericStack stack) {
        Objects.requireNonNull(stack, "Crafting AE amount");
        add(stack.what(), stack.amount());
    }

    void add(AEKey key, long amount) {
        add(key, BigInteger.valueOf(amount));
    }

    void add(AEKey key, BigInteger amount) {
        Objects.requireNonNull(key, "Crafting AE key");
        Objects.requireNonNull(amount, "Crafting AE amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Crafting AE amounts must be positive");
        }
        this.amounts.merge(key, amount, BigInteger::add);
    }

    boolean fitsWithinLongAfterMultiplying(long multiplier) {
        if (multiplier <= 0L) {
            throw new IllegalArgumentException("Crafting AE multiplier must be positive");
        }
        BigInteger factor = BigInteger.valueOf(multiplier);
        for (BigInteger amount : this.amounts.values()) {
            if (amount.multiply(factor).compareTo(MAX_LONG) > 0) {
                return false;
            }
        }
        return true;
    }

    List<GenericStack> segments() {
        ArrayList<GenericStack> result = new ArrayList<>(this.amounts.size());
        for (var entry : this.amounts.object2ObjectEntrySet()) {
            BigInteger remaining = entry.getValue();
            while (remaining.compareTo(MAX_LONG) > 0) {
                result.add(new GenericStack(entry.getKey(), Long.MAX_VALUE));
                remaining = remaining.subtract(MAX_LONG);
            }
            if (remaining.signum() > 0) {
                result.add(new GenericStack(entry.getKey(), remaining.longValueExact()));
            }
        }
        return result;
    }
}
