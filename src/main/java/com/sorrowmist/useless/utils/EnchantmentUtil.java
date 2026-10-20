package com.sorrowmist.useless.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.Map;

public class EnchantmentUtil {

    /**
     * 安全地向物品栈添加附魔
     *
     * @param stack          目标物品栈
     * @param lookup         注册表查询句柄 (可以通过 level.registryAccess() 或物品栏参数获取)
     * @param enchantment 附魔
     * @param level          附魔等级
     */
    public static void applyEnchantment(ItemStack stack, HolderLookup.Provider lookup,
                                        Enchantment enchantment, int level) {
        Map<Enchantment, Integer> enchantments = new HashMap<>(EnchantmentHelper.getEnchantments(stack));
        int normalizedLevel = Math.max(0, level);
        if (enchantments.getOrDefault(enchantment, 0) == normalizedLevel) return;
        if (normalizedLevel == 0) enchantments.remove(enchantment);
        else enchantments.put(enchantment, normalizedLevel);
        EnchantmentHelper.setEnchantments(enchantments, stack);
    }
}
