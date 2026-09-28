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
     * 扫描路径的优化入口：调用方手里已经有 {@link #getStackInSlot(int)} 的结果。
     *
     * <p>默认实现<b>忽略</b> {@code stackFromSlot}，直接走 {@link #amountIn(int)}——这是正确的
     * 默认值，因为 {@link #getStackInSlot(int)} 的返回值「count 不代表槽内真实数量」（见其说明）。
     * 只有「槽内栈的 count 就是真实数量」的实现才该覆写它。</p>
     *
     * <p>存在的理由：扫描路径本来就要读一次 {@code getStackInSlot} 拿类型，紧接着又要
     * {@code amountIn} 拿数量。对底层是 int 槽位的实现，后者等于把同一个槽位<b>再读一遍</b>；
     * 对 AE 端点则是一次完整的 {@code AEKey → ItemStack} 物化（要连带复制整份组件映射）。
     * 覆写它就能把这一次省掉。</p>
     *
     * <p><b>调用方必须保证 {@code stackFromSlot} 确实取自同一个 {@code slot}</b>：
     * 覆写实现会直接拿它的 count 当答案。</p>
     */
    default long amountIn(int slot, ItemStack stackFromSlot) {
        return amountIn(slot);
    }

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

    /**
     * 按类型直接抽走 {@code amount} 个 {@code template}，不经过「第几号槽」。
     *
     * <p><b>过滤器驱动的搬运只走这一个入口。</b>它把「源端有没有这一种」和「能抽多少」
     * 合并成一次调用（{@code simulate} 为真时返回值就是可抽量），调用方因此不必先
     * {@link #findSlot(ItemStack)} 探一次、再 {@code amountIn} 问一次。</p>
     *
     * <p>默认实现 = {@link #findSlot(ItemStack)} + {@link #extract(int, long, boolean)}，
     * 对槽位有限的实现完全够用（多一次槽位扫描，代价可忽略）。</p>
     *
     * <p><b>条目数很大的实现必须覆写。</b>默认实现要先 {@code findSlot}，而 AE 端点的
     * {@code findSlot} 需要先抓一份整网快照 —— 那是一次完整的
     * {@code MEStorage.getAvailableStacks()}，把 ME 网络里的每一种资源都遍历一遍。
     * 覆写成「拿 {@code template} 直接构造资源键、问一次 {@code MEStorage.extract}」即可
     * 把这一步变成一次哈希查找。AE2 的 {@code extract} 本身就是 long 签名，
     * 「先模拟后提交」的时序也完全不变。</p>
     *
     * @return 实际抽出的数量；{@code simulate} 为真时只试算不改动
     */
    default long extractMatching(ItemStack template, long amount, boolean simulate) {
        if (template == null || template.isEmpty() || amount <= 0L) {
            return 0L;
        }
        int slot = findSlot(template);
        return slot < 0 ? 0L : extract(slot, amount, simulate);
    }
}
