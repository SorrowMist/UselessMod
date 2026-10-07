package com.sorrowmist.useless.content.stafflink;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 过滤器里的一格。
 *
 * <p><b>一格只装一种标记资源 A</b>，有三种形态：</p>
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
 * <h2>三种附加信息</h2>
 *
 * <ul>
 *   <li>{@link #exclude()}：<b>这一格是「黑名单」而不是「白名单」</b>。标记命中的资源<b>不搬</b>，
 *       其余照搬。同一格上「白名单」与「黑名单」互斥——它就是这一格的方向。</li>
 *   <li>{@link #outCond()}：<b>输出端条件</b>，在源容器上测量（旧「源端保留」的推广）。</li>
 *   <li>{@link #inCond()}：<b>输入端条件</b>，在目标容器上测量（旧「接收端上限」的推广）。</li>
 * </ul>
 *
 * <p>两条条件<b>同时成立</b>才允许搬运这一格标记的资源；任一为 {@link LinkFilterCondition#OFF}
 * 即视为不限制。控制材料留空时条件测的就是被搬的资源 A 自身，行为与改动前完全一致。</p>
 *
 * <p><b>空槽不变式</b>：标记为空 ⇒ {@code exclude=false} 且两条条件都是 {@code OFF}，
 * 让「空槽」与「带信息的空槽」不可能共存，也让 {@code equals} 干净。</p>
 */
public record LinkFilterSlot(ItemStack item, FluidStack fluid,
                             @Nullable String pattern,
                             boolean exclude,
                             LinkFilterCondition outCond,
                             LinkFilterCondition inCond) {
    public static final LinkFilterSlot EMPTY = new LinkFilterSlot(
            ItemStack.EMPTY, FluidStack.EMPTY, null, false,
            LinkFilterCondition.OFF, LinkFilterCondition.OFF);

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

        outCond = outCond == null ? LinkFilterCondition.OFF : outCond;
        inCond = inCond == null ? LinkFilterCondition.OFF : inCond;

        // 空槽不保留任何无意义的附加信息：让「空槽」与「带信息的空槽」不可能共存，
        // 也让 equals 干净。
        if (pattern == null && fluid.isEmpty() && item.isEmpty()) {
            exclude = false;
            outCond = LinkFilterCondition.OFF;
            inCond = LinkFilterCondition.OFF;
        }
    }

    public static LinkFilterSlot ofItem(ItemStack stack) {
        return stack == null || stack.isEmpty()
                ? EMPTY
                : new LinkFilterSlot(stack, FluidStack.EMPTY, null, false,
                        LinkFilterCondition.OFF, LinkFilterCondition.OFF);
    }

    public static LinkFilterSlot ofFluid(FluidStack stack) {
        return stack == null || stack.isEmpty()
                ? EMPTY
                : new LinkFilterSlot(ItemStack.EMPTY, stack, null, false,
                        LinkFilterCondition.OFF, LinkFilterCondition.OFF);
    }

    /**
     * 用一段模式文本造一格；文本非法（见 {@link LinkFilterPattern#parse}）时返回
     * {@link #EMPTY}——调用方若想区分「非法输入」应当自己先 {@code parse} 一次。
     */
    public static LinkFilterSlot ofPattern(String raw) {
        LinkFilterPattern parsed = LinkFilterPattern.parse(raw);
        return parsed == null
                ? EMPTY
                : new LinkFilterSlot(ItemStack.EMPTY, FluidStack.EMPTY, parsed.text(), false,
                        LinkFilterCondition.OFF, LinkFilterCondition.OFF);
    }

    /** 用已解析好的模式造一格。 */
    public static LinkFilterSlot ofPattern(LinkFilterPattern parsed) {
        return parsed == null
                ? EMPTY
                : new LinkFilterSlot(ItemStack.EMPTY, FluidStack.EMPTY, parsed.text(), false,
                        LinkFilterCondition.OFF, LinkFilterCondition.OFF);
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

    /** 这一格是「黑名单」而不是「白名单」。 */
    public boolean isExcluded() {
        return exclude;
    }

    /** 这一格有没有启用任何一条控制条件（给界面角标用）。 */
    public boolean hasConditions() {
        return !outCond.isOff() || !inCond.isOff();
    }

    /** 换掉「白名单 / 黑名单」，其余不变。 */
    public LinkFilterSlot withExclude(boolean newExclude) {
        return new LinkFilterSlot(item, fluid, pattern, newExclude, outCond, inCond);
    }

    /** 换掉两条控制条件，其余不变。 */
    public LinkFilterSlot withConditions(LinkFilterCondition newOut, LinkFilterCondition newIn) {
        return new LinkFilterSlot(item, fluid, pattern, exclude, newOut, newIn);
    }

    /**
     * 换掉标记 A，<b>保留</b>白名单/黑名单与两条条件。
     *
     * <p>标记换成空时构造器会把附加信息一起清掉（空槽不变式）。</p>
     */
    public LinkFilterSlot withMarker(LinkFilterSlot marker) {
        if (marker == null) {
            return EMPTY;
        }
        return new LinkFilterSlot(marker.item(), marker.fluid(), marker.pattern(),
                exclude, outCond, inCond);
    }

    /**
     * 兼容旧语义的便捷映射：{@code keep} → 输出端 {@code ≥ self}，
     * {@code max} → 输入端 {@code ≤ self}；{@code 0} 表示该侧条件关闭。
     */
    public LinkFilterSlot withLimits(long keepAtSource, long maxInto) {
        LinkFilterCondition out = keepAtSource > 0L
                ? LinkFilterCondition.self(LinkFilterCondition.Op.AT_LEAST, keepAtSource)
                : LinkFilterCondition.OFF;
        LinkFilterCondition in = maxInto > 0L
                ? LinkFilterCondition.self(LinkFilterCondition.Op.AT_MOST, maxInto)
                : LinkFilterCondition.OFF;
        return new LinkFilterSlot(item, fluid, pattern, exclude, out, in);
    }

    /** 旧「源端保留」值（= 输出端 ≥ self 的阈值）；没有这类条件时返回 0。 */
    public long keepAtSource() {
        return outCond.op() == LinkFilterCondition.Op.AT_LEAST && outCond.isSelf()
                ? outCond.value() : 0L;
    }

    /** 旧「接收端上限」值（= 输入端 ≤ self 的阈值）；没有这类条件时返回 0。 */
    public long maxInto() {
        return inCond.op() == LinkFilterCondition.Op.AT_MOST && inCond.isSelf()
                ? inCond.value() : 0L;
    }
}
