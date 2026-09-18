package com.sorrowmist.useless.api.crafting.bigint;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 一条 BigInteger 规模的 AE 产物。
 *
 * <p><b>为什么需要它</b>：AE2 自己的存储接口单次最多接受 {@code long}
 * （{@code IMEInventory#insert(AEKey, long, Actionable)}），而大数批次的产物总量可以超过
 * {@code long}。本模组内部用 BigInteger 记账、再按 {@code Long.MAX_VALUE} 切段写回网络，
 * 对外则用这个不可变记录暴露<b>完整</b>数量，让第三方 CPU 能自己核账，而不必去猜分段。</p>
 *
 * <p>切段粒度与数据能源（Data Energistics）自己的
 * {@code PlayerInventoryRefundDelivery#PHYSICAL_CHUNK} 一致，都是 {@code Long.MAX_VALUE}。</p>
 *
 * @param what   资源键（物品 / 流体 / 其它 AE 键）
 * @param amount 精确数量，必须为正
 */
public record AlloyFurnaceBigIntegerOutput(AEKey what, BigInteger amount) {

    /** 校验并冻结一条大数产物。 */
    public AlloyFurnaceBigIntegerOutput {
        Objects.requireNonNull(what, "BigInteger crafting output key must not be null");
        Objects.requireNonNull(amount, "BigInteger crafting output amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("BigInteger crafting output amount must be positive");
        }
    }

    /** @return 该数量是否已超出单个 {@code long} 能表达的范围 */
    public boolean exceedsLong() {
        return amount.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0;
    }
}
