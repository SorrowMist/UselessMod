package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.networking.crafting.ICraftingProvider;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * <b>bigint 适配器子类的唯一引用点</b>，独立成一个类是有意的。
 *
 * <h2>为什么不能直接在基类的工厂里 new 子类</h2>
 *
 * <p>{@link AlloyFurnaceBigIntegerCraftingAdapter} 实现了数据能源 3.3.0 才有的
 * {@code BigIntegerCraftingProviderAdapter}。基类
 * {@link AlloyFurnaceCountedCraftingAdapter} 在旧版数据能源下也会被加载，而
 * <b>JVM 校验一个方法体时，为了确认「子类可赋值给基类」可能会去加载子类</b> ——
 * 那在旧版下就是 NoClassDefFoundError。</p>
 *
 * <p>把 {@code new} 挪到本类之后，基类的常量池里只有本类（两版都存在），
 * 它自己校验时永远不需要子类；而本类的 class 文件虽然引用了子类，却<b>只在能力探针为真时</b>
 * 才会被触达（{@link DataEnergisticsBigIntSupport#AVAILABLE}）。
 * 这就是常见的「两级间接」写法，比反射干净：没有反射、没有安全检查开销，
 * 换版本重启即自动切换。</p>
 */
final class AlloyFurnaceBigIntegerAdapterFactory {

    private AlloyFurnaceBigIntegerAdapterFactory() {
    }

    /** 构造 bigint 适配器子类实例。调用方必须已确认 {@link DataEnergisticsBigIntSupport#AVAILABLE}。 */
    static @NotNull CountedCraftingProviderAdapter create(
            @NotNull ICraftingProvider provider,
            @NotNull BooleanSupplier online,
            @NotNull String routeIdentity,
            @NotNull String machineIdentity,
            @NotNull Supplier<@Nullable CraftingTaskContext> taskContext,
            @NotNull ToIntFunction<Boolean> remainingThreads,
            @NotNull IntSupplier totalThreads,
            @NotNull AlloyFurnaceCountedCraftingAdapter.BigIntegerPush bigIntegerPush) {
        return new AlloyFurnaceBigIntegerCraftingAdapter(
                provider,
                online,
                routeIdentity,
                machineIdentity,
                taskContext,
                remainingThreads,
                totalThreads,
                bigIntegerPush);
    }
}
