package com.sorrowmist.useless.api.crafting.bigint.cpu;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 把一个大数批次绑定到某个已注册的 CPU 适配器上。
 *
 * <p>由调用方在 {@code admit(...)} 时构造并传入；机器只把它当不透明的标签，
 * 用来在产物交付时找到该回调谁。</p>
 *
 * @param adapterId 已通过 {@link AlloyFurnaceBigIntegerCpuAdapters#register} 注册的适配器 id
 * @param cpuToken  调用方自己的上下文（合成任务句柄、CPU 引用等），对机器<b>完全不透明</b>，
 *                  机器只会原样回传，不做任何检查、比较或序列化。
 *                  可以传 {@code null}（例如只有全局回调、不需要区分任务时）
 */
public record AlloyFurnaceBigIntegerCpuBinding(@NotNull ResourceLocation adapterId,
                                              @Nullable Object cpuToken) {

    /** 校验并冻结一次 CPU 绑定。 */
    public AlloyFurnaceBigIntegerCpuBinding {
        Objects.requireNonNull(adapterId, "BigInteger crafting CPU adapter id must not be null");
    }

    /** @return 只有适配器 id、没有上下文的绑定 */
    public static @NotNull AlloyFurnaceBigIntegerCpuBinding of(@NotNull ResourceLocation adapterId) {
        return new AlloyFurnaceBigIntegerCpuBinding(adapterId, null);
    }
}
