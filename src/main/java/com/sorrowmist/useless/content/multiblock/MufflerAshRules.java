package com.sorrowmist.useless.content.multiblock;

/** Pure ash pacing/probability rules, shared with regression tests. */
public final class MufflerAshRules {
    public static final int ASH_INTERVAL_TICKS = 3600;

    private MufflerAshRules() {}

    public static boolean isProductionSuppressed(int suppressionPercent, int rollOutOf100) {
        return Math.max(0, Math.min(100, suppressionPercent)) > rollOutOf100;
    }

    public static int toxicChancePermille(int maxChance, int count, int slotLimit) {
        if (slotLimit <= 0 || count <= 0) return 0;
        return (int) ((long) Math.max(0, Math.min(1000, maxChance))
                * Math.min(count, slotLimit) / slotLimit);
    }
}
