package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.CraftingPlan;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Applies smart-doubling to plans produced outside AE2's standard planner. */
public final class SmartDoublingPlans {
    private SmartDoublingPlans() {
    }

    /** 不带 {@code Level} 的重载：只认原样返还的可复用输入，见 {@link ReusablePatternInputs}。 */
    public static ICraftingPlan rewriteForSubmission(
            ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup) {
        return rewriteForSubmission(plan, providerLookup, null);
    }

    /**
     * @param level 判定「可复用输入」用的关卡。改写后的样板会按它决定哪些输入不放大倍率；
     *              执行期重建同一样板时必须传同一个关卡，否则倍率与计划对不上。
     */
    public static ICraftingPlan rewriteForSubmission(
            ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup,
            @Nullable Level level) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(providerLookup, "providerLookup");

        Map<IPatternDetails, Long> rewritten = SmartDoublingPlanner.rewrite(
                plan.patternTimes(), providerLookup, level);
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
