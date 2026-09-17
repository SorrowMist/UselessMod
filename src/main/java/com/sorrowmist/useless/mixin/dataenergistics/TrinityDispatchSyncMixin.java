package com.sorrowmist.useless.mixin.dataenergistics;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.sorrowmist.useless.integration.dataenergistics.TrinityDispatchAcceleration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在**调用点**把 Trinity 的「异步派发提案」改判成同步派发，从而让一个 pattern 每 tick 能连续吃多个 long 窗口。
 *
 * <p><b>为什么不直接改 {@code CraftingDispatchBudget#asynchronousEnabled()}。</b>试过了，会崩：
 * {@code CraftingDispatchGovernorSettings#validateBudgetOrdering} 在构造期强制校验
 * 「hard 档必须异步 / safe 档必须同步」，而 hard 与 safe 共用同一个 record ⇒ 改访问器等于把 hard
 * 也变成同步 ⇒ {@code CraftingService} 构造时抛 IllegalArgumentException，进世界即崩。
 * 这里只改**派发路径上那一处读值**，预算对象本身仍报告异步，校验照常通过。</p>
 *
 * <p><b>改完会怎样。</b>{@code executeTrinityCrafting} 的 pass 循环里那处判断变成 false 之后，
 * CPU 走 {@code Fallback(SCHEDULER_DISABLED)} 同步路径：提案在同一次 pass 内算完并直接派发，
 * 于是 {@code physicalAttempts > 0} 让外层 {@code while (passes < maxPatterns)} 继续，
 * 配合 {@link TrinityPlanExecutionMixin} 放开的 stage 去重，每 tick 可以连吃多个窗口
 * （{@code maxPatterns = providerQuantum}，hard 档 32768）。
 * 实际张数被三样东西顶住：本模组按线圈线程数给出的窗口预算、
 * DE 自己的 {@code CraftingDispatchWindow} 提交/捕获时间预算、本机合成样板回网队列。</p>
 *
 * <p><b>这是 DE 从未测试过的 hard+同步 组合</b>（校验器明确禁止），所以注入是 fail-soft 的
 * （{@code require = 0} + 线圈线程数为 1 时完全不动）。</p>
 *
 * <p>目标类是包私有的，所以只能用 {@code targets} 字符串形式引用（不能用 class 字面量）。</p>
 */
@Mixin(targets = "com.fish_dan_.data_energistics.common.crafting.trinity.execution.cpu.TrinityDataCoreCpuLogic", remap = false)
public class TrinityDispatchSyncMixin {
    /**
     * 目标方法是 {@code dispatchToAvailableProvider} 的第二个 overload（15 个参数、返回
     * {@code ProviderDispatchOutcome} 的那个）—— 只有它内部读 {@code asynchronousEnabled()}；
     * 同名 overload 必须用完整 descriptor 区分（descriptor 由 javap 取自 data_energistics-1.21.1-3.2.2.jar）。
     */
    @ModifyExpressionValue(
            method = "dispatchToAvailableProvider(Lcom/fish_dan_/data_energistics/common/crafting/trinity/execution/cpu/TrinityDataCoreExecutingCraftingJob;Lappeng/api/crafting/IPatternDetails;Lappeng/api/crafting/IPatternDetails;JLjava/lang/Object;JZLcom/fish_dan_/data_energistics/common/crafting/trinity/dispatch/provider/CraftingProviderPublicationIndex;Ljava/lang/String;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;Lcom/fish_dan_/data_energistics/common/crafting/trinity/dispatch/commit/CraftingDispatchWindow;ILcom/fish_dan_/data_energistics/common/crafting/trinity/dispatch/governor/CraftingDispatchBudget;Ljava/util/function/Consumer;)Lcom/fish_dan_/data_energistics/common/crafting/trinity/execution/cpu/TrinityDataCoreCpuLogic$ProviderDispatchOutcome;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/fish_dan_/data_energistics/common/crafting/trinity/dispatch/governor/CraftingDispatchBudget;asynchronousEnabled()Z"),
            require = 0)
    private boolean useless$forceSynchronousDispatch(boolean original) {
        if (TrinityDispatchAcceleration.windowBudget() <= 1) {
            return original;
        }
        // 只有「本次派发确实是我们这台机器」才改判；别的供应器原样走 DE 的异步路径。
        return TrinityDispatchAcceleration.consumeForcedSync() ? Boolean.FALSE : original;
    }
}
