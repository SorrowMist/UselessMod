package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.CraftingPlan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Applies smart-doubling to plans produced outside AE2's standard planner. */
public final class SmartDoublingPlans {
    private SmartDoublingPlans() {
    }

    /**
     * 改写提交给 CPU 的计划，把可放大的样板换成「一次推送代表多份操作」的包装。
     *
     * <p>判定「可复用输入」所需的关卡由 {@link SmartDoublingPlanner#rewrite} 从<b>供应器本身</b>取
     * （也就是本模组自己的机器），所以这里不需要关卡参数 —— 也就避免了为此在
     * {@code CraftingService} 上挂 mixin（那会静默顶掉 OmniSequence 的注入，
     * 详见 {@link SmartDoublingPlanner#rewrite} 的说明）。</p>
     *
     * <p><b>注意</b>：一旦这里改写了样板就会构造新的 {@link CraftingPlan}，
     * 从而丢掉 AppliedEnhancements 的 AELIS 精确（BigInteger）计划 —— 两者互斥。</p>
     */
    public static ICraftingPlan rewriteForSubmission(
            ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(providerLookup, "providerLookup");

        Map<IPatternDetails, Long> rewritten = SmartDoublingPlanner.rewrite(
                plan.patternTimes(), providerLookup);
        if (rewritten.equals(plan.patternTimes())) {
            return plan;
        }

        Map<IPatternDetails, Long> immutablePatterns = Collections.unmodifiableMap(
                new LinkedHashMap<>(rewritten));
        return new CraftingPlan(
                plan.finalOutput(),
                plan.bytes(),
                plan.simulation(),
                plan.multiplePaths(),
                plan.usedItems(),
                plan.emittedItems(),
                plan.missingItems(),
                immutablePatterns);
    }
}
