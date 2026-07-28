package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Pure planner that partitions AE operations across eligible in-mod providers. */
public final class SmartDoublingPlanner {
    private SmartDoublingPlanner() {
    }

    public static Map<IPatternDetails, Long> rewrite(
            Map<IPatternDetails, Long> crafts,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup) {
        Map<IPatternDetails, Long> rewritten = new LinkedHashMap<>();
        for (var entry : crafts.entrySet()) {
            IPatternDetails pattern = entry.getKey();
            long totalOperations = entry.getValue();
            if (totalOperations <= 1L || pattern instanceof ScaledProcessingPattern) {
                merge(rewritten, pattern, totalOperations);
                continue;
            }

            long providerCount = countEligibleProviders(providerLookup.apply(pattern), totalOperations);
            if (providerCount == 0L) {
                merge(rewritten, pattern, totalOperations);
                continue;
            }

            long maximumMultiplier = SmartDoublingPatterns.maximumSafeMultiplier(pattern);
            long batchCount = Math.max(
                    Math.min(totalOperations, providerCount),
                    ceilDivPositive(totalOperations, maximumMultiplier));
            long baseMultiplier = totalOperations / batchCount;
            long remainder = totalOperations % batchCount;

            if (remainder > 0L) {
                merge(rewritten, SmartDoublingPatterns.scale(pattern, baseMultiplier + 1L), remainder);
            }
            long baseBatchCount = batchCount - remainder;
            if (baseBatchCount > 0L) {
                merge(rewritten, SmartDoublingPatterns.scale(pattern, baseMultiplier), baseBatchCount);
            }
        }
        return rewritten;
    }

    public static List<ICraftingProvider> eligibleProviders(
            Iterable<ICraftingProvider> providers) {
        if (providers == null) {
            return List.of();
        }
        List<ICraftingProvider> eligible = new ArrayList<>();
        for (ICraftingProvider provider : providers) {
            if (provider instanceof SmartDoublingCraftingProvider) {
                eligible.add(provider);
            }
        }
        return List.copyOf(eligible);
    }

    private static long countEligibleProviders(
            Iterable<ICraftingProvider> providers, long maximumNeeded) {
        if (providers == null) {
            return 0L;
        }
        long count = 0L;
        for (ICraftingProvider provider : providers) {
            if (provider instanceof SmartDoublingCraftingProvider) {
                count++;
                if (count >= maximumNeeded) {
                    break;
                }
            }
        }
        return count;
    }

    private static long ceilDivPositive(long dividend, long divisor) {
        return dividend / divisor + (dividend % divisor == 0L ? 0L : 1L);
    }

    private static void merge(
            Map<IPatternDetails, Long> target, IPatternDetails pattern, long amount) {
        target.merge(pattern, amount, SmartDoublingPlanner::saturatingAdd);
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
