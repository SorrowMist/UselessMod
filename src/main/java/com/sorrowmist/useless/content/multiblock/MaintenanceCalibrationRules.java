package com.sorrowmist.useless.content.multiblock;

/** Server-authoritative three-round timing game; clients only extrapolate the pointer. */
public final class MaintenanceCalibrationRules {
    public static final byte PHASE_IDLE = 0;
    public static final byte PHASE_RUNNING = 1;
    public static final byte PHASE_COMPLETED = 2;
    public static final int ROUNDS_TOTAL = 3;
    public static final int TRAVEL_TICKS = 40;
    public static final float START_HALF_WIDTH = 0.10F;
    public static final float HALF_WIDTH_STEP = 0.02F;
    public static final long ELAPSED_TOLERANCE_TICKS = 20L;

    private MaintenanceCalibrationRules() {}

    public static float pointerAt(long elapsed) {
        if (elapsed <= 0L) return 0.0F;
        long cycle = 2L * TRAVEL_TICKS;
        long phase = elapsed % cycle;
        return (float) (phase <= TRAVEL_TICKS ? phase : cycle - phase) / TRAVEL_TICKS;
    }

    public static float halfWidthFor(int roundsCleared) {
        int round = Math.max(0, Math.min(roundsCleared, ROUNDS_TOTAL - 1));
        return START_HALF_WIDTH - HALF_WIDTH_STEP * round;
    }

    public static boolean isPlausibleElapsed(long reported, long serverObserved) {
        // Subtract the smaller nonnegative value: neither negatives nor long overflow can bypass this check.
        return reported >= 0L && serverObserved >= 0L
                && Math.max(reported, serverObserved) - Math.min(reported, serverObserved)
                <= ELAPSED_TOLERANCE_TICKS;
    }

    public static boolean isHit(float pointer, float center, float halfWidth) {
        return Float.isFinite(pointer) && Float.isFinite(center) && Float.isFinite(halfWidth)
                && pointer >= 0.0F && pointer <= 1.0F && halfWidth >= 0.0F
                && Math.abs(pointer - center) <= halfWidth;
    }
}
