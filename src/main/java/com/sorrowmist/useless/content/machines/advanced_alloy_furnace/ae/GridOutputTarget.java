package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.compat.neoecoae.NeoEcoDynamicOutputCompat;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link CraftingAeOutputTarget} 的默认实现：先给 neoecoae 的输出认领一次机会，剩余量写进 ME 存储。
 *
 * <p>语义与原来两个宿主里的内联 lambda 完全一致（{@code tryOutputKeyToAE} 走 {@link #insert}，
 * 一条不多一条不少）。抽成类的唯一目的是让「兼容层认领」与「存储插入」两段能被分别调用 ——
 * 见下面 {@link #claim} 的说明。</p>
 *
 * <h2>为什么要把认领拆出来</h2>
 *
 * <p>「产物回网」的可持续吞吐完全由「每 tick 能插多少次」决定，而每个分段的固定成本里包含一次
 * {@link NeoEcoDynamicOutputCompat#claim}：它是一次 {@code synchronized} 调用加若干次集合遍历与分配，
 * 在没有 ECO 候选时返回 0。原来这段成本<b>每个 {@code Long.MAX} 分段都要付一次</b>
 * —— 8ms 回网预算下约 6 千次/tick，100ms 下约 7 万次/tick，绝大多数是纯浪费。</p>
 *
 * <p><b>提到循环外为什么不改行为</b>：认领量只取决于 ECO 任务自己的剩余需求
 * （{@code pending.remainingAmount()} 封顶），与传入的 {@code amount} 无关。
 * 所以「按整键总量认领一次」与「逐段各认领一次」得到的认领量完全相同。
 * 调用方只需保证传入量不低于该需求 —— {@link Long#MAX_VALUE} 恒满足。</p>
 */
public final class GridOutputTarget implements CraftingAeOutputTarget {

    private final @Nullable IGrid grid;
    private final MEStorage storage;
    private final IActionSource source;
    /** 构造时判定一次即可：模块是否加载在整个运行期不变。 */
    private final boolean ecoCompatLoaded;

    public GridOutputTarget(@Nullable IGrid grid, @NotNull MEStorage storage, @NotNull IActionSource source) {
        this.grid = grid;
        this.storage = storage;
        this.source = source;
        this.ecoCompatLoaded = ModList.get().isLoaded("neoecoae");
    }

    @Override
    public boolean supportsClaimHoisting() {
        return this.ecoCompatLoaded;
    }

    @Override
    public long claim(@NotNull AEKey key, long totalAmount) {
        if (!this.ecoCompatLoaded || this.grid == null || key == null || totalAmount <= 0L) {
            return 0L;
        }
        return NeoEcoDynamicOutputCompat.claim(this.grid, key, totalAmount);
    }

    @Override
    public long insertRaw(@NotNull AEKey key, long amount) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        return this.storage.insert(key, amount, Actionable.MODULATE, this.source);
    }

    /** 认领 + 存储插入，与拆分前的内联实现逐字等价。 */
    @Override
    public long insert(@NotNull AEKey key, long amount) {
        if (key == null || amount <= 0L) {
            return 0L;
        }
        long claimed = claim(key, amount);
        long accepted = Math.min(amount, claimed);
        long remaining = amount - accepted;
        if (remaining <= 0L) {
            return amount;
        }
        return accepted + insertRaw(key, remaining);
    }
}
