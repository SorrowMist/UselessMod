package com.sorrowmist.useless.api.crafting.bigint;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Objects;

/**
 * 一次容量查询的结果：本机此刻最多能接受多少份「单次原型」。
 *
 * <p>份数用 {@link BigInteger} 表达，可以远超 {@code long} —— 这是本 API 与 AE2 原生
 * {@code ICraftingProvider#pushPattern} 最本质的区别。</p>
 *
 * @param accepted  可接受份数；{@link BigInteger#ZERO} 表示此刻一份都收不了
 * @param statusKey 为 0 时给出的原因（一个语言键，可直接显示给玩家）；非 0 时为空串
 */
public record AlloyFurnaceBigIntegerCapacity(BigInteger accepted, String statusKey) {

    /** 校验并冻结一次容量结果。 */
    public AlloyFurnaceBigIntegerCapacity {
        Objects.requireNonNull(accepted, "BigInteger crafting capacity must not be null");
        if (accepted.signum() < 0) {
            throw new IllegalArgumentException("BigInteger crafting capacity must not be negative");
        }
        statusKey = statusKey == null ? "" : statusKey;
    }

    /** @return 本机此刻一份都收不了，并附带原因 */
    public static @NotNull AlloyFurnaceBigIntegerCapacity none(@NotNull String statusKey) {
        return new AlloyFurnaceBigIntegerCapacity(BigInteger.ZERO, statusKey);
    }

    /** @return 可接受 {@code accepted} 份（必须为正） */
    public static @NotNull AlloyFurnaceBigIntegerCapacity of(@NotNull BigInteger accepted) {
        return new AlloyFurnaceBigIntegerCapacity(accepted, "");
    }

    /** @return 此刻是否至少能收一份 */
    public boolean isAvailable() {
        return accepted.signum() > 0;
    }
}
