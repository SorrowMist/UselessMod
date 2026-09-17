package com.sorrowmist.useless.integration.dataenergistics;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trinity 派发加速的诊断埋点。
 *
 * <p>背景：一个 pattern 每 tick 能拿到多少合成量由 DE 一侧决定（单次派发 = 一个 AE2 long 物理窗口，
 * 且 {@code inspectedStages} 让同一 stage 每 tick 只被 poll 一次）。我们的 mixin 只放开后者，
 * 所以「装了之后界面还是 1E」有几种完全不同的原因，必须能区分开：</p>
 * <ul>
 *   <li><b>mixin 没被应用</b>（配置没加载 / 目标方法找不到）：没有「已生效」日志，且每 tick 只有 1 次推送；</li>
 *   <li><b>mixin 生效了但 DE 还有第二道门</b>（异步提案许可 {@code proposalCoordinator.dispatchable}）：
 *       有「已生效」日志，但每 tick 仍然只有 1 次推送；</li>
 *   <li><b>真的多窗口</b>：有「已生效」日志，且每 tick 推送次数 &gt; 1。</li>
 * </ul>
 */
public final class TrinityDispatchDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 有推送才汇总，避免空闲时刷屏。 */
    private static final long SUMMARY_INTERVAL_TICKS = 40L;

    private static final AtomicBoolean ACCELERATION_REPORTED = new AtomicBoolean();

    private static long lastSummaryTick = Long.MIN_VALUE;
    private static long windows;
    /** 必须用 double：一个窗口就是 ~1e18，几秒累加就溢出 long 了（0.1 版埋点踩过）。 */
    private static double craftsTotal;
    private static long craftsLargest;
    private static int probesMax;
    private static long probesTotal;
    private static long gateReopens;

    private TrinityDispatchDiagnostics() {}

    /** mixin 第一次真正执行到注入点时调用：证明配置已加载、目标方法已找到、注入成功。 */
    public static void reportAccelerationApplied(int windowsPerTick) {
        if (ACCELERATION_REPORTED.compareAndSet(false, true)) {
            LOGGER.info(
                    "[trinity] 派发加速 mixin 已生效：每 tick 窗口预算 {}（跟随线圈线程数，设为 1 即关闭）",
                    windowsPerTick);
        }
    }

    /**
     * mixin 真的清空了 stage 去重集合时调用一次，用来判断「同 tick 的第二次 pass 到底有没有发生」。
     */
    public static synchronized void reportGateReopened() {
        gateReopens++;
    }

    /**
     * 记录一次抵达本机的合成样板推送。
     *
     * @param tick   当前游戏刻
     * @param crafts 本次推送代表的合成次数
     * @param probes 本次真实装配了多少份（1 = 整批折叠成功，说明 P0 的折叠修复在起作用）
     */
    public static synchronized void reportCraftingPatternWindow(long tick, long crafts, int probes) {
        windows++;
        craftsTotal += crafts;
        craftsLargest = Math.max(craftsLargest, crafts);
        probesMax = Math.max(probesMax, probes);
        probesTotal += probes;

        if (windows == 1L) {
            // 本会话第一笔：立刻给一条，便于确认「机器确实在收 DE 的合成样板推送」
            LOGGER.info(
                    "[trinity] 首次合成样板推送：{} 次合成、真实装配 {} 次（1 = 整批折叠成功）",
                    format("%.3e", (double) crafts),
                    probes);
        }

        if (lastSummaryTick == Long.MIN_VALUE) {
            lastSummaryTick = tick;
            return;
        }
        long span = tick - lastSummaryTick;
        if (span < SUMMARY_INTERVAL_TICKS) {
            return;
        }
        LOGGER.info(
                "[trinity] 近 {} tick：合成样板推送 {} 次（{} 次/tick）、合计 {} 次合成、单次最大 {}、"
                        + "真实装配合计 {} 次 / 单批最多 {} 次（1 = 整批折叠成功）、闸门重开 {} 次",
                span,
                windows,
                format("%.2f", (double) windows / span),
                format("%.3e", craftsTotal),
                format("%.3e", (double) craftsLargest),
                probesTotal,
                probesMax,
                gateReopens);
        windows = 0L;
        craftsTotal = 0.0D;
        craftsLargest = 0L;
        probesTotal = 0L;
        probesMax = 0;
        gateReopens = 0L;
        lastSummaryTick = tick;
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
