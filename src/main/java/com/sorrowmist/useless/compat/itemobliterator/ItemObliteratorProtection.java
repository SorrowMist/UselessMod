package com.sorrowmist.useless.compat.itemobliterator;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

public final class ItemObliteratorProtection {
    private static final String NAMESPACE_PREFIX = UselessMod.MODID + ":";

    private ItemObliteratorProtection() {
    }

    public static boolean isProtectedItemId(String itemId) {
        return itemId != null && itemId.startsWith(NAMESPACE_PREFIX);
    }

    public static boolean isProtected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId != null && isProtectedItemId(itemId.toString());
    }
}
