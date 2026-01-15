package com.sorrowmist.useless.datagen.providers.tags;


import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class UItemTagsProvider extends ItemTagsProvider {

    public UItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, CompletableFuture<TagLookup<Block>> blockTags) {
        super(output, registries, blockTags);
    }

    @Override
    public @NotNull String getName() {
        return "Tags (Item)";
    }

    @Override
    protected void addTags(HolderLookup.@NotNull Provider provider) {
        this.addUselessModTags();
        this.addCTags();
        this.addGtceuTags();
        this.addMinecraftTags();
    }

    private void addUselessModTags() {
        this.tag(this.createTagKey("useless_ingots"))
            .replace(false)
            .add(ModItems.USELESS_INGOT_TIER_1.get())
            .add(ModItems.USELESS_INGOT_TIER_2.get())
            .add(ModItems.USELESS_INGOT_TIER_3.get())
            .add(ModItems.USELESS_INGOT_TIER_4.get())
            .add(ModItems.USELESS_INGOT_TIER_5.get())
            .add(ModItems.USELESS_INGOT_TIER_6.get())
            .add(ModItems.USELESS_INGOT_TIER_7.get())
            .add(ModItems.USELESS_INGOT_TIER_8.get())
            .add(ModItems.USELESS_INGOT_TIER_9.get())
            .add(ModItems.USEFUL_INGOT.get());
    }

    private void addCTags() {
        TagKey<Item> crowbar = this.createExternalTagKey("c", "tools/crowbar");
        TagKey<Item> hammer = this.createExternalTagKey("c", "tools/hammer");
        TagKey<Item> mallet = this.createExternalTagKey("c", "tools/mallet");
        TagKey<Item> screwdriver = this.createExternalTagKey("c", "tools/screwdriver");
        TagKey<Item> wrench = this.createExternalTagKey("c", "tools/wrench");
        TagKey<Item> file = this.createExternalTagKey("c", "tools/file");
        TagKey<Item> knife = this.createExternalTagKey("c", "tools/knife");
        TagKey<Item> miningTool = this.createExternalTagKey("c", "tools/mining_tool");
        TagKey<Item> mortar = this.createExternalTagKey("c", "tools/mortar");
        TagKey<Item> saw = this.createExternalTagKey("c", "tools/saw");
        TagKey<Item> shear = this.createExternalTagKey("c", "tools/shear");
        TagKey<Item> tools = this.createExternalTagKey("c", "tools/tools");
        TagKey<Item> wireCutter = this.createExternalTagKey("c", "tools/wire_cutter");

        this.tag(crowbar)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get());

        this.tag(hammer)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(mallet)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get());

        this.tag(screwdriver)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get());

        this.tag(wrench)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get());

        this.tag(file)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(knife)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(miningTool)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(mortar)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(saw)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(shear)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(tools)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(wireCutter)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());
    }

    private void addGtceuTags() {
        TagKey<Item> crowbar = this.createExternalTagKey("gtceu", "crafting_tools/crowbar");
        TagKey<Item> hammer = this.createExternalTagKey("gtceu", "crafting_tools/hammer");
        TagKey<Item> mallet = this.createExternalTagKey("gtceu", "crafting_tools/mallet");
        TagKey<Item> screwdriver = this.createExternalTagKey("gtceu", "crafting_tools/screwdriver");
        TagKey<Item> wrench = this.createExternalTagKey("gtceu", "crafting_tools/wrench");
        TagKey<Item> file = this.createExternalTagKey("gtceu", "crafting_tools/file");
        TagKey<Item> knife = this.createExternalTagKey("gtceu", "crafting_tools/knife");
        TagKey<Item> mortar = this.createExternalTagKey("gtceu", "crafting_tools/mortar");
        TagKey<Item> saw = this.createExternalTagKey("gtceu", "crafting_tools/saw");
        TagKey<Item> wireCutter = this.createExternalTagKey("gtceu", "crafting_tools/wire_cutter");

        this.tag(crowbar).replace(false).add(ModItems.ENDLESS_BEAF_CROWBAR.get());
        this.tag(hammer).replace(false).add(ModItems.ENDLESS_BEAF_HAMMER.get());
        this.tag(mallet).replace(false).add(ModItems.ENDLESS_BEAF_MALLET.get());
        this.tag(screwdriver).replace(false).add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get());
        this.tag(wrench).replace(false).add(ModItems.ENDLESS_BEAF_WRENCH.get());

        this.tag(file)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(knife)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(mortar)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(saw)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(wireCutter)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());
    }

    private void addMinecraftTags() {
        this.tag(ItemTags.AXES)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(ItemTags.BREAKS_DECORATED_POTS)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(ItemTags.CLUSTER_MAX_HARVESTABLES)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(ItemTags.HOES)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(ItemTags.PICKAXES)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());

        this.tag(ItemTags.SHOVELS)
            .replace(false)
            .add(ModItems.ENDLESS_BEAF_ITEM.get())
            .add(ModItems.ENDLESS_BEAF_WRENCH.get())
            .add(ModItems.ENDLESS_BEAF_SCREWDRIVER.get())
            .add(ModItems.ENDLESS_BEAF_MALLET.get())
            .add(ModItems.ENDLESS_BEAF_CROWBAR.get())
            .add(ModItems.ENDLESS_BEAF_HAMMER.get());
    }

    private TagKey<Item> createTagKey(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(UselessMod.MODID, path));
    }

    private TagKey<Item> createExternalTagKey(String namespace, String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }
}
