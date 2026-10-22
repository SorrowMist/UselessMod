package com.sorrowmist.useless.content.multiblock;

/**
 * 万象合金炉维护度的共享规则。
 *
 * <p>维护度是整机状态而非单个部件状态，因此由核心持有与扣减；维护仓只负责提供
 * 把维护度恢复到满值的操作入口。这与校准小游戏的分工一致：玩法状态归核心，交互入口归仓体。</p>
 *
 * <h2>方向：满值起步，越用越低</h2>
 *
 * <p>新机器的维护度为 {@link #MAX_MAINTENANCE}，即「完全健康」；运行与合成都会产生磨损，
 * 使维护度下降。降到 {@link #WARNING_THRESHOLD} 以下进入警告段，加工开始变慢；
 * 降到 0 停机，必须由维护仓校准才能恢复。</p>
 *
 * <h2>为什么按「运行 tick + 合成批次」两条线累加磨损</h2>
 *
 * <p>只按 tick 累加会让满载与轻载的磨损速度相同，机器一旦开机就按时间老化；
 * 只按处理量累加又会让长期挂在网络上等任务的机器完全不磨损。两者叠加后，
 * 「开机即老化」与「干得越多磨得越快」都能体现。</p>
 *
 * <h2>分段而非线性</h2>
 *
 * <p>维护度不低于 {@link #WARNING_THRESHOLD} 时不产生任何影响，玩家可以正常生产；
 * 跌破该点进入警告段，加工时长随维护度下降而线性放大；到 0 停机。
 * 这样「什么时候该维护」有一个明确的、可预期的分界，而不是全程都在微妙地拖慢。</p>
 *
 * <h2>量级</h2>
 *
 * <p>按 {@link #WEAR_TICKS_PER_POINT} 换算，持续满载约 4 小时耗尽维护度，
 * 其中前约 2.4 小时完全无感。这个量级让维护成为周期性事件而不是开机负担。</p>
 */
public final class MaintenanceWearRules {

    /** 维护度上限，即新机器的初始值。 */
    public static final long MAX_MAINTENANCE = 1000L;

    /** 警告段起点：维护度低于该值后开始产生效率影响。 */
    public static final long WARNING_THRESHOLD = 600L;

    /**
     * 每累积多少个「有实际进展的运行 tick」消耗 1 点维护度。
     *
     * <p>288 tick 合 14.4 秒，满值约 4 小时。取这个量级是为了让连续生产成为磨损的主因，
     * 而不是让机器开一会儿就需要维护。</p>
     */
    public static final long WEAR_TICKS_PER_POINT = 288L;

    /**
     * 批次磨损的换算单位：每完成 {@value} 个合成消耗 1 点维护度。
     *
     * <p>取 64 是为了让大批量合成有可感知的额外磨损，同时不至于让小批量合成
     * 比纯 tick 磨损更重——一个 1 秒完成的配方只贡献约 0.03 点。</p>
     */
    public static final long CRAFTS_PER_BATCH_WEAR = 64L;

    /**
     * 单批合成最多消耗的维护度。
     *
     * <p>万象样板可以一次推送极大的数量（BigInteger 批次），若不封顶，一次推送就可能把
     * 维护度直接推到停机点。封顶让磨损随产量增长但保持可预期。</p>
     */
    public static final long MAX_WEAR_PER_BATCH = 20L;

    /** 警告段末端的效率千分比；再往前一步就是停机。 */
    public static final int MIN_WARNING_EFFICIENCY_PERMILLE = 500;

    /** 千分比基准，效率满值。 */
    public static final int PERMILLE_ONE = 1000;

    private MaintenanceWearRules() {
    }

    /** 维护度是否已耗尽（停机点）。 */
    public static boolean isOverdue(long maintenance) {
        return maintenance <= 0L;
    }

    /** 维护度是否已跌破警告阈值（含停机点）。 */
    public static boolean isWarning(long maintenance) {
        return maintenance < WARNING_THRESHOLD;
    }

    /** 把维护度限制在合法区间内。 */
    public static long clamp(long maintenance) {
        return Math.max(0L, Math.min(MAX_MAINTENANCE, maintenance));
    }

    /**
     * 当前维护度下的效率千分比：正常段为 {@link #PERMILLE_ONE}，警告段随维护度下降线性下滑至
     * {@link #MIN_WARNING_EFFICIENCY_PERMILLE}，耗尽后为 0。
     *
     * <p>返回值直接参与加工时长的放大：效率 750‰ 意味着同样一个配方要多花约三分之一的时间。</p>
     */
    public static int efficiencyPermille(long maintenance) {
        if (maintenance <= 0L) {
            return 0;
        }
        if (maintenance >= WARNING_THRESHOLD) {
            return PERMILLE_ONE;
        }
        long missing = WARNING_THRESHOLD - maintenance;
        int drop = PERMILLE_ONE - MIN_WARNING_EFFICIENCY_PERMILLE;
        return PERMILLE_ONE - (int) (drop * missing / WARNING_THRESHOLD);
    }

    /**
     * 按当前维护度放大加工时长，向上取整且不小于原值。
     *
     * <p>用千分比直接换算而不是先取整数倍数：效率 750‰ 时 200 tick 的配方应变成 267 tick，
     * 若先算「倍数」再相乘会取整成 2 倍，警告段的减速就会显得突兀。</p>
     *
     * <p>维护度耗尽时效率为 0，返回 {@link Integer#MAX_VALUE} 而不是抛错，让调用方无需额外判空；
     * 实际上停机的机器已被 {@link #isOverdue} 拦住，不会进入任务。</p>
     */
    public static int scaledProcessTime(int baseTicks, long maintenance) {
        int permille = efficiencyPermille(maintenance);
        if (permille <= 0) {
            return Integer.MAX_VALUE;
        }
        long scaled = (long) Math.max(1, baseTicks) * PERMILLE_ONE;
        return (int) Math.min(Integer.MAX_VALUE, (scaled + permille - 1) / permille);
    }

    /** 完成一批合成应消耗的维护度，含单批封顶；不足一个换算单位时为 0。 */
    public static long wearForCrafts(long crafts) {
        if (crafts <= 0L) {
            return 0L;
        }
        return Math.min(MAX_WEAR_PER_BATCH, crafts / CRAFTS_PER_BATCH_WEAR);
    }

    /** 由累积的运行 tick 折算维护度损耗。 */
    public static long wearForTicks(long ticks) {
        return ticks <= 0L ? 0L : ticks / WEAR_TICKS_PER_POINT;
    }

    /**
     * 把旧存档中的「累计磨损」换算为现行「剩余维护度」。
     *
     * <p>旧语义下该字段从 0 起累加、数值越大越糟；现行语义从 {@link #MAX_MAINTENANCE}
     * 起扣减、数值越小越糟。两者方向相反，直接沿用会让刚造好的旧机器被读成维护度 0 而停机。</p>
     */
    public static long fromLegacyWear(long legacyWear) {
        return clamp(MAX_MAINTENANCE - legacyWear);
    }
}
