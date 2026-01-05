package com.sorrowmist.useless.init;


import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.items.EndlessBeafItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomModelData;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UselessMod.MODID);

    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM = ITEMS.register(
            "teleport_block",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK.get(), new Item.Properties())
    );

    static final DeferredItem<BlockItem> TELEPORT_BLOCK_ITEM_2 = ITEMS.register(
            "teleport_block_2",
            () -> new BlockItem(ModBlocks.TELEPORT_BLOCK_2.get(), new Item.Properties())
    );

    static final DeferredItem<EndlessBeafItem> ENDLESS_BEAF_ITEM = ITEMS.register(
            "endless_beaf_item",
            () -> {
                // 创建物品
                return new EndlessBeafItem(
                        Tiers.NETHERITE,
                        new Item.Properties()
                                .attributes(DiggerItem.createAttributes(Tiers.NETHERITE, 50, 2.0F))
                                .stacksTo(1)
                                .rarity(Rarity.EPIC)
                                .durability(0)
                                .component(UComponents.EnchantModeComponent.get(), EnchantMode.SILK_TOUCH)
                                .component(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1))
                );
            }
    );
    static final DeferredItem<BlockItem> ORE_GENERATOR_BLOCK = ITEMS.register(
            "ore_generator_block",
            () -> new BlockItem(ModBlocks.ORE_GENERATOR_BLOCK.get(), new Item.Properties())
    );

    private ModItems() {}
}
