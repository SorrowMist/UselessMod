package com.sorrowmist.useless.compat.forge1201;

/** Integer-preserving range clamps for quantities larger than a float can represent. */
public final class LongMath {
    private LongMath() {}
    public static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
