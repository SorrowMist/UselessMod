package com.sorrowmist.useless.mixin.dataenergistics;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.TrinityPlanExecution;
import com.sorrowmist.useless.integration.dataenergistics.TrinityDispatchAcceleration;
import com.sorrowmist.useless.integration.dataenergistics.TrinityDispatchDiagnostics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 放开数据能源 Trinity 调度器的「同一 stage 每 tick 只派发一次」限制。
 *
 * <p><b>为什么需要它。</b>一个 pattern 每 tick 能拿到多少合成量，由两件事决定，而且<b>都在 DE 一侧</b>：
 * ① 单次派发的 AE2 物理窗口 = {@code TrinityDataCoreCpuLogic#limitByInputAvailability} 末尾的
 * {@code Long.MAX_VALUE / amountPerCraft}（九合一 → {@code Long.MAX/9}，界面上的「正在合成 1E」就是它）；
 * ② 一个 stage 每 tick 只能被 poll 一次 —— 本 mixin 针对的就是这一条。
 * provider 侧报多大容量都不会改变这两个数，所以「一条 AE 下单合成超过 long/tick」只能从这里开刀。</p>
 *
 * <p><b>做法。</b>{@code pollDispatchable} 用调用方传入的 {@code inspectedStages} 做 pass 内去重
 * （javadoc 原话：<i>Each stage may be selected at most once per pass</i>）。该集合是
 * {@code executeTrinityCrafting} 的局部变量，每 tick 新建、跨该 tick 的多个 pass 共享。
 * 我们在每次 poll 之前按预算把它清空，于是同一 stage 能在同一 tick 的后续 pass 里再次被 poll、
 * 再次拿到一个完整的 long 窗口（{@code TrinityExactWorkingInventory#refillPhysicalWindows}
 * 每个 pass 都会从 BigInteger 溢出量把窗口补满）。</p>
 *
 * <p><b>为什么不会失控（三道闸都不用本 mixin 实现）。</b>
 * ① 外层循环 {@code while (passes < maxPatterns)}，{@code maxPatterns = providerQuantum}
 * （hard 档 32768 / safe 档 2），且 DE 的 governor 会按服务器负载自适应降档；
 * ② {@code CraftingDispatchWindow#isExhausted()}：每网格 30ms 提交预算与调用次数硬顶；
 * ③ 我们自己的回网队列：装满之后 provider 报 0 容量 ⇒ 该 pass 物理调用数为 0 ⇒ 外层循环自己 break。
 * 另外本 mixin 自带每 tick 预算（= 线圈线程数），线程数设为 1 即完全回到 DE 原行为。</p>
 */
@Mixin(value = TrinityPlanExecution.class, remap = false)
public class TrinityPlanExecutionMixin {
    /** 上一次记账的 tick；换 tick 时重置本 tick 的窗口预算。 */
    @Unique
    private long useless$stageWindowTick = Long.MIN_VALUE;
    /** 本 tick 已经用掉的窗口数（含第一个）。 */
    @Unique
    private int useless$stageWindowsThisTick;

    /**
     * @param currentTick      DE 传进来的当前 tick，直接当预算重置的时刻用
     * @param inspectedStages  调用方持有的 pass 内去重集合；清空它等于让本 pass 重新可见
     */
    @Inject(method = "pollDispatchable", at = @At("HEAD"), require = 0)
    private void useless$reopenStageGate(long currentTick,
                                         Set<Integer> inspectedStages,
                                         Predicate<TrinityPlanExecution.Work> workDispatchable,
                                         boolean allowNewLease,
                                         CallbackInfoReturnable<Optional<TrinityPlanExecution.Work>> callback) {
        int limit = TrinityDispatchAcceleration.windowBudget();
        if (limit <= 1 || inspectedStages == null) {
            return;
        }
        // 走到这里就证明：目标方法已找到、注入已生效（内部只打一次日志）
        TrinityDispatchDiagnostics.reportAccelerationApplied(limit);
        // 这台 CPU 上还跑着别人的机器时不能乱放开：只有我们的炉子最近真的被派发过才动闸门，
        // 否则完整保留 DE 的「每个 stage 每 tick 最多一次」公平性语义。
        if (!TrinityDispatchAcceleration.recentlyEngaged()) {
            this.useless$stageWindowTick = Long.MIN_VALUE;
            return;
        }
        if (this.useless$stageWindowTick != currentTick) {
            this.useless$stageWindowTick = currentTick;
            this.useless$stageWindowsThisTick = 1;
            return;
        }
        if (this.useless$stageWindowsThisTick >= limit) {
            return;
        }
        this.useless$stageWindowsThisTick++;
        inspectedStages.clear();
        TrinityDispatchDiagnostics.reportGateReopened();
    }
}
