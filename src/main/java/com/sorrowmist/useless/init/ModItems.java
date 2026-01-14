package com.sorrowmist.useless.init;


import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.tool.ToolTypeMode;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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
    static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_ITEM = ITEMS.register(
            "endless_beaf_item",
            () -> new EndlessBeafItem()
    );
    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM = ITEMS.register(
            "teleport_block",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM_2 = ITEMS.register(
            "teleport_block_2",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK_2.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM_3 = ITEMS.register(
            "teleport_block_3",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK_3.get(), new Item.Properties())
    );
    static final DeferredItem<BlockItem> ORE_GENERATOR_BLOCK = ITEMS.register(
            "ore_generator_block",
            () -> new BlockItem(ModBlocks.ORE_GENERATOR_BLOCK.get(), new Item.Properties())
    );

    private ModItems() {}
}
