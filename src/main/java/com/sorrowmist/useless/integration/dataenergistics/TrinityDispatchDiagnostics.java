package com.sorrowmist.useless.integration.dataenergistics;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceTickBudget;
import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.Locale;

/**
 * 三位一体合成样板派发的诊断埋点。
 *
 * <p>用于确认三件事：</p>
 * <ol>
 *   <li>一 tick 收到的批次数量（DE 的 bigint 批次节奏）；</li>
 *   <li>每批的实际装配次数 —— {@code 1} 表示整批折叠为一次装配再按 N 放大（本机的核心优化）；</li>
 *   <li>「本机每 tick 时间预算」是否正在降频（见 {@link AlloyFurnaceTickBudget}）。</li>
 * </ol>
 *
 * <p><b>累加一律用 {@link BigInteger}</b>：单批规模即可超过 1e20，用 long 会溢出为负数，
 * 用 double 会丢失尾数精度（两种写法都曾在本文件中造成缺陷）。仅在显示时转换为科学计数法，
 * 且按十进制位数直接构造、不经过浮点，因此日志中输出的是精确值。</p>
 *
 * <p>界面上的「正在合成 N」仅表示<b>单个批次</b>的量级，不等于一 tick 的总量，验收应以本处汇总为准。</p>
 */
public final class TrinityDispatchDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 有推送才汇总，避免空闲时刷屏。 */
    private static final long SUMMARY_INTERVAL_TICKS = 40L;
    /** 科学计数保留的尾数位数（仅显示用）。 */
    private static final int SCI_DIGITS = 4;

    private static BigInteger craftsTotal = BigInteger.ZERO;
    private static BigInteger craftsLargest = BigInteger.ZERO;
    private static long batches;
    private static long probesTotal;
    private static int probesMax;
    private static long lastSummaryTick = Long.MIN_VALUE;

    private TrinityDispatchDiagnostics() {}

    /** 长版 counted 路径的一次物理派发。 */
    public static synchronized void reportCraftingPatternWindow(long tick, long crafts, int probes) {
        report(tick, BigInteger.valueOf(crafts), probes);
    }

    /** bigint 批次（{@code BigIntegerCraftingProviderAdapter}）的一次派发。 */
    public static synchronized void reportCraftingPatternWindow(long tick, BigInteger crafts, int probes) {
        report(tick, crafts, probes);
    }

    private static void report(long tick, BigInteger crafts, int probes) {
        BigInteger amount = crafts.signum() < 0 ? BigInteger.ZERO : crafts;
        batches++;
        craftsTotal = craftsTotal.add(amount);
        craftsLargest = craftsLargest.max(amount);
        probesTotal += probes;
        probesMax = Math.max(probesMax, probes);

        if (batches == 1L) {
            LOGGER.info("[trinity] 首次合成样板批次：{} 次合成、真实装配 {} 次（1 = 整批折叠成功）",
                    scientific(amount), probes);
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
                "[trinity] 近 {} tick：合成样板批次 {} 个（{} 个/tick）、合计 {} 次合成、单批最大 {}、"
                        + "真实装配合计 {} 次 / 单批最多 {} 次（1 = 整批折叠成功）、降频系数 {}（本机每 tick 实测 {} ms / 预算 {} ms）",
                span,
                batches,
                format("%.2f", (double) batches / span),
                scientific(craftsTotal),
                scientific(craftsLargest),
                probesTotal,
                probesMax,
                format("%.3f", AlloyFurnaceTickBudget.scale()),
                format("%.2f", AlloyFurnaceTickBudget.spentMillis()),
                format("%.2f", AlloyFurnaceTickBudget.budgetMillis()));
        batches = 0L;
        craftsTotal = BigInteger.ZERO;
        craftsLargest = BigInteger.ZERO;
        probesTotal = 0L;
        probesMax = 0;
        lastSummaryTick = tick;
    }

    /**
     * 精确科学计数：按十进制位数直接构造，不经过 double。
     *
     * @param value 任意大小的计数
     * @return 形如 {@code 5.902e+20} 的字符串
     */
    private static String scientific(BigInteger value) {
        if (value == null || value.signum() == 0) {
            return "0";
        }
        boolean negative = value.signum() < 0;
        String digits = value.abs().toString();
        int exponent = digits.length() - 1;
        StringBuilder out = new StringBuilder();
        if (negative) {
            out.append('-');
        }
        out.append(digits.charAt(0));
        if (digits.length() > 1) {
            out.append('.').append(digits, 1, Math.min(digits.length(), 1 + SCI_DIGITS));
        }
        return out.append("e+").append(exponent).toString();
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
