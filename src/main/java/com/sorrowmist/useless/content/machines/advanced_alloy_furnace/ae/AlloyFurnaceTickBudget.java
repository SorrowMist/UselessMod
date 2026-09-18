package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import java.math.BigInteger;

/**
 * 本机自己的「每 tick 时间预算」动态降频，思路仿数据能源的 30ms 提交预算
 * （{@code CraftingDispatchWindow}）：量一下自己每服务器 tick 花在合金炉合成上的时间，
 * 超预算就把后面批次的窗口预算按比例收窄，避免大数批次把服务端线程拖死。
 *
 * <p>为什么需要它：bigint 批次本身是 O(1) 折叠，但「产物切段 + 回网插入」以及数据能源那侧的
 * 记账都与<b>批次规模</b>相关。线程数可以配到很大（配置者自负），所以必须有运行时反馈：
 * 超了就自动少收一点，缓过来再放开。</p>
 *
 * <p>粒度是<b>全局</b>的（所有机器跑在同一个服务端线程上），和 DE 的每网格预算同一个量级；
 * 多台机器的耗时会累加、共同把降频压下去 —— 这正是想要的效果。</p>
 *
 * <p>统计窗口用墙钟（{@code System.nanoTime()}）而不是游戏刻：调用方只需在自己花时间的地方
 * 报一笔 {@link #addWork(long)} 即可，不需要额外的 tick 管道，也不会因为漏调而失真。</p>
 */
public final class AlloyFurnaceTickBudget {
    /** 统计窗口：约 2–3 个服务器 tick 的墙钟时长。 */
    private static final long WINDOW_NANOS = 50_000_000L;
    /** 每窗口愿意花在这套机器合成上的时间。仿 DE 的 30ms（那是整网格预算），这里取更小的份额。 */
    private static final long BUDGET_NANOS = 10_000_000L;
    /** 平滑系数：新窗口实测占 1/4，兼顾响应与抗抖。 */
    private static final double SMOOTHING = 0.25D;
    /** 收窄下限：再忙也保留一点吞吐，避免完全停手（否则 DE 会走「无容量 → 重新提交」的空转）。 */
    private static final double MIN_SCALE = 0.02D;
    /** 定点缩放精度。 */
    private static final long SCALE_UNITS = 10_000L;

    private static long windowStartNanos;
    private static long spentThisWindow;
    private static double smoothedNanos;

    private AlloyFurnaceTickBudget() {}

    /** 记录一次本机工作耗时（纳秒）。 */
    public static synchronized void addWork(long nanos) {
        rollWindow();
        if (nanos > 0L) {
            spentThisWindow = spentThisWindow > Long.MAX_VALUE - nanos
                    ? Long.MAX_VALUE : spentThisWindow + nanos;
        }
    }

    /**
     * @return 当前批次缩放系数：预算内为 {@code 1.0}；超出按 {@code 预算 / 实测} 收窄（下限 {@link #MIN_SCALE}）
     */
    public static synchronized double scale() {
        rollWindow();
        long spent = Math.max(spentThisWindow, (long) smoothedNanos);
        if (spent <= BUDGET_NANOS) {
            return 1.0D;
        }
        return Math.max(MIN_SCALE, (double) BUDGET_NANOS / (double) spent);
    }

    /** 定点缩放：把 {@code value} 按 {@link #scale()} 收窄；至少保留 1。 */
    public static BigInteger applyScale(BigInteger value) {
        double scale = scale();
        if (scale >= 1.0D) {
            return value;
        }
        long units = Math.max(1L, (long) (scale * SCALE_UNITS));
        BigInteger scaled = value
                .multiply(BigInteger.valueOf(units))
                .divide(BigInteger.valueOf(SCALE_UNITS));
        return scaled.signum() <= 0 ? BigInteger.ONE : scaled;
    }

    /** @return 观测用：本窗口（或平滑值）的每 tick 实测耗时（毫秒） */
    public static synchronized double spentMillis() {
        return Math.max(spentThisWindow, (long) smoothedNanos) / 1_000_000.0D;
    }

    /** @return 观测用：每 tick 预算（毫秒） */
    public static double budgetMillis() {
        return BUDGET_NANOS / 1_000_000.0D;
    }

    /** 窗口到期就把实测值并入平滑值并清零。 */
    private static void rollWindow() {
        long now = System.nanoTime();
        if (windowStartNanos == 0L) {
            windowStartNanos = now;
            return;
        }
        if (now - windowStartNanos < WINDOW_NANOS) {
            return;
        }
        double observedPerTick = spentThisWindow * ((double) WINDOW_NANOS / (now - windowStartNanos));
        smoothedNanos = smoothedNanos <= 0.0D
                ? observedPerTick
                : smoothedNanos * (1.0D - SMOOTHING) + observedPerTick * SMOOTHING;
        windowStartNanos = now;
        spentThisWindow = 0L;
    }
}
