package com.sorrowmist.useless.init;


import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.enums.RarityExtension;
import com.sorrowmist.useless.api.enums.tool.ToolTypeMode;
import com.sorrowmist.useless.content.items.EndlessBeafItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessMod.MODID);
    // 扳手子类物品注册
    public static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_WRENCH = ITEMS.register(
            "endless_beaf_wrench",
            () -> new EndlessBeafItem(ToolTypeMode.WRENCH_MODE)
    );
    // 螺丝刀子类物品注册
    public static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_SCREWDRIVER = ITEMS.register(
            "endless_beaf_screwdriver",
            () -> new EndlessBeafItem(ToolTypeMode.SCREWDRIVER_MODE)
    );
    // 软锤子类物品注册
    public static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_MALLET = ITEMS.register(
            "endless_beaf_mallet",
            () -> new EndlessBeafItem(ToolTypeMode.MALLET_MODE)
    );
    // 撬棍子类物品注册
    public static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_CROWBAR = ITEMS.register(
            "endless_beaf_crowbar",
            () -> new EndlessBeafItem(ToolTypeMode.CROWBAR_MODE)
    );
    // 硬锤子类物品注册
    public static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_HAMMER = ITEMS.register(
            "endless_beaf_hammer",
            () -> new EndlessBeafItem(ToolTypeMode.HAMMER_MODE)
    );
    public static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_ITEM = ITEMS.register(
            "endless_beaf_item",
            () -> new EndlessBeafItem()
    );

    static final List<DeferredItem<? extends Item>> CREATIVE_MAIN_TAB_ITEMS = new ArrayList<>();
    public static final DeferredItem<Item> USELESS_INGOT_TIER_1 = registerAndAdd(
            "useless_ingot_tier_1",
            () -> new Item(new Item.Properties().rarity(Rarity.COMMON))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_2 = registerAndAdd(
            "useless_ingot_tier_2",
            () -> new Item(new Item.Properties().rarity(Rarity.COMMON))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_3 = registerAndAdd(
            "useless_ingot_tier_3",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_4 = registerAndAdd(
            "useless_ingot_tier_4",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_5 = registerAndAdd(
            "useless_ingot_tier_5",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_6 = registerAndAdd(
            "useless_ingot_tier_6",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_7 = registerAndAdd(
            "useless_ingot_tier_7",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_8 = registerAndAdd(
            "useless_ingot_tier_8",
            () -> new Item(new Item.Properties().rarity(RarityExtension.MYTHIC.getValue()))
    );
    public static final DeferredItem<Item> USELESS_INGOT_TIER_9 = registerAndAdd(
            "useless_ingot_tier_9",
            () -> new Item(new Item.Properties().rarity(RarityExtension.LEGENDARY.getValue()))
    );
    public static final DeferredItem<Item> POSSIBLE_USEFUL_INGOT = registerAndAdd(
            "possible_useful_ingot",
            () -> new Item(new Item.Properties().rarity(RarityExtension.LEGENDARY.getValue()))
    );
    public static final DeferredItem<Item> USEFUL_INGOT = registerAndAdd(
            "useful_ingot",
            () -> new Item(new Item.Properties().rarity(RarityExtension.LEGENDARY.getValue()))
    );
    // 金属模具
    public static final DeferredItem<Item> METAL_MOLD_PLATE = registerAndAdd(
            "metal_mold_plate",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> METAL_MOLD_ROD = registerAndAdd(
            "metal_mold_rod",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> METAL_MOLD_GEAR = registerAndAdd(
            "metal_mold_gear",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> METAL_MOLD_WIRE = registerAndAdd(
            "metal_mold_wire",
            () -> new Item(new Item.Properties())
    );
    public static final DeferredItem<Item> METAL_MOLD_BLOCK = registerAndAdd(
            "metal_mold_block",
            () -> new Item(new Item.Properties())
    );
    // 无用锭对应的齿轮物品
    public static final DeferredItem<Item> USELESS_GEAR_TIER_1 = registerAndAdd(
            "useless_gear_tier_1",
            () -> new Item(new Item.Properties().rarity(Rarity.COMMON))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_2 = registerAndAdd(
            "useless_gear_tier_2",
            () -> new Item(new Item.Properties().rarity(Rarity.COMMON))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_3 = registerAndAdd(
            "useless_gear_tier_3",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_4 = registerAndAdd(
            "useless_gear_tier_4",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_5 = registerAndAdd(
            "useless_gear_tier_5",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_6 = registerAndAdd(
            "useless_gear_tier_6",
            () -> new Item(new Item.Properties().rarity(Rarity.RARE))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_7 = registerAndAdd(
            "useless_gear_tier_7",
            () -> new Item(new Item.Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_8 = registerAndAdd(
            "useless_gear_tier_8",
            () -> new Item(new Item.Properties().rarity(RarityExtension.MYTHIC.getValue()))
    );
    public static final DeferredItem<Item> USELESS_GEAR_TIER_9 = registerAndAdd(
            "useless_gear_tier_9",
            () -> new Item(new Item.Properties().rarity(RarityExtension.MYTHIC.getValue()))
    );
    // 无用玻璃方块物品
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_1 = registerAndAdd(
            "useless_glass_tier_1",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_1.get(), new Item.Properties().rarity(Rarity.COMMON))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_2 = registerAndAdd(
            "useless_glass_tier_2",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_2.get(), new Item.Properties().rarity(Rarity.COMMON))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_3 = registerAndAdd(
            "useless_glass_tier_3",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_3.get(), new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_4 = registerAndAdd(
            "useless_glass_tier_4",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_4.get(), new Item.Properties().rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_5 = registerAndAdd(
            "useless_glass_tier_5",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_5.get(), new Item.Properties().rarity(Rarity.RARE))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_6 = registerAndAdd(
            "useless_glass_tier_6",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_6.get(), new Item.Properties().rarity(Rarity.RARE))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_7 = registerAndAdd(
            "useless_glass_tier_7",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_7.get(), new Item.Properties().rarity(Rarity.EPIC))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_8 = registerAndAdd(
            "useless_glass_tier_8",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_8.get(), new Item.Properties().rarity(RarityExtension.MYTHIC.getValue()))
    );
    public static final DeferredItem<BlockItem> USELESS_GLASS_TIER_9 = registerAndAdd(
            "useless_glass_tier_9",
            () -> new BlockItem(ModBlocks.USELESS_GLASS_TIER_9.get(), new Item.Properties().rarity(RarityExtension.LEGENDARY.getValue()))
    );
    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM = registerAndAdd(
            "teleport_block",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM_2 = registerAndAdd(
            "teleport_block_2",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK_2.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM_3 = registerAndAdd(
            "teleport_block_3",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK_3.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> ORE_GENERATOR_BLOCK = registerAndAdd(
            "ore_generator_block",
            () -> new BlockItem(ModBlocks.ORE_GENERATOR_BLOCK.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> ADVANCED_ALLOY_FURNACE_BLOCK = registerAndAdd(
            "advanced_alloy_furnace_block",
            () -> new BlockItem(ModBlocks.ADVANCED_ALLOY_FURNACE_BLOCK.get(), new Item.Properties())
    );

    private ModItems() {}

    private static <T extends Item> DeferredItem<T> registerAndAdd(String name, Supplier<T> supplier) {
        DeferredItem<T> deferred = ITEMS.register(name, supplier);
        CREATIVE_MAIN_TAB_ITEMS.add(deferred);
        return deferred;
    }
}
