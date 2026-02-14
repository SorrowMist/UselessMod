package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 催化剂并行数管理器
 * 统一管理所有催化剂的并行数计算逻辑
 */
public class CatalystParallelManager {

    // 基础并行倍数
    public static final int BASE_PARALLEL = 3;

    // 催化剂等级映射（物品ID -> 等级）
    private static final Map<String, Integer> CATALYST_TIER_MAP = new HashMap<>();

    static {
        // 初始化催化剂等级映射
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_1", 1);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_2", 2);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_3", 3);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_4", 4);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_5", 5);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_6", 6);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_7", 7);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_8", 8);
        CATALYST_TIER_MAP.put("useless_mod:useless_ingot_tier_9", 9);
        CATALYST_TIER_MAP.put("useless_mod:possible_useful_ingot", 10);
        CATALYST_TIER_MAP.put("useless_mod:useful_ingot", Integer.MAX_VALUE);
    }

    /**
     * 获取催化剂的等级
     *
     * @param stack 催化剂物品堆
     * @return 等级，如果不是催化剂则返回0
     */
    public static int getCatalystTier(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return CATALYST_TIER_MAP.getOrDefault(itemId, 0);
    }

    /**
     * 判断物品是否为无用锭（tier 1-9）
     *
     * @param stack 物品堆
     * @return 是否为无用锭
     */
    public static boolean isUselessIngot(ItemStack stack) {
        int tier = getCatalystTier(stack);
        return tier >= 1 && tier <= 9;
    }

    /**
     * 判断物品是否为有用锭
     *
     * @param stack 物品堆
     * @return 是否为有用锭
     */
    public static boolean isUsefulIngot(ItemStack stack) {
        return stack.is(ModItems.USEFUL_INGOT.get());
    }

    /**
     * 判断物品是否为可能有用锭
     *
     * @param stack 物品堆
     * @return 是否为可能有用锭
     */
    public static boolean isPossibleUsefulIngot(ItemStack stack) {
        return stack.is(ModItems.POSSIBLE_USEFUL_INGOT.get());
    }

    /**
     * 计算普通配方的并行数
     * 普通配方使用催化剂时，并行数 = 3^催化剂等级
     *
     * @param catalystStack 催化剂物品堆
     * @return 并行数，无催化剂返回1
     */
    public static int calculateParallelForNormalRecipe(ItemStack catalystStack) {
        if (catalystStack.isEmpty()) return 1;

        // 有用锭提供无限并行
        if (isUsefulIngot(catalystStack)) {
            return Integer.MAX_VALUE;
        }

        int tier = getCatalystTier(catalystStack);
        if (tier <= 0) return 1;

        return (int) Math.pow(BASE_PARALLEL, tier);
    }

    /**
     * 计算无用锭配方的并行数（跨阶合成）
     * 当合成N阶无用锭时，使用M阶催化剂（M > N），并行数从基础重新开始计算
     * 例如：合成4阶，用5阶催化剂，并行数 = 3（不是81）
     * 例如：合成4阶，用6阶催化剂，并行数 = 9（3^2）
     *
     * @param catalystStack 催化剂物品堆
     * @param targetTier    目标无用锭等级（配方输出）
     * @return 并行数，无催化剂或无效组合返回1
     */
    public static int calculateParallelForUselessIngotRecipe(ItemStack catalystStack, int targetTier) {
        if (catalystStack.isEmpty() || targetTier < 1 || targetTier > 9) return 1;

        int catalystTier = getCatalystTier(catalystStack);

        // 有用锭提供无限并行
        if (catalystTier == Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }

        // 催化剂等级必须高于目标等级才有效
        if (catalystTier <= targetTier) return 1;

        // 计算等级差，并行数 = 3^等级差
        int tierDifference = catalystTier - targetTier;
        return (int) Math.pow(BASE_PARALLEL, tierDifference);
    }

    /**
     * 获取配方的目标无用锭等级
     * 通过检查配方输出物品判断
     *
     * @param outputStack 配方输出物品
     * @return 等级（1-9），如果不是无用锭则返回0
     */
    public static int getTargetUselessIngotTier(ItemStack outputStack) {
        if (outputStack.isEmpty()) return 0;

        String itemId = BuiltInRegistries.ITEM.getKey(outputStack.getItem()).toString();
        return CATALYST_TIER_MAP.getOrDefault(itemId, 0);
    }

    /**
     * 获取催化剂的显示名称
     *
     * @param stack 催化剂物品堆
     * @return 显示名称
     */
    public static String getCatalystDisplayName(ItemStack stack) {
        int tier = getCatalystTier(stack);
        return switch (tier) {
            case 1 -> "一阶无用锭";
            case 2 -> "二阶无用锭";
            case 3 -> "三阶无用锭";
            case 4 -> "四阶无用锭";
            case 5 -> "五阶无用锭";
            case 6 -> "六阶无用锭";
            case 7 -> "七阶无用锭";
            case 8 -> "八阶无用锭";
            case 9 -> "九阶无用锭";
            case 10 -> "可能有用锭";
            case Integer.MAX_VALUE -> "有用锭";
            default -> "";
        };
    }

    /**
     * 检查物品是否为有效的催化剂
     *
     * @param stack 物品堆
     * @return 是否为有效催化剂
     */
    public static boolean isValidCatalyst(ItemStack stack) {
        return getCatalystTier(stack) > 0;
    }
}
