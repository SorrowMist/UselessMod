package com.sorrowmist.useless.api.logistics;

/**
 * long 级能量处理器。
 *
 * <p>NeoForge 的 {@code IEnergyStorage} 只有 int 面。本模组自己的 {@code IEnergyManager}
 * 从设计之初就是 long 契约，因此会<b>原生长途直通</b>：不存在任何 int 中转，也没有补循环。</p>
 *
 * <p>外部模组的 int 级能量能力由适配器包装成这一层，内部分片细节被封在适配器里，
 * 调用方看不到也不需要知道。</p>
 */
public interface LongEnergyHandler extends LongResourceHandler {

    /** 抽出最多 {@code amount} 能量，返回实际抽出量。 */
    long extract(long amount, boolean simulate);

    /** 接收最多 {@code amount} 能量，返回实际接收量。 */
    long receive(long amount, boolean simulate);

    /** 当前储量。 */
    long stored();

    /** 容量。 */
    long capacity();

    default boolean canExtract() {
        return true;
    }

    default boolean canReceive() {
        return true;
    }
}
