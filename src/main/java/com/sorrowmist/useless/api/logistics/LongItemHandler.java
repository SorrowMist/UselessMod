package com.sorrowmist.useless.api.logistics;

import net.minecraft.world.item.ItemStack;

/**
 * long 级物品处理器。
 *
 * <p>和 NeoForge 的 {@code IItemHandler} 相比，这里的「数量」参数与返回值都是 {@code long}：
 * 一次调用要搬的数量不会被截断到 {@link Integer#MAX_VALUE}，调用方也不需要在外面补循环。</p>
 *
 * <p>槽内堆叠上限仍然是物理事实（原版 64），所以底层是 int 槽位的实现一次能抽出的数量受堆叠
 * 限制；但<b>接口本身不设 int 上限</b>，超大容量或多槽聚合的实现可以一次性给出更多。</p>
 */
public interface LongItemHandler extends LongResourceHandler {

    /** 槽位数。 */
    int getSlots();

    /**
     * 查看槽里的物品。<b>只读</b>：返回的栈仅用于判断类型，其 count 不代表槽内真实数量，
     * 真实数量请用 {@link #amountIn(int)}。
     */
    ItemStack getStackInSlot(int slot);

    /** 槽内实际数量（long 语义）。空槽返回 0。 */
    long amountIn(int slot);

    /**
     * 从指定槽位抽走 {@code amount} 个物品。
     *
     * @return 实际抽出的数量（可能小于请求量）；{@code simulate} 为真时只试算不改动
     */
    long extract(int slot, long amount, boolean simulate);

    /**
     * 插入物品。{@code template} 只取其<b>类型</b>，其 count 被忽略，实际插入量由 {@code amount} 决定。
     *
     * @return 实际插入的数量（可能小于请求量）；{@code simulate} 为真时只试算不改动
     */
    long insert(ItemStack template, long amount, boolean simulate);

    /**
     * 找出「和给定物品同类型」的槽位；找不到返回 -1。
     *
     * <p>过滤器驱动的搬运靠它直接定位：有过滤器时「要搬什么」已经完全确定，直接问
     * 「源端有没有这一种」即可，不必扫全表。</p>
     *
     * <p><b>对条目数很大的实现必须覆写。</b>默认实现是线性扫描，而 AE 端点的
     * {@link #getSlots()} 是整个 ME 网络的资源种类数（动辄上千），靠扫描找某一种既慢，
     * 又会因为调用方的扫描预算而永远找不到排在后面的那些。AE 端点按资源键定位。</p>
     */
    default int findSlot(ItemStack template) {
        if (template == null || template.isEmpty()) {
            return -1;
        }
        for (int slot = 0; slot < getSlots(); slot++) {
            if (amountIn(slot) > 0L
                    && ItemStack.isSameItemSameComponents(getStackInSlot(slot), template)) {
                return slot;
            }
        }
        return -1;
    }
}
