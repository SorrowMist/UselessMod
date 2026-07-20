package com.sorrowmist.useless.utils.mining;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void acceptsBlocksThatDoNotRequireTheCorrectTool() {
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        var glowstone = Blocks.GLOWSTONE.defaultBlockState();

        assertFalse(glowstone.requiresCorrectToolForDrops());
        assertFalse(pickaxe.isCorrectToolForDrops(glowstone));
        assertTrue(MiningUtils.canMineBlock(glowstone, pickaxe, false));
    }

    @Test
    void keepsCorrectToolRequirementUnlessMiningIsForced() {
        var obsidian = Blocks.OBSIDIAN.defaultBlockState();
        ItemStack woodenPickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        ItemStack explicitCorrectTool = new ItemStack(Items.STICK);
        explicitCorrectTool.set(DataComponents.TOOL, new Tool(
                List.of(Tool.Rule.minesAndDrops(List.of(Blocks.OBSIDIAN), 1.0F)), 1.0F, 0));

        assertTrue(obsidian.requiresCorrectToolForDrops());
        assertFalse(MiningUtils.canMineBlock(obsidian, woodenPickaxe, false));
        assertTrue(MiningUtils.canMineBlock(obsidian, explicitCorrectTool, false));
        assertTrue(MiningUtils.canMineBlock(obsidian, woodenPickaxe, true));
    }
}
