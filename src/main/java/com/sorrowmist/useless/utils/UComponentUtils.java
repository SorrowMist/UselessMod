package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.api.component.UComponents;
import net.minecraft.world.item.ItemStack;

public class UComponentUtils {
    /**
     * 获取物品的增强连锁挖矿模式
     * @param stack 物品栈
     * @return 增强连锁挖矿是否启用
     */
    public static boolean isEnhancedChainMiningEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.EnhancedChainMiningComponent.get(), false);
    }

    /**
     * 获取物品的强制挖掘状态
     * @param stack 物品栈
     * @return 强制挖掘是否启用
     */
    public static boolean isForceMiningEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.ForceMiningComponent.get(), false);
    }

    /**
     * 获取物品的AE存储优先状态
     * @param stack 物品栈
     * @return AE存储优先是否启用
     */
    public static boolean isAEStoragePriorityEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.AEStoragePriorityComponent.get(), false);
    }
}
