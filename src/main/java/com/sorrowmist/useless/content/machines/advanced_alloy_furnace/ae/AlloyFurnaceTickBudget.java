package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import com.sorrowmist.useless.core.config.ConfigManager;

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
    /** 全局预算相对「每台机器回网预算」的倍数：留一倍余量给准入 / 任务工作。 */
    private static final long GLOBAL_BUDGET_MULTIPLIER = 2L;
    /** 全局预算下限：即使配置很小也不低于此值（保持原有量级）。 */
    private static final long MIN_GLOBAL_BUDGET_NANOS = 10_000_000L;

    /**
     * 每窗口（{@link #WINDOW_NANOS}）愿意花在这套机器合成上的时间。
     *
     * <p><b>为什么必须由配置推导、不能硬编码</b>：它原来是写死的 10ms，而
     * {@link #scaledFlushBudget(long)} 又要乘上 {@link #scale()}（= {@code 预算 / 实测}）——
     * 于是「把回网预算配置调大」会被这里直接卡回去：配置成 20ms 时回网自己每 tick 花 20ms，
     * 折算到 50ms 窗口约 50ms ⇒ {@code scale = 10/50 = 0.2} ⇒ 实际只剩 4ms，<b>配置文件形同虚设</b>。</p>
     *
     * <p>现在按配置推导：全局 = 每台机器回网预算 × {@link #GLOBAL_BUDGET_MULTIPLIER}，
     * 下限 {@link #MIN_GLOBAL_BUDGET_NANOS}。这样配置就是唯一真相来源，同时仍保留
     * 「多台机器总耗时共同把降频压下去」的全局保护。</p>
     */
    private static long budgetNanos() {
        long perMachine = ConfigManager.getAdvancedAlloyFurnaceAeOutputReturnBudgetMillis() * 1_000_000L;
        long derived = perMachine > Long.MAX_VALUE / GLOBAL_BUDGET_MULTIPLIER
                ? Long.MAX_VALUE : perMachine * GLOBAL_BUDGET_MULTIPLIER;
        return Math.max(MIN_GLOBAL_BUDGET_NANOS, derived);
    }
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
        long budget = budgetNanos();
        if (spent <= budget) {
            return 1.0D;
        }
        return Math.max(MIN_SCALE, (double) budget / (double) spent);
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

    /**
     * 本窗口是否正在降频（{@link #scale()} &lt; 1.0）。
     *
     * <p>对应数据能源 dispatch window 的 {@code BUDGET_EXHAUSTED} 语义：让调用方知道
     * 「容量变小是因为我忙，不是我的能力只有这么多」，从而可以主动延后非紧急批次。</p>
     *
     * <p><b>为什么降频时仍然至少收 1 份</b>（{@link #applyScale} 的 {@code MIN_SCALE} 下限）：
     * 完全报「无容量」会让数据能源走「无容量 → 重新提交」的空转，反而更亏。
     * 所以降频只收窄批次规模，不彻底停手 —— 调用方要停手请自行判断 {@link #isThrottled()}。</p>
     */
    public static synchronized boolean isThrottled() {
        return scale() < 1.0D;
    }

    /** 产物回网窗口的下限：保证重载下仍能推进（够覆盖一次插入），避免队列被彻底饿死。 */
    private static final long MIN_FLUSH_BUDGET_NANOS = 250_000L;

    /**
     * 把一次「产物回网」的时间预算按当前全局预算系数收窄。
     *
     * <p>准入路径用 {@link #applyScale(BigInteger)} 收窄<b>批次规模</b>，回网路径收窄的是
     * <b>本 tick 愿意花的时间</b>。两者共用同一个预算信号，但目的相反：</p>
     * <ul>
     *   <li>准入收窄 ⇒ 少收新活（保护本 tick）；</li>
     *   <li>回网收窄 ⇒ 少投递已完成的产物（同样保护本 tick）。</li>
     * </ul>
     *
     * <p><b>为什么必须收窄</b>：回网预算是<b>每台机器</b>的。若写死一个固定值（例如 4ms），
     * N 台机器最坏就是 N×4ms/tick —— 10 台满载能吃掉大半个 tick。按全局系数收窄后总量自然收敛。</p>
     *
     * <p><b>为什么保留下限</b>：回网是幂等的，理论上可以截断到零；但那样在持续重载下会饿死队列
     * （队列不空 ⇒ 背压一直压着准入 ⇒ 容量 0 ⇒ 整机停摆）。所以保留一个下限保证每 tick 仍有推进。
     * 调用方另有一层保险：预算检查排在第一次插入之后，所以任何情况下每 tick 至少会尝试一次插入。</p>
     *
     * @param baseBudgetNanos 本机的基础回网预算（正数）
     * @return 收窄后的预算，恒 ∈ [{@code min(MIN_FLUSH_BUDGET_NANOS, baseBudgetNanos)}, baseBudgetNanos]
     */
    public static synchronized long scaledFlushBudget(long baseBudgetNanos) {
        if (baseBudgetNanos <= 0L) {
            return MIN_FLUSH_BUDGET_NANOS;
        }
        double scale = scale();
        long scaled = scale >= 1.0D ? baseBudgetNanos : (long) (baseBudgetNanos * scale);
        // 下限只在「收窄」方向生效：不会把一个本来就小于下限的预算抬上去。
        long floor = Math.min(MIN_FLUSH_BUDGET_NANOS, baseBudgetNanos);
        return Math.max(floor, Math.min(baseBudgetNanos, scaled));
    }

    /** @return 观测用：本窗口（或平滑值）的每 tick 实测耗时（毫秒） */
    public static synchronized double spentMillis() {
        return Math.max(spentThisWindow, (long) smoothedNanos) / 1_000_000.0D;
    }

    /** @return 观测用：每 tick 预算（毫秒，随配置推导） */
    public static double budgetMillis() {
        return budgetNanos() / 1_000_000.0D;
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
