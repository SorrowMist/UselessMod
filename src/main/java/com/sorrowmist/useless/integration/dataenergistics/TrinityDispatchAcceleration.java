package com.sorrowmist.useless.integration.dataenergistics;

import com.sorrowmist.useless.core.config.ConfigManager;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 三位一体派发加速的预算与**作用域**。
 *
 * <p><b>预算</b>：一 tick 允许同一个样板 stage 被重复派发的窗口数 = 线圈线程数
 * （沿用已有的 {@code useful_tier_threads} 等配置，不新增配置项）。线程数设 1 等于关掉加速。</p>
 *
 * <p><b>作用域</b>：两个 mixin 都只能挂在 Trinity CPU 级的类上（{@code TrinityPlanExecution} /
 * {@code TrinityDataCoreCpuLogic}），天生会波及同一 CPU 上的其它供应器。所以这里加了一道
 * 「本次派发是否与我们有关」的信号：只有我们的适配器在容量捕获里真的给出过容量时，
 * 才允许强制同步 / 重开 stage 去重闸门。炉子没在合成的时侯，同 CPU 的其它机器完全回到 DE 原行为。</p>
 *
 * <p>用 {@link System#nanoTime()} 做「近期」判断而不是比对 tick：我们的方块实体拿到的
 * {@code Level#getGameTime()} 与 DE 的 {@code TickHandler#getCurrentTick()} 不保证同源，
 * 比 tick 会静默失配。</p>
 */
public final class TrinityDispatchAcceleration {
    /** 「我们刚被派发过」的有效窗口，约 3 tick，跨 tick 边界也不会断。 */
    private static final long ENGAGED_WINDOW_NANOS = 60_000_000L;

    /** 待消费的「本次派发属于我们」信号；由适配器的容量捕获置位，由同步 mixin 消费。 */
    private static final AtomicBoolean PENDING_OUR_DISPATCH = new AtomicBoolean();

    private static volatile long lastOurCaptureNanos = Long.MIN_VALUE;

    private TrinityDispatchAcceleration() {}

    /** @return 每 tick 的窗口预算；恒 ≥ 1，1 表示不加速 */
    public static int windowBudget() {
        return Math.max(1, ConfigManager.getOmniversalUsefulTierThreads());
    }

    /** @return 是否启用加速（窗口预算 &gt; 1） */
    public static boolean enabled() {
        return windowBudget() > 1;
    }

    /**
     * 我们的适配器在容量捕获里给出过容量时调用。
     *
     * <p>两个信号一起置位：{@link #consumeForcedSync()} 用的「本次派发属于我们」，
     * 以及 {@link #recentlyEngaged()} 用的「最近被派发过」。</p>
     */
    public static void noteOurCapacityCaptured() {
        lastOurCaptureNanos = System.nanoTime();
        PENDING_OUR_DISPATCH.set(true);
    }

    /**
     * 消费一次「本次派发属于我们」的信号；由同步 mixin 在读取
     * {@code CraftingDispatchBudget#asynchronousEnabled()} 时调用。
     *
     * <p>置位与消费发生在同一次派发调用内（容量捕获在前、读值在后），所以信号是精确的：
     * 别的供应器的派发拿不到它，只会拿到 {@code false} ⇒ 保持 DE 原行为。</p>
     *
     * @return 本次派发是否属于我们
     */
    public static boolean consumeForcedSync() {
        return PENDING_OUR_DISPATCH.getAndSet(false);
    }

    /**
     * @return 我们的机器最近（约 3 tick 内）是否真的被派发过；闸门重开以此为前置条件
     */
    public static boolean recentlyEngaged() {
        long last = lastOurCaptureNanos;
        return last != Long.MIN_VALUE && System.nanoTime() - last < ENGAGED_WINDOW_NANOS;
    }
}
