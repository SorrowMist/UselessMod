package com.sorrowmist.useless.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 造化杖提示分页状态。
 *
 * <p>造化杖的提示行数已超过屏幕可用高度，超出部分在原版提示框中被裁掉。此处把提示内容
 * 按页切分：渲染时只取当前页对应的行，玩家用 {@code [} 与 {@code ]} 翻页。</p>
 *
 * <p>页码按物品栈缓存（键为 {@link System#identityHashCode(Object)}），而非全局单值：
 * 切换所持物品、物品被消耗或切换存档后，原键不再出现，页码自然回到首页，无需在事件中清理。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class BeefTooltipPager {

    /** 单页最多行数。 */
    public static final int LINES_PER_PAGE = 12;

    private BeefTooltipPager() {
    }

    /** 读取当前物品栈的页码（从 0 开始）。 */
    public static int getPage(ItemStack stack) {
        return TooltipPageState.pageOf(identityOf(stack));
    }

    /**
     * 取物品栈的标识哈希。
     *
     * <p>{@link ItemStack} 未覆写 {@code equals} 与 {@code hashCode}，其默认实现即按对象身份比较，
     * 因此直接沿用 {@link System#identityHashCode(Object)}。该值在一次游戏会话内唯一且不复用。</p>
     */
    private static int identityOf(ItemStack stack) {
        return System.identityHashCode(stack);
    }

    /**
     * 翻页。
     *
     * @param delta 正值向后翻页，负值向前翻页
     */
    public static void turnPage(ItemStack stack, int delta) {
        TooltipPageState.turnPage(identityOf(stack), delta);
    }

    /** 把行数换算成总页数，至少为 1。 */
    public static int pageCount(int lineCount) {
        return Math.max(1, (lineCount + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
    }

    /**
     * 裁取当前页对应的行区间，并把回绕后的页码写回存储。
     *
     * <p>页码在按键阶段只能单调累加，真正的回绕需要总行数，因此推迟到渲染阶段在此完成。
     * 连续按同一方向键时页码可无界增长，取模后等价于循环翻页。</p>
     *
     * @return 长度恰为 2 的数组 {@code [fromIndex, toIndex)}，可直接用于 {@code List#subList}
     */
    public static int[] pageRange(ItemStack stack, int lineCount, int totalPages) {
        int page = Math.floorMod(getPage(stack), totalPages);
        TooltipPageState.setPage(identityOf(stack), page);
        int from = Math.min(page * LINES_PER_PAGE, lineCount);
        int to = Math.min(from + LINES_PER_PAGE, lineCount);
        return new int[]{from, to};
    }

    /** 生成翻页指示行，形如 {@code 1/3 [ ] 翻页}。 */
    public static MutableComponent pageIndicator(int page, int totalPages) {
        return Component.translatable(
                "tooltip.useless_mod.tooltip_page",
                page + 1,
                totalPages,
                "[",
                "]"
        );
    }
}