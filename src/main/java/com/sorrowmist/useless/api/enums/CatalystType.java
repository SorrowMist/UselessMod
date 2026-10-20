package com.sorrowmist.useless.api.enums;

import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * 高级合金炉可识别的催化剂类型枚举。
 * 统一维护催化剂的默认信息和配置驱动的运行参数。
 */
public enum CatalystType {
    NONE(null, 0, "", 1, false),
    USELESS_INGOT_TIER_1(ModItems.USELESS_INGOT_TIER_1, 1, "一阶无用锭", 2, false),
    USELESS_INGOT_TIER_2(ModItems.USELESS_INGOT_TIER_2, 2, "二阶无用锭", 4, false),
    USELESS_INGOT_TIER_3(ModItems.USELESS_INGOT_TIER_3, 3, "三阶无用锭", 8, false),
    USELESS_INGOT_TIER_4(ModItems.USELESS_INGOT_TIER_4, 4, "四阶无用锭", 16, false),
    USELESS_INGOT_TIER_5(ModItems.USELESS_INGOT_TIER_5, 5, "五阶无用锭", 32, false),
    USELESS_INGOT_TIER_6(ModItems.USELESS_INGOT_TIER_6, 6, "六阶无用锭", 64, false),
    USELESS_INGOT_TIER_7(ModItems.USELESS_INGOT_TIER_7, 7, "七阶无用锭", 128, false),
    USELESS_INGOT_TIER_8(ModItems.USELESS_INGOT_TIER_8, 8, "八阶无用锭", 256, false),
    USELESS_INGOT_TIER_9(ModItems.USELESS_INGOT_TIER_9, 9, "九阶无用锭", 512, false),
    USEFUL_INGOT(ModItems.USEFUL_INGOT, Integer.MAX_VALUE, "有用锭", Integer.MAX_VALUE, true);

    private final Supplier<? extends Item> itemSupplier;
    private final int tier;
    private final String displayName;
    private final int normalRecipeParallel;
    private final boolean infiniteParallel;

    CatalystType(Supplier<? extends Item> itemSupplier, int tier, String displayName, int normalRecipeParallel, boolean infiniteParallel) {
        this.itemSupplier = itemSupplier;
        this.tier = tier;
        this.displayName = displayName;
        this.normalRecipeParallel = normalRecipeParallel;
        this.infiniteParallel = infiniteParallel;
    }

    /**
     * 根据物品堆解析对应的催化剂类型。
     */
    public static CatalystType fromStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return NONE;
        }
        for (CatalystType type : values()) {
            if (type.itemSupplier != null && stack.is(type.itemSupplier.get())) {
                return type;
            }
        }
        return NONE;
    }

    /**
     * 根据无用锭等级获取对应的催化剂类型。
     */
    public static CatalystType uselessIngotTier(int tier) {
        for (CatalystType type : values()) {
            if (type.tier == tier && type.isUselessIngotTier()) {
                return type;
            }
        }
        return NONE;
    }

    public int getTier() {
        return tier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getNormalRecipeParallel() {
        return this == USEFUL_INGOT
                ? normalRecipeParallel
                : ConfigManager.getAdvancedAlloyFurnaceCatalystParallel(tier);
    }

    public boolean isInfiniteParallel() {
        return infiniteParallel;
    }

    public boolean isUsefulIngot() {
        return this == USEFUL_INGOT;
    }

    public boolean isValidCatalyst() {
        return this != NONE;
    }

    public boolean isUselessIngotTier() {
        return tier >= 1 && tier <= 9;
    }

    /**
     * 计算当前催化剂对基础处理时间的修正结果。
     */
    public int calculateProcessTime(int baseTime) {
        if (this == USEFUL_INGOT) {
            return 1;
        }
        if (tier <= 0) {
            return baseTime;
        }
        double multiplier = ConfigManager.getAdvancedAlloyFurnaceCatalystTimeMultiplier(tier);
        return Math.max(1, (int) Math.ceil(baseTime * multiplier));
    }
}
