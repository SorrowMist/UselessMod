package com.sorrowmist.useless.init;

import com.sorrowmist.useless.UselessMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;

public final class ModTags {

    public static final TagKey<Item> CATALYSTS = createItemTag("catalysts");
    public static final TagKey<Item> MOLDS = createItemTag("molds");
    public static final TagKey<Block> OMNIVERSAL_FURNACE_CASINGS =
            BlockTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, "omniversal_furnace_casings"));

    private static TagKey<Item> createItemTag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, path));
    }

    private ModTags() {
    }
}
