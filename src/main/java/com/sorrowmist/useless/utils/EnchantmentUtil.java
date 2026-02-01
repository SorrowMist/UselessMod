package com.sorrowmist.useless.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;

public class EnchantmentUtil {

    /**
     * 根据 Enchantment 的 ResourceKey 获取 Holder<Enchantment>
     * 适用于 1.21（NeoForge）
     *
     * @param level 任意 Level
     * @param key   Enchantments.SHARPNESS 等
     */
    public static Holder<Enchantment> getEnchantmentHolder(Level level,
                                                           ResourceKey<Enchantment> key) {
        if (level == null) {
            throw new IllegalArgumentException("Level 不能为空（服务端和客户端都需要 Level 才能访问注册表）");
        }

        Registry<Enchantment> registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        return registry.getHolderOrThrow(key);
    }

    /**
     * 安全地向物品栈添加附魔
     *
     * @param stack          目标物品栈
     * @param lookup         注册表查询句柄 (可以通过 level.registryAccess() 或物品栏参数获取)
     * @param enchantmentKey 附魔的 ResourceKey
     * @param level          附魔等级
     */
    public static void applyEnchantment(ItemStack stack, HolderLookup.Provider lookup,
                                        ResourceKey<Enchantment> enchantmentKey, int level) {
        // 获取附魔的 Holder
        Holder.Reference<Enchantment> holder = lookup.lookupOrThrow(Registries.ENCHANTMENT)
                                                     .getOrThrow(enchantmentKey);

        // 获取当前物品的附魔组件
        ItemEnchantments currentEnchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(currentEnchantments);

        // 设置等级并写回组件
        mutable.set(holder, level);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }
}