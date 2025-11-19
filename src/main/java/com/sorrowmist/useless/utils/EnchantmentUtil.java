package com.sorrowmist.useless.utils;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

public class EnchantmentUtil {

    /**
     * 根据 Enchantment 的 ResourceKey 获取 Holder<Enchantment>
     * 适用于 1.21（NeoForge）
     *
     * @param level 任意 Level（必须！不能为 null）
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
}