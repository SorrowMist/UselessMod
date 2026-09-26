package com.sorrowmist.useless.api.logistics;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * long 级流体处理器。
 *
 * <p>NeoForge 的 {@code IFluidHandler} 单次只能 {@code fill}/{@code drain} 一个 int，
 * 且 {@code FluidStack} 的 amount 本身就是 int。这里把「类型」和「数量」拆开表达：
 * 数量走 {@code long} 参数与返回值，类型由 {@link FluidStack} 单独承载（其 amount 视为占位）。</p>
 */
public interface LongFluidHandler extends LongResourceHandler {

    /** 储罐数量。 */
    int getTanks();

    /**
     * 查看某个储罐里的流体类型。<b>只读</b>：返回栈的 amount 不代表罐内真实数量，
     * 真实数量请用 {@link #amountIn(int)}。
     */
    FluidStack getFluidInTank(int tank);

    /** 储罐内实际流体量（long 语义）。空罐返回 0。 */
    long amountIn(int tank);

    /** 储罐容量（long 语义）。 */
    long capacityOf(int tank);

    /** 从指定储罐抽出 {@code amount}。返回值是实际抽出量；{@code simulate} 为真时只试算。 */
    long drain(int tank, long amount, boolean simulate);

    /**
     * 向该处理器注入 {@code type} 流体 {@code amount}。
     * {@code type} 只取其流体类型与组件，其 amount 被忽略。
     *
     * @return 实际注入量；{@code simulate} 为真时只试算
     */
    long fill(FluidStack type, long amount, boolean simulate);
}
