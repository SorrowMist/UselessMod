package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.NotNull;

/**
 * 一次「产物回网」刷写 pass 内可复用的 ME 网络写入目标。
 *
 * <p><b>需要它的原因</b>：{@link CraftingTaskContext#tryOutputKeyToAE} 每次调用都需重新解析
 * 装配体 / 节点 / 网格 / 存储服务（多方块侧为 2 次方块实体查询 + 2 次分配）。低频调用时影响有限，
 * 但<b>可持续吞吐直接由「每 tick 可插入次数」决定</b>（AE2 存储接口只收 {@code long}，
 * 每个产物分段都需一次真实插入），而一 tick 可能插入数千次 —— 该解析成本会直接影响吞吐。</p>
 *
 * <p>一次 pass 内所有键都写在同一个网格上，解析一次即可复用。</p>
 *
 * <p>实现必须与 {@code tryOutputKeyToAE} 语义完全一致：先给 neoecoae 的输出认领一次机会，
 * 剩余部分再以 {@code MODULATE} 写进 ME 存储。</p>
 */
@FunctionalInterface
public interface CraftingAeOutputTarget {
    /**
     * 把一个键的若干量写回网络。
     *
     * @return 实际写入量，恒 ∈ [0, amount]
     */
    long insert(@NotNull AEKey key, long amount);

    /**
     * 实现是否支持把「兼容层认领」提到逐段插入之前（{@link #claim} + {@link #insertRaw}）。
     *
     * <p>默认 {@code false} ⇒ 调用方继续只用 {@link #insert}，行为与拆分前完全一致。
     * 只有返回 {@code true} 时，调用方才可以用「先 {@link #claim} 一次整键总量、再逐段
     * {@link #insertRaw}」的组合替代逐段 {@link #insert} —— 可减少 N-1 次认领调用。</p>
     */
    default boolean supportsClaimHoisting() {
        return false;
    }

    /**
     * 只做「兼容层认领」（neoecoae 的动态产物认领），不写 ME 存储。
     *
     * <p><b>只应在 {@link #supportsClaimHoisting()} 为 true 时调用</b>，且每个键在一次刷写 pass 里
     * 只调用一次，传入该键本趟的<b>全部</b>待投递量（不低于 {@code Long.MAX_VALUE} 即可）。
     * 认领量由兼容层自己的待满足需求封顶，与传入量无关，所以提前到循环外不改变结果。</p>
     *
     * @return 被兼容层认领的量，恒 ∈ [0, totalAmount]
     */
    default long claim(@NotNull AEKey key, long totalAmount) {
        return 0L;
    }

    /**
     * 只写 ME 存储，不做兼容层认领。
     *
     * <p>默认实现退回 {@link #insert}：对没有兼容层认领的实现（{@link #claim} 恒返回 0）
     * 语义完全相同。</p>
     *
     * @return 实际写入量，恒 ∈ [0, amount]
     */
    default long insertRaw(@NotNull AEKey key, long amount) {
        return insert(key, amount);
    }
}
