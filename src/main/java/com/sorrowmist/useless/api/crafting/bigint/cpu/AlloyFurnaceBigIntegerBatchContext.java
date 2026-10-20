package com.sorrowmist.useless.api.crafting.bigint.cpu;

import appeng.api.crafting.IPatternDetails;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 一个大数批次的不可变上下文，随每个 CPU 回调一起传入。
 *
 * <p>回调可能发生在提交之后的任意 tick（产物要逐段写回网络），所以请用
 * {@link #batchId()} 而不是「当前正在提交的那个批次」来定位状态。</p>
 *
 * @param batchId         批次唯一标识。存档重载后 {@code cpuToken} 会丢失，请用它重新绑定
 * @param cpuToken        调用方在 {@link AlloyFurnaceBigIntegerCpuBinding#cpuToken()} 里给的上下文，
 *                        原样回传；存档重载后为 {@code null}
 * @param machineIdentity 实际执行本批的机器身份（{@code 维度@x,y,z}）
 * @param pattern         本批使用的样板（原始样板，不是倍率包装类）
 * @param acceptedCount   已获准的份数
 * @param plannedOutputs  样板<b>声明</b>的产物（模板键）。动态产物（id-only 产物槽）的实际键
 *                        可能与之不同，实际值见
 *                        {@link AlloyFurnaceBigIntegerCpuAdapter#onBatchOutputs}
 * @param energyCharged   提交时实际扣掉的能量
 */
public record AlloyFurnaceBigIntegerBatchContext(@NotNull UUID batchId,
                                                @Nullable Object cpuToken,
                                                @NotNull String machineIdentity,
                                                @NotNull IPatternDetails pattern,
                                                @NotNull BigInteger acceptedCount,
                                                @NotNull List<AlloyFurnaceBigIntegerOutput> plannedOutputs,
                                                long energyCharged) {

    /** 校验并冻结一次批次上下文。 */
    public AlloyFurnaceBigIntegerBatchContext {
        Objects.requireNonNull(batchId, "BigInteger crafting batch id must not be null");
        Objects.requireNonNull(machineIdentity, "BigInteger crafting machine identity must not be null");
        Objects.requireNonNull(pattern, "BigInteger crafting batch pattern must not be null");
        Objects.requireNonNull(acceptedCount, "BigInteger crafting batch count must not be null");
        Objects.requireNonNull(plannedOutputs, "BigInteger crafting planned outputs must not be null");
        if (acceptedCount.signum() <= 0) {
            throw new IllegalArgumentException("BigInteger crafting batch count must be positive");
        }
        plannedOutputs = List.copyOf(plannedOutputs);
    }
}
