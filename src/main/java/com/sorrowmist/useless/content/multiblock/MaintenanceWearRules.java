package com.sorrowmist.useless.content.multiblock;

/** Remaining health, owned by the controller. Only actual production causes wear. */
public final class MaintenanceWearRules {
    public static final long MAX_MAINTENANCE = 1000L;
    public static final long WARNING_THRESHOLD = 600L;
    public static final long WEAR_TICKS_PER_POINT = 288L;
    public static final long CRAFTS_PER_BATCH_WEAR = 64L;
    public static final long MAX_WEAR_PER_BATCH = 20L;
    public static final int MIN_WARNING_EFFICIENCY_PERMILLE = 500;
    public static final int PERMILLE_ONE = 1000;

    private MaintenanceWearRules() {}

    public static boolean isOverdue(long maintenance) { return maintenance <= 0L; }
    public static boolean isWarning(long maintenance) { return maintenance < WARNING_THRESHOLD; }
    public static long clamp(long maintenance) { return Math.max(0L, Math.min(MAX_MAINTENANCE, maintenance)); }

    public static int efficiencyPermille(long maintenance) {
        if (isOverdue(maintenance)) return 0;
        if (!isWarning(maintenance)) return PERMILLE_ONE;
        long missing = WARNING_THRESHOLD - maintenance;
        return PERMILLE_ONE - (int) ((PERMILLE_ONE - MIN_WARNING_EFFICIENCY_PERMILLE)
                * missing / WARNING_THRESHOLD);
    }

    /** Ceiling division keeps the warning segment smooth and cannot overflow int. */
    public static int scaledProcessTime(int baseTicks, long maintenance) {
        int efficiency = efficiencyPermille(maintenance);
        if (efficiency <= 0) return Integer.MAX_VALUE;
        long scaled = (long) Math.max(1, baseTicks) * PERMILLE_ONE;
        return (int) Math.min(Integer.MAX_VALUE, (scaled + efficiency - 1L) / efficiency);
    }

    public static long wearForCrafts(long crafts) {
        return crafts <= 0L ? 0L : Math.min(MAX_WEAR_PER_BATCH, crafts / CRAFTS_PER_BATCH_WEAR);
    }

    public static long wearForTicks(long ticks) {
        return ticks <= 0L ? 0L : ticks / WEAR_TICKS_PER_POINT;
    }
}
