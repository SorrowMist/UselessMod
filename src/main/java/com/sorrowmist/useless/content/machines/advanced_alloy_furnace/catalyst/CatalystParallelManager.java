package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst;

import com.sorrowmist.useless.api.enums.CatalystType;
import net.minecraft.world.item.ItemStack;

/**
 * 催化剂并行数管理器
 * 统一管理所有催化剂的并行数计算逻辑
 */
public class CatalystParallelManager {

    public static final int BASE_PARALLEL = 2;

    /**
     * 获取催化剂的等级
     *
     * @param stack 催化剂物品堆
     * @return 等级，如果不是催化剂则返回0
     */
    public static int getCatalystTier(ItemStack stack) {
        return CatalystType.fromStack(stack).getTier();
    }


    /**
     * 判断物品是否为有用锭
     *
     * @param stack 物品堆
     * @return 是否为有用锭
     */
    public static boolean isUsefulIngot(ItemStack stack) {
        return CatalystType.fromStack(stack).isUsefulIngot();
    }

    /**
     * 计算普通配方的并行数
     * 普通配方使用催化剂时，并行数 = 3^催化剂等级
     *
     * @param catalystStack 催化剂物品堆
     * @return 并行数，无催化剂返回1
     */
    public static int calculateParallelForNormalRecipe(ItemStack catalystStack) {
        return CatalystType.fromStack(catalystStack).getNormalRecipeParallel();
    }

    /**
     * 计算无用锭配方的并行数
     * 无用锭配方的并行数 = 2^目标等级
     *
     * @param targetTier    目标无用锭等级（配方输出）
     * @return 并行数，无效目标等级返回1
     */
    public static int calculateParallelForUselessIngotRecipe(int targetTier) {
        if (targetTier < 1 || targetTier > 9) return 1;
        return (int) Math.pow(BASE_PARALLEL, targetTier);
    }

    /**
     * 获取配方的目标无用锭等级
     * 通过检查配方输出物品判断
     *
     * @param outputStack 配方输出物品
     * @return 等级（1-9），如果不是无用锭则返回0
     */
    public static int getTargetUselessIngotTier(ItemStack outputStack) {
        CatalystType type = CatalystType.fromStack(outputStack);
        return type.isUselessIngotTier() ? type.getTier() : 0;
    }

    /**
     * 获取催化剂的显示名称
     *
     * @param stack 催化剂物品堆
     * @return 显示名称
     */
    public static String getCatalystDisplayName(ItemStack stack) {
        return CatalystType.fromStack(stack).getDisplayName();
    }

    /**
     * 检查物品是否为有效的催化剂
     *
     * @param stack 物品堆
     * @return 是否为有效催化剂
     */
    public static boolean isValidCatalyst(ItemStack stack) {
        return CatalystType.fromStack(stack).isValidCatalyst();
    }

    /**
     * 计算催化剂对处理时间的加成
     * 一阶催化剂减少10%时间（90%），二阶减少20%（80%），以此类推
     * 有用锭固定为1 tick
     *
     * @param baseTime      基础处理时间
     * @param catalystStack 催化剂物品堆
     * @return 加成后的处理时间（向上取整，最小为1）
     */
    public static int calculateProcessTimeWithCatalyst(int baseTime, ItemStack catalystStack) {
        return CatalystType.fromStack(catalystStack).calculateProcessTime(baseTime);
    }
}
