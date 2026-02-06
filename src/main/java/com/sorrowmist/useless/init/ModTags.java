package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {

    public static final TagKey<Item> CATALYSTS = createItemTag("catalysts");

    private static TagKey<Item> createItemTag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, path));
    }

    private ModTags() {
    }
}
