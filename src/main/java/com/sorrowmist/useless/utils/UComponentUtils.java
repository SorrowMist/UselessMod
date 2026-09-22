package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.core.component.UComponents;
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
     * 获取物品的自动熔炼状态
     *
     * <p>开启后，造化杖挖掘产生的掉落物会先按熔炉配方熔炼一次再入库。</p>
     *
     * @param stack 物品栈
     * @return 自动熔炼是否启用
     */
    public static boolean isAutoSmeltEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.AutoSmeltComponent.get(), false);
    }

    /**
     * 获取物品的AE存储优先状态
     * @param stack 物品栈
     * @return AE存储优先是否启用
     */
    public static boolean isAEStoragePriorityEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.AEStoragePriorityComponent.get(), false);
    }

    /**
     * 获取物品的「范围磁力」状态
     *
     * <p>默认开启：没有显式写入组件时按启用处理，与物品属性初始化保持一致。</p>
     *
     * @param stack 物品栈
     * @return 范围磁力是否启用
     */
    public static boolean isBeefMagnetEnabled(ItemStack stack) {
        return stack.getOrDefault(UComponents.BeefMagnetEnabledComponent.get(), true);
    }

    /**
     * 判断本次产出是否需要「接管」——即是否要把掉落物收起来而不是留在原地走原版拾取。
     *
     * <p>范围磁力是所有掉落物的总开关，AE 存储优先独立生效：只要两者任一开启，
     * 造化杖产生的掉落物就需要经过 {@code MiningUtils.handleDrops} 处理
     * （AE 优先存入，塞不下的按磁力开关决定进背包还是落地）。</p>
     *
     * @param stack 工具
     * @return 是否需要接管本次掉落
     */
    public static boolean shouldCollectDrops(ItemStack stack) {
        return isBeefMagnetEnabled(stack) || isAEStoragePriorityEnabled(stack);
    }
}
