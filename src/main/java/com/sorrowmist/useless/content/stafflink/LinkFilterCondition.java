package com.sorrowmist.useless.content.stafflink;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 过滤格里的一条「控制条件」。
 *
 * <p>它把原来那两个纯数字（源端保留 / 接收端上限）推广成一句完整的话：</p>
 *
 * <pre>当 [某个容器] 里的 [控制材料 B] [≥ / ≤] [N] 时，才允许搬运。</pre>
 *
 * <ul>
 *   <li>{@link #op()}：比较方向，{@link Op#OFF} 表示这条条件不启用（默认）。</li>
 *   <li>{@link #item()}/{@link #fluid()}：<b>控制材料 B</b>。两者互斥，流体优先。
 *       <b>都为空 = B 就是被搬运的那种材料 A 自身</b>（即改动前的行为）。</li>
 *   <li>{@link #value()}：阈值 N，非负。</li>
 * </ul>
 *
 * <p><b>测量在哪一侧</b>由它挂在 {@link LinkFilterSlot#outCond()} 还是
 * {@link LinkFilterSlot#inCond()} 上决定：前者在<b>输出端（源容器）</b>上量，后者在
 * <b>输入端（目标容器）</b>上量。</p>
 *
 * <p><b>上限折算</b>（由引擎负责，见 {@code StaffLinkTargets}）：只有当 B 就是 A 自身、
 * 且方向是「输出端 ≥ N」或「输入端 ≤ N」时，这条条件除了门控之外还<b>顺带限定搬运量</b>
 * （等价于旧的「保留 / 上限」）。B 是别的材料时它只是纯门控。</p>
 *
 * <p><b>为什么不支持模式（{@code #tag} / 通配符）</b>：控制材料要按类型去查容器存量
 * （{@code amountOf}），模式匹配的是一整类，没有单一模板可查；而且量一整类也没有明确的语义。</p>
 */
public record LinkFilterCondition(Op op, ItemStack item, FluidStack fluid, long value) {

    /** 比较方向。 */
    public enum Op {
        /** 不启用这条条件。 */
        OFF,
        /** 容器里该材料 ≥ N 才允许搬运。 */
        AT_LEAST,
        /** 容器里该材料 ≤ N 才允许搬运。 */
        AT_MOST
    }

    /** 不启用的条件。 */
    public static final LinkFilterCondition OFF =
            new LinkFilterCondition(Op.OFF, ItemStack.EMPTY, FluidStack.EMPTY, 0L);

    public LinkFilterCondition {
        op = op == null ? Op.OFF : op;
        item = item == null || item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1);
        fluid = fluid == null || fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
        // 控制材料二选一，流体优先（与 LinkFilterSlot 的标记同一套收敛规则）。
        if (!fluid.isEmpty()) {
            item = ItemStack.EMPTY;
        }
        value = Math.max(0L, value);
        // 不启用时不留无意义的控制材料与数值，让 equals / 存档 / 同步都干净。
        if (op == Op.OFF) {
            item = ItemStack.EMPTY;
            fluid = FluidStack.EMPTY;
            value = 0L;
        }
    }

    /** 造一条「测 A 自身」的条件（B 留空）。 */
    public static LinkFilterCondition self(Op op, long value) {
        return new LinkFilterCondition(op, ItemStack.EMPTY, FluidStack.EMPTY, value);
    }

    public boolean isOff() {
        return op == Op.OFF;
    }

    /** 控制材料留空 = 测被搬运的那种材料 A 自身。 */
    public boolean isSelf() {
        return item.isEmpty() && fluid.isEmpty();
    }

    public boolean isItemControl() {
        return !item.isEmpty();
    }

    public boolean isFluidControl() {
        return !fluid.isEmpty();
    }

    public LinkFilterCondition withOp(Op newOp) {
        return new LinkFilterCondition(newOp, item, fluid, value);
    }

    public LinkFilterCondition withValue(long newValue) {
        return new LinkFilterCondition(op, item, fluid, newValue);
    }

    public LinkFilterCondition withControl(ItemStack control) {
        return new LinkFilterCondition(op, control, FluidStack.EMPTY, value);
    }

    public LinkFilterCondition withControl(FluidStack control) {
        return new LinkFilterCondition(op, ItemStack.EMPTY, control, value);
    }

    /** 清掉控制材料（回到「测 A 自身」），保留方向与数值。 */
    public LinkFilterCondition withoutControl() {
        return new LinkFilterCondition(op, ItemStack.EMPTY, FluidStack.EMPTY, value);
    }
}
