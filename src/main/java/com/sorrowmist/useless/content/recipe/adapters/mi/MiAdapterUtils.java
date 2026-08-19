package com.sorrowmist.useless.content.recipe.adapters.mi;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared helpers for Modern Industrialization recipe adapters. */
final class MiAdapterUtils {
    static final String MOD_ID = "modern_industrialization";

    private MiAdapterUtils() {
    }

    static ItemStack item(String path) {
        return item(ResourceLocation.fromNamespaceAndPath(MOD_ID, path));
    }

    static ItemStack item(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
