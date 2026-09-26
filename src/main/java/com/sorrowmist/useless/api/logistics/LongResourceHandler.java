package com.sorrowmist.useless.api.logistics;

/**
 * long 级资源处理器的公共标记。
 *
 * <p>{@link LongItemHandler}、{@link LongFluidHandler}、{@link LongEnergyHandler} 都实现它，
 * 让「解析出来的处理器」有一个统一的静态类型，调用方不必先 cast 成 {@code Object} 再试。</p>
 *
 * <p>化学品刻意不在这个家族里：它的视图接口（{@code ChemicalHandlerView}）从设计之初就以 long
 * 计数，本身已经是 long 级契约，再包一层只会多一次无意义的转发。</p>
 */
public interface LongResourceHandler {
}
