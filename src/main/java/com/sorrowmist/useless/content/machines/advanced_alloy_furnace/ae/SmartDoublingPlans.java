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
