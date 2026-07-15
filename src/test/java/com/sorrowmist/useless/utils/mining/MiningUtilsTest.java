package com.sorrowmist.useless.utils.mining;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class MiningUtilsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void keepsRedstoneDustEvenThoughItIsBlockItem() {
        assertInstanceOf(BlockItem.class, Items.REDSTONE);

        List<ItemStack> naturalDrops = List.of(new ItemStack(Items.REDSTONE, 4));
        List<ItemStack> actualDrops = List.of(new ItemStack(Items.REDSTONE, 6));
        List<ItemStack> fallbackDrops = List.of(new ItemStack(Blocks.REDSTONE_ORE));

        assertSame(actualDrops, MiningUtils.selectForcedDrops(naturalDrops, actualDrops, fallbackDrops));
    }

    @Test
    void keepsOtherNonEmptyBlockItemDrops() {
        assertInstanceOf(BlockItem.class, Items.COBBLESTONE);

        List<ItemStack> naturalDrops = List.of(new ItemStack(Items.COBBLESTONE));
        List<ItemStack> actualDrops = List.of(new ItemStack(Items.COBBLESTONE));
        List<ItemStack> fallbackDrops = List.of(new ItemStack(Blocks.STONE));

        assertSame(actualDrops, MiningUtils.selectForcedDrops(naturalDrops, actualDrops, fallbackDrops));
    }

    @Test
    void usesFallbackWhenNaturalAndActualDropsAreEmpty() {
        List<ItemStack> fallbackDrops = List.of(new ItemStack(Blocks.BEDROCK));

        assertSame(fallbackDrops, MiningUtils.selectForcedDrops(List.of(), List.of(), fallbackDrops));
    }

    @Test
    void keepsActualDropsWhenLootTableWasEmpty() {
        List<ItemStack> actualDrops = List.of(new ItemStack(Items.DIAMOND));
        List<ItemStack> fallbackDrops = List.of(new ItemStack(Blocks.DIAMOND_ORE));

        assertSame(actualDrops, MiningUtils.selectForcedDrops(List.of(), actualDrops, fallbackDrops));
    }
}
