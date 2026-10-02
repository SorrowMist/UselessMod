package com.sorrowmist.useless.content.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;

/**
 * 造化杖作为材料参与合成时，让本次产出物品变为不可破坏（无限耐久）。
 * <p>
 * 覆盖 <b>3×3 工作台</b>与 <b>2×2 背包合成</b>两种格子。
 * <p>
 * <b>为什么走 {@code PlayerEvent.ItemCraftedEvent} 而不是 mixin {@code ResultSlot}：</b>
 * 整合包里装了 <b>FastWorkbench</b>，它用 {@code dev.shadowsoffire.fastbench.util.CraftResultSlotExt
 * extends ResultSlot} <b>覆写</b>了 {@code ResultSlot#onTake}，且不调用 {@code super} ——
 * 所以注入父类 {@code ResultSlot#onTake} 的代码在运行时永远不会执行。
 * 而事件的触发点在 {@code ResultSlot#checkTakeAchievements} 里的
 * {@code EventHooks.firePlayerCraftingEvent(...)}，FastWorkbench 也保留了这个调用，
 * 因此事件路径是 mod 无关、可靠的。
 * <p>
 * 「杖子不被消耗」不在这里处理：{@link EndlessBeafItem} 已覆写
 * {@code hasCraftingRemainingItem() -> true} / {@code getCraftingRemainingItem(stack) -> stack.copy()}，
 * 原版会把杖子返还到合成格。
 */
public final class BeefCraftingHandler {
    private BeefCraftingHandler() {
    }

    /**
     * 若合成格中含任意造化杖变体，则给产出物品打上「不可破坏」。
     *
     * @param result      合成产出栈（{@code ItemCraftedEvent#getCrafting()}）
     * @param craftMatrix 合成格（{@code ItemCraftedEvent#getInventory()}）
     * @return true 表示已施加不可破坏
     */
    public static boolean applyUnbreakableToCraftResult(ItemStack result, Container craftMatrix) {
        if (result == null || result.isEmpty() || craftMatrix == null) {
            return false;
        }
        // 3x3 工作台与 2x2 背包合成都生效（不再按宽度过滤）
        if (!containsEndlessBeaf(craftMatrix)) {
            return false;
        }
        // 只加 Unbreakable，附魔 / rarity / 名称一概不动
        result.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        return true;
    }

    private static boolean containsEndlessBeaf(Container craftMatrix) {
        int size = craftMatrix.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack slot = craftMatrix.getItem(i);
            if (slot != null && !slot.isEmpty() && slot.getItem() instanceof EndlessBeafItem) {
                return true;
            }
        }
        return false;
    }
}
