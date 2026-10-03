package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Pure planner that partitions AE operations across eligible in-mod providers. */
public final class SmartDoublingPlanner {
    private SmartDoublingPlanner() {
    }

    /**
     * 把 AE 的操作数按「可放大的供应器数量」切分，并把每批包装成「一次推送代表多份操作」。
     *
     * <p>判定「可复用输入」所需的关卡<b>从供应器本身取</b>（见 {@link #levelOfProviders}），
     * 不从 AE2 网格取。</p>
     */
    public static Map<IPatternDetails, Long> rewrite(
            Map<IPatternDetails, Long> crafts,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup) {
        Map<IPatternDetails, Long> rewritten = new LinkedHashMap<>();
        for (var entry : crafts.entrySet()) {
            IPatternDetails pattern = entry.getKey();
            long totalOperations = entry.getValue();
            if (totalOperations <= 1L || pattern instanceof ScaledPattern
                    || !SmartDoublingPatterns.canScale(pattern)) {
                merge(rewritten, pattern, totalOperations);
                continue;
            }

            Iterable<ICraftingProvider> providers = providerLookup.apply(pattern);
            long providerCount = countEligibleProviders(providers, totalOperations);
            if (providerCount == 0L) {
                merge(rewritten, pattern, totalOperations);
                continue;
            }
            Level level = levelOfProviders(providers);

            long maximumMultiplier = SmartDoublingPatterns.maximumSafeMultiplier(pattern);
            long batchCount = Math.max(
                    Math.min(totalOperations, providerCount),
                    ceilDivPositive(totalOperations, maximumMultiplier));
            long baseMultiplier = totalOperations / batchCount;
            long remainder = totalOperations % batchCount;

            if (remainder > 0L) {
                merge(rewritten,
                        SmartDoublingPatterns.scale(pattern, baseMultiplier + 1L, level), remainder);
            }
            long baseBatchCount = batchCount - remainder;
            if (baseBatchCount > 0L) {
                merge(rewritten, SmartDoublingPatterns.scale(pattern, baseMultiplier, level), baseBatchCount);
            }
        }
        return rewritten;
    }

    /**
     * 从候选供应器里取关卡。
     *
     * <p><b>为什么不从 AE2 网格取</b>：那需要在 {@code CraftingService} 上挂一个 mixin 去读它的私有
     * {@code grid} 字段；而该类上已经挂着 OmniSequence 的 {@code OmniCraftingServiceMixin}，
     * 再加一个会<b>静默顶掉对方的注入</b> —— 实测后果是 AppliedEnhancements 的 AELIS 精确规划器
     * 不再参与，计划从真实量级退化成 long 饱和值，任务卡在 0 进度，且日志里没有任何错误。</p>
     *
     * <p>本模组的机器都是方块实体，从供应器自己取关卡既够用又安全；而且计划期与执行期取到的是
     * <b>同一台机器</b>的关卡，天然不会出现「两处判定不一致」。</p>
     */
    private static @Nullable Level levelOfProviders(@Nullable Iterable<ICraftingProvider> providers) {
        if (providers == null) {
            return null;
        }
        for (ICraftingProvider provider : providers) {
            if (provider instanceof BlockEntity blockEntity && blockEntity.getLevel() != null) {
                return blockEntity.getLevel();
            }
        }
        return null;
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
