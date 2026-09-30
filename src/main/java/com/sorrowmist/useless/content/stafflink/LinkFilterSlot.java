package com.sorrowmist.useless.content.stafflink;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 过滤器里的一格标记物。
 *
 * <p><b>一格只装一种资源</b>，有三种形态：</p>
 *
 * <ul>
 *   <li><b>物品</b>：装物品本身（化学品线路装的是化学品集成自己的「储罐物品」表示）。</li>
 *   <li><b>流体</b>：装<b>流体本身</b>（不是装它的桶）——没有桶的流体也能标记上了。</li>
 *   <li><b>模式</b>：{@link LinkFilterPattern}，即 {@code #tag} 或含通配符的字面量，
 *       能一次覆盖一整类资源。</li>
 * </ul>
 *
 * <p>早先这一格统一是 {@link ItemStack}，流体得先换成「装它的桶」才能存进去，于是
 * 「流体过滤」实际是在比物品：没有桶的流体根本标记不上，把流体拖到物品线路上还会变成
 * 一个水桶物品。现在流体以 {@link FluidStack} 直接存，与物品彻底分开。</p>
 *
 * <p>化学品仍是化学品集成自己的「储罐物品」表示（{@code ChemicalCompatProvider#markerForChemical}），
 * 因此落在 {@link #item()} 上——那套抽象只提供「从物品里读出化学品」，没有可序列化的化学品栈。</p>
 *
 * <h2>两个数量限制</h2>
 *
 * <p>每格还带两个 long，让玩家把「搬多少」说得更细：</p>
 *
 * <ul>
 *   <li>{@link #keepAtSource()}：<b>源端保留</b>。小于等于它就不搬了，避免把源容器掏空
 *       （例如机器输入缓冲留底）。</li>
 *   <li>{@link #maxInto()}：<b>接收端最多存到</b>这个数，到量就停，避免把目标塞爆。</li>
 * </ul>
 *
 * <p><b>{@code 0} 表示不限制</b>（留空即此），与改动前的行为完全一致。</p>
 *
 * <p>把限制放在这里而不是平行的 {@code long[]} 数组里：平行数组会破坏 record 的自动
 * {@code equals}/{@code hashCode}，而塞进 record 后「空槽强制 0」让语义干净、
 * 「第 N 格」只有一个真值来源。</p>
 */
public record LinkFilterSlot(ItemStack item, FluidStack fluid,
                             @Nullable String pattern,
                             long keepAtSource, long maxInto) {
    public static final LinkFilterSlot EMPTY = new LinkFilterSlot(
            ItemStack.EMPTY, FluidStack.EMPTY, null, 0L, 0L);

    public LinkFilterSlot {
        item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        fluid = fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        pattern = pattern == null || pattern.isBlank() ? null : pattern.trim();

        // 三态互斥，优先级：模式 > 流体 > 物品。调用方本来也不该一次给多个，
        // 但存档被改坏 / 网络包构造异常时这里必须收敛到一个确定状态。
        if (pattern != null) {
            fluid = FluidStack.EMPTY;
            item = ItemStack.EMPTY;
        } else if (!fluid.isEmpty()) {
            item = ItemStack.EMPTY;
        }

        // 空槽不保留无意义的限制值：让「空槽」与「有限制的空槽」不可能共存，
        // 也让 equals 干净。
        if (pattern == null && fluid.isEmpty() && item.isEmpty()) {
            keepAtSource = 0L;
            maxInto = 0L;
        } else {
            keepAtSource = Math.max(0L, keepAtSource);
            maxInto = Math.max(0L, maxInto);
        }
    }

    public static LinkFilterSlot ofItem(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? EMPTY
                : new LinkFilterSlot(stack, FluidStack.EMPTY, null, 0L, 0L);
    }

    public static LinkFilterSlot ofFluid(FluidStack stack) {
        return stack == null || stack.isEmpty()
                ? EMPTY
                : new LinkFilterSlot(ItemStack.EMPTY, stack, null, 0L, 0L);
    }

    /**
     * 用一段模式文本造一格；文本非法（见 {@link LinkFilterPattern#parse}）时返回
     * {@link #EMPTY}——调用方若想区分「非法输入」应当自己先 {@code parse} 一次。
     */
    public static LinkFilterSlot ofPattern(String raw) {
        LinkFilterPattern parsed = LinkFilterPattern.parse(raw);
        return parsed == null
                ? EMPTY
                : new LinkFilterSlot(ItemStack.EMPTY, FluidStack.EMPTY, parsed.text(), 0L, 0L);
    }

    /** 用已解析好的模式造一格。 */
    public static LinkFilterSlot ofPattern(LinkFilterPattern parsed) {
        return parsed == null
                ? EMPTY
                : new LinkFilterSlot(ItemStack.EMPTY, FluidStack.EMPTY, parsed.text(), 0L, 0L);
    }

    /**
     * 换掉两个限制值，其余不变。
     *
     * <p>给界面的输入框用：玩家在「保留 / 上限」框里敲数字，只改这两个字段。</p>
     */
    public LinkFilterSlot withLimits(long newKeepAtSource, long newMaxInto) {
        return new LinkFilterSlot(item, fluid, pattern, newKeepAtSource, newMaxInto);
    }

    public boolean isEmpty() {
        return item.isEmpty() && fluid.isEmpty() && pattern == null;
    }

    /** 这一格装的是流体（否则是物品或模式）。 */
    public boolean isFluid() {
        return pattern == null && !fluid.isEmpty();
    }

    /** 这一格装的是物品。 */
    public boolean isItem() {
        return pattern == null && !item.isEmpty();
    }

    /** 这一格是 {@code #tag} / 通配符模式。 */
    public boolean isPattern() {
        return pattern != null;
    }

    /**
     * 这一格有没有「数量限制」。
     *
     * <p>{@code false} 时搬运路径一次都不会去查存量——{@code LongItemHandler#amountOf}
     * 在 AE 端点上要抓整网快照，不能被当成免费查询。</p>
     */
    public boolean hasLimits() {
        return keepAtSource > 0L || maxInto > 0L;
    }

    /** 源端保留量；{@code 0} = 不保留（可以掏空）。 */
    public long keepAtSource() {
        return keepAtSource;
    }

    /** 接收端上限；{@code 0} = 不限。 */
    public long maxInto() {
        return maxInto;
    }
}
