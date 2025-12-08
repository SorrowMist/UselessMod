// BlacklistManager.java
package com.sorrowmist.useless.registry;

import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;

public class BlacklistManager {
    private static final Set<String> BLACKLISTED_OUTPUTS = new HashSet<>();

    static {
        // 将所有无用锭添加到黑名单
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_1");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_2");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_3");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_4");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_5");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_6");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_7");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_8");
        BLACKLISTED_OUTPUTS.add("useless_mod:useless_ingot_tier_9");
        // 添加新的锭到黑名单，防止它们被催化剂影响
        BLACKLISTED_OUTPUTS.add("useless_mod:useful_ingot");
    }

    public static boolean isOutputBlacklisted(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        return BLACKLISTED_OUTPUTS.contains(itemId);
    }

    public static void addToBlacklist(String itemId) {
        BLACKLISTED_OUTPUTS.add(itemId);
    }

    public static void removeFromBlacklist(String itemId) {
        BLACKLISTED_OUTPUTS.remove(itemId);
    }

    public static Set<String> getBlacklist() {
        return new HashSet<>(BLACKLISTED_OUTPUTS);
    }
}