package com.sorrowmist.useless.content.recipe;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/** Converts independent random item outputs into the smallest deterministic batch. */
public final class ExpectedOutputScaler {
    private static final int MAX_PROBABILITY_DENOMINATOR = 1000;
    private static final double EXACT_FRACTION_EPSILON = 1.0E-9;

    private ExpectedOutputScaler() {
    }

    public static Optional<ScaledOutputs> scale(List<WeightedItemOutput> weightedOutputs) {
        if (weightedOutputs == null || weightedOutputs.isEmpty()) {
            return Optional.of(new ScaledOutputs(1, List.of()));
        }

        List<ExpectedItemOutput> expectedOutputs = new ArrayList<>();
        long operations = 1;

        for (WeightedItemOutput weighted : weightedOutputs) {
            if (weighted == null || weighted.stack() == null || weighted.stack().isEmpty()) {
                continue;
            }
            if (weighted.min() < 0 || weighted.max() < weighted.min() || !Double.isFinite(weighted.chance())) {
                return Optional.empty();
            }

            Fraction chance = approximateChance(weighted.chance());
            if (chance.numerator() == 0 || weighted.max() == 0) {
                continue;
            }

            final long numerator;
            final long denominator;
            try {
                numerator = Math.multiplyExact(chance.numerator(), Math.addExact((long) weighted.min(), weighted.max()));
                denominator = Math.multiplyExact(chance.denominator(), 2L);
            } catch (ArithmeticException overflow) {
                return Optional.empty();
            }

            long divisor = AdapterUtils.gcd(numerator, denominator);
            long reducedNumerator = numerator / divisor;
            long reducedDenominator = denominator / divisor;
            expectedOutputs.add(new ExpectedItemOutput(weighted.stack().copyWithCount(1), reducedNumerator, reducedDenominator));

            OptionalInt lcm = leastCommonMultiple(operations, reducedDenominator);
            if (lcm.isEmpty()) {
                return Optional.empty();
            }
            operations = lcm.getAsInt();
        }

        List<ItemStack> scaledOutputs = new ArrayList<>();
        for (ExpectedItemOutput expected : expectedOutputs) {
            long factor = operations / expected.denominator();
            final long count;
            try {
                count = Math.multiplyExact(expected.numerator(), factor);
            } catch (ArithmeticException overflow) {
                return Optional.empty();
            }
            if (count <= 0) {
                continue;
            }
            if (!mergeOutput(scaledOutputs, expected.stack(), count)) {
                return Optional.empty();
            }
        }

        return Optional.of(new ScaledOutputs((int) operations, scaledOutputs));
    }

    public static OptionalInt multiplyToInt(long value, int multiplier) {
        if (value < 0 || multiplier < 0) {
            return OptionalInt.empty();
        }
        try {
            long result = Math.multiplyExact(value, (long) multiplier);
            return result <= Integer.MAX_VALUE ? OptionalInt.of((int) result) : OptionalInt.empty();
        } catch (ArithmeticException overflow) {
            return OptionalInt.empty();
        }
    }

    private static Fraction approximateChance(double rawChance) {
        double chance = Math.max(0.0, Math.min(1.0, rawChance));
        if (chance == 0.0) {
            return new Fraction(0, 1);
        }
        if (chance == 1.0) {
            return new Fraction(1, 1);
        }

        long bestNumerator = 0;
        long bestDenominator = 1;
        double bestError = Double.MAX_VALUE;
        for (int denominator = 1; denominator <= MAX_PROBABILITY_DENOMINATOR; denominator++) {
            long numerator = Math.round(chance * denominator);
            double error = Math.abs(chance - (double) numerator / denominator);
            if (error < bestError) {
                bestError = error;
                bestNumerator = numerator;
                bestDenominator = denominator;
                if (error < EXACT_FRACTION_EPSILON) {
                    break;
                }
            }
        }

        long divisor = AdapterUtils.gcd(bestNumerator, bestDenominator);
        return new Fraction(bestNumerator / divisor, bestDenominator / divisor);
    }

    private static OptionalInt leastCommonMultiple(long left, long right) {
        if (left <= 0 || right <= 0) {
            return OptionalInt.empty();
        }
        long reducedRight = right / AdapterUtils.gcd(left, right);
        try {
            long result = Math.multiplyExact(left, reducedRight);
            return result <= Integer.MAX_VALUE ? OptionalInt.of((int) result) : OptionalInt.empty();
        } catch (ArithmeticException overflow) {
            return OptionalInt.empty();
        }
    }

    private static boolean mergeOutput(List<ItemStack> outputs, ItemStack stack, long amount) {
        for (ItemStack existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            long merged = (long) existing.getCount() + amount;
            if (merged > Integer.MAX_VALUE) {
                return false;
            }
            existing.setCount((int) merged);
            return true;
        }
        if (amount > Integer.MAX_VALUE) {
            return false;
        }
        ItemStack output = stack.copy();
        output.setCount((int) amount);
        outputs.add(output);
        return true;
    }

    public record WeightedItemOutput(ItemStack stack, int min, int max, double chance) {
    }

    public record ScaledOutputs(int operations, List<ItemStack> outputs) {
        public ScaledOutputs {
            outputs = outputs.stream().map(ItemStack::copy).toList();
        }
    }

    private record ExpectedItemOutput(ItemStack stack, long numerator, long denominator) {
    }

    private record Fraction(long numerator, long denominator) {
    }
}
