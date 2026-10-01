package com.sorrowmist.useless.client;

import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品提示分页页码的存储。
 *
 * <p>页码以物品栈的标识哈希为键。标识哈希在一次游戏会话内唯一且不复用，
 * 因此不同物品栈、乃至同一物品的不同堆叠都不会互相干扰。</p>
 */
public final class TooltipPageState {

    private static final Map<Integer, Integer> PAGES = new ConcurrentHashMap<>();

    /**
     * 提示框可见性标记。
     *
     * <p>由 {@code EndlessBeafItem#appendHoverText} 在渲染提示框时置位，由客户端渲染帧末复位。
     * 按键事件无法得知提示框是否真的绘制，只能依赖此标记判断翻页键当前是否有效。</p>
     */
    private static volatile boolean tooltipVisible;

    /**
     * 本次渲染提示框所对应的物品栈。
     *
     * <p>按键处理与提示框渲染不在同一阶段，按键期间无法回溯到「正在展示哪一份提示」；
     * 记录该引用后，按键入口直接对同一物品栈翻页，不必再按主手/副手推断。
     * 持有的是物品栈引用而非副本，与页码缓存使用同一标识哈希，两者键一致。</p>
     */
    private static volatile ItemStack viewedStack;

    private TooltipPageState() {
    }

    /** 记录本次渲染提示框的物品栈。 */
    public static void setViewedStack(ItemStack stack) {
        viewedStack = stack;
    }

    /** 读取本次渲染提示框的物品栈，无提示框时为 null。 */
    public static ItemStack getViewedStack() {
        return viewedStack;
    }

    /** 标记提示框本轮已渲染。 */
    public static void markTooltipVisible() {
        tooltipVisible = true;
    }

    /** 清除提示框可见标记，应在每帧渲染结束后调用。 */
    public static void clearTooltipVisible() {
        tooltipVisible = false;
    }

    /** 提示框本轮是否已渲染。 */
    public static boolean isTooltipVisible() {
        return tooltipVisible;
    }

    /**
     * 读取页码，不存在时返回 0。
     *
     * <p>只读取值而不创建条目：提示渲染每帧都会调用，若此处写入缓存，
     * 未翻页的物品栈会长期占用内存。</p>
     */
    public static int pageOf(int stackIdentity) {
        Integer page = PAGES.get(stackIdentity);
        return page == null ? 0 : page;
    }

    /**
     * 翻页并把结果写入存储。
     *
     * <p>此处不接收总页数：按键处理发生在提示框渲染之前，调用时无法得知本次提示将产生多少行。
     * 页码只做非负夹紧并单调累加，由 {@code BeefTooltipPager} 在渲染时按总页数取模回绕。</p>
     *
     * @param delta 正值向后翻页，负值向前翻页
     */
    public static void turnPage(int stackIdentity, int delta) {
        int current = pageOf(stackIdentity);
        PAGES.put(stackIdentity, Math.max(0, current + delta));
    }

    /** 覆写页码，供渲染阶段回绕后写回。 */
    public static void setPage(int stackIdentity, int page) {
        PAGES.put(stackIdentity, Math.max(0, page));
    }
}