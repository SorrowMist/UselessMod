package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.BigIntegerCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.BigIntegerCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * 万象合金炉适配器的 <b>bigint（exact）派发层</b>，数据能源 3.3.0 专有。
 *
 * <h2>为什么不直接合进基类</h2>
 *
 * <p>本类 <b>implements {@link BigIntegerCraftingProviderAdapter}</b>，而那个接口是数据能源
 * 3.3.0 才有的。把它单独放一个类，是为了让基类
 * {@link AlloyFurnaceCountedCraftingAdapter} 能在<b>旧版数据能源</b>下正常加载 ——
 * 旧版下 {@link DataEnergisticsBigIntSupport#AVAILABLE} 为假，基类的工厂就不会 new 本类，
 * 于是本类<b>永远不会被加载</b>（类加载是按需的，只是"被 import"不算）。</p>
 *
 * <h2>语义</h2>
 *
 * <p>本层只加两件事，两者都不改长版 counted 的行为：</p>
 * <ul>
 *   <li>{@link #captureCapacityFast}：3.3.0 的快速容量入口（返回不可变 {@link ObjectList}），
 *       与基类的 {@code captureCapacity} 共用同一条算术，口径不会漂移；</li>
 *   <li>{@link #prepareBigIntegerBatch}：exact 一次性准入 —— 拿到的 prototype 是<b>单次推送</b>的
 *       原型，{@code count} 只通过 {@link BigIntegerCraftingAdmission#exactCount()} 传递，
 *       剩余 {@code count-1} 份材料由数据能源自己的 BigInteger 账本扣除。</li>
 * </ul>
 *
 * <p>只有真正具备 bigint 语义的样板才会被发布带 machine identity 的 target（判定仍在基类的
 * {@code supportsExactBatch}），因为数据能源的 exact 分支一旦接管就<b>不会回落</b>长版路径。</p>
 */
final class AlloyFurnaceBigIntegerCraftingAdapter extends AlloyFurnaceCountedCraftingAdapter
        implements BigIntegerCraftingProviderAdapter {

    AlloyFurnaceBigIntegerCraftingAdapter(
            @NotNull ICraftingProvider provider,
            @NotNull BooleanSupplier online,
            @NotNull String routeIdentity,
            @NotNull String machineIdentity,
            @NotNull Supplier<@Nullable CraftingTaskContext> taskContext,
            @NotNull ToIntFunction<Boolean> remainingThreads,
            @NotNull IntSupplier totalThreads,
            @NotNull BigIntegerPush bigIntegerPush) {
        super(provider, online, routeIdentity, machineIdentity, taskContext, remainingThreads, totalThreads,
                bigIntegerPush);
    }

    // ==================== 3.3.0 的快速容量入口 ====================

    @Override
    public @NotNull ObjectList<@NotNull CountedCraftingCapacity> captureCapacityFast(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        CountedCraftingCapacity single = capacityEntry(patternDetails, prototype, requestedCount);
        return single == null ? ObjectLists.emptyList() : ObjectLists.singleton(single);
    }

    // ==================== 原生 bigint（exact）路径 ====================

    @Override
    public @Nullable BigIntegerCraftingAdmission prepareBigIntegerBatch(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            @NotNull BigInteger requestedCount,
            @NotNull CountedCraftingTarget requestedTarget) {
        if (requestedCount.signum() <= 0 || !targetFor(patternDetails).equals(requestedTarget)) {
            return null;
        }
        BigInteger accepted = availableBigIntegerCount(patternDetails, prototype, requestedCount);
        return accepted.signum() <= 0
                ? null
                : new AlloyFurnaceBigIntegerAdmission(this, patternDetails, prototype, accepted);
    }

    /**
     * bigint 批次的准入：{@code exactCount()} 可以超过 {@code long}，因此 {@code count()} 保持接口默认
     * （{@code longValueExact()} 会在超限时抛异常而不是截断）—— exact 路径的记账一律读 {@code exactCount()}。
     */
    private static final class AlloyFurnaceBigIntegerAdmission implements BigIntegerCraftingAdmission {
        private AdmissionState state;
        private final BigInteger count;
        private boolean transferredInputOwnership;

        private AlloyFurnaceBigIntegerAdmission(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] preparedPrototype,
                @NotNull BigInteger count) {
            this.state = new PreparedState(adapter, patternDetails, preparedPrototype);
            this.count = count;
        }

        @Override
        public @NotNull BigInteger exactCount() {
            return count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return transferredInputOwnership;
        }

        @Override
        public boolean commit(KeyCounter @NotNull [] prototype) {
            if (!(state instanceof PreparedState(
                    AlloyFurnaceCountedCraftingAdapter adapter,
                    IPatternDetails patternDetails,
                    KeyCounter[] preparedPrototype))) {
                throw new IllegalStateException("Exact admission has already been committed");
            }
            if (prototype != preparedPrototype) {
                throw new IllegalArgumentException("Exact admission must be committed with its prepared prototype");
            }
            state = ReleasedState.RELEASED;
            boolean accepted = adapter.dispatchBigInteger(patternDetails, prototype, count);
            transferredInputOwnership = accepted;
            return accepted;
        }

        private sealed interface AdmissionState permits PreparedState, ReleasedState {}

        private record PreparedState(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype) implements AdmissionState {}

        private enum ReleasedState implements AdmissionState {
            RELEASED
        }
    }
}
