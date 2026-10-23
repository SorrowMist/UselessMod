package com.sorrowmist.useless.compat.thermal;

/** Parallel values represent extra operations beyond the machine's native operation. */
public final class ThermalParallelMath {
    private ThermalParallelMath() {
    }

    public static int extraOperations(float augment, float baseModifier, boolean applyBaseModifier) {
        double extra = applyBaseModifier ? (Math.max(0.0D, augment) + 1.0D) * baseModifier - 1.0D : augment;
        return (int) Math.max(0.0D, Math.min(Integer.MAX_VALUE - 1.0D, extra));
    }

    public static int scale(int value, int extraOperations) {
        if (value <= 0) return 0;
        long scaled = (long) value * (1L + Math.max(0, extraOperations));
        return (int) Math.min(Integer.MAX_VALUE, scaled);
    }
}
