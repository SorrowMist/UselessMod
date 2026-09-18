package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.NotNull;

/**
 * 一次「产物回网」刷写 pass 内可复用的 ME 网络写入目标。
 *
 * <p><b>为什么需要它</b>：{@link CraftingTaskContext#tryOutputKeyToAE} 每次调用都要重新解析
 * 装配体 / 节点 / 网格 / 存储服务（多方块侧是 2 次方块实体查询 + 2 次分配）。低频率调用时无所谓，
 * 但<b>可持续吞吐直接由「每 tick 能插多少次」决定</b>（AE2 存储接口只收 {@code long}，
 * 每个产物分段都必须一次真实插入），而一 tick 可能要插数千次 —— 于是这段解析成本直接吃吞吐。</p>
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
}
