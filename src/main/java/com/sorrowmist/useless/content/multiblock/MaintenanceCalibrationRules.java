package com.sorrowmist.useless.content.multiblock;

/**
 * 维护仓校准小游戏的共享规则。
 *
 * <p>指针位置完全由「自本局开始以来流逝的 tick 数」推导，服务端与客户端使用同一套公式：
 * 客户端据此绘制，服务端据此判定。双方不需要逐帧同步指针坐标。</p>
 *
 * <p><b>为什么不用 {@code Level#getGameTime} 直接对齐：</b>客户端与服务端各自累加自己的
 * 世界时间，两者存在固定偏差，不能当作同一个时钟。因此客户端上报的是<b>相对流逝</b>，
 * 服务端再用 {@link #isPlausibleElapsed} 把它限制在自己观测值的邻域内。</p>
 */
public final class MaintenanceCalibrationRules {

    /** 校准阶段：尚未开始。 */
    public static final byte PHASE_IDLE = 0;
    /** 校准阶段：进行中。 */
    public static final byte PHASE_RUNNING = 1;
    /** 校准阶段：本轮已完成。 */
    public static final byte PHASE_COMPLETED = 2;

    /** 一局需要命中的轮数。 */
    public static final int ROUNDS_TOTAL = 3;

    /** 指针从一端走到另一端所需的 tick 数。往返周期为它的两倍。 */
    public static final int TRAVEL_TICKS = 40;

    /** 第一轮的命中半宽（占轨道长度的比例）。 */
    public static final float START_HALF_WIDTH = 0.10F;
    /** 每通过一轮，命中半宽收窄的幅度。 */
    public static final float HALF_WIDTH_STEP = 0.02F;

    /**
     * 客户端上报的流逝值与服务端观测值之间允许的最大差值（tick）。
     *
     * <p>这个窗口的作用是吸收网络延迟：客户端以「收到开局包」为计时起点，天然比服务端
     * 晚一个单程延迟；同时它限制了谎报流逝值的空间——作弊者最多只能在本轮目标附近
     * 上下微调，而目标中心每轮重新随机，收益不足以稳定命中。</p>
     */
    public static final long ELAPSED_TOLERANCE_TICKS = 20L;

    private MaintenanceCalibrationRules() {
    }

    /** 指针在轨道上的归一化位置，范围 {@code [0, 1]}；{@code elapsed} 为负时按 0 处理。 */
    public static float pointerAt(long elapsed) {
        if (elapsed <= 0L) {
            return 0.0F;
        }
        long cycle = 2L * TRAVEL_TICKS;
        long phase = Math.floorMod(elapsed, cycle);
        return phase <= TRAVEL_TICKS
                ? (float) phase / TRAVEL_TICKS
                : (float) (cycle - phase) / TRAVEL_TICKS;
    }

    /** 已通过给定轮数后，本轮目标的命中半宽。 */
    public static float halfWidthFor(int roundsCleared) {
        int clamped = Math.max(0, Math.min(roundsCleared, ROUNDS_TOTAL - 1));
        return START_HALF_WIDTH - HALF_WIDTH_STEP * clamped;
    }

    /** 客户端上报的流逝值是否落在服务端观测值的容差窗口内。 */
    public static boolean isPlausibleElapsed(long reported, long serverObserved) {
        return reported >= serverObserved - ELAPSED_TOLERANCE_TICKS
                && reported <= serverObserved + ELAPSED_TOLERANCE_TICKS;
    }

    /** 指针是否落在以 {@code center} 为中心、半宽为 {@code halfWidth} 的目标区间内。 */
    public static boolean isHit(float pointer, float center, float halfWidth) {
        return Math.abs(pointer - center) <= halfWidth;
    }
}
