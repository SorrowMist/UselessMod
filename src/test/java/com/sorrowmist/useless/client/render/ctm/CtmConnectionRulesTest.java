package com.sorrowmist.useless.client.render.ctm;

import com.sorrowmist.useless.init.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CtmConnectionRulesTest {
    @Test
    void furnacePartsConnectAcrossBlockTypes() {
        Block core = ModBlocks.MULTIBLOCK_ALLOY_FURNACE_CORE.get();
        Block casing = ModBlocks.OMNIVERSAL_FURNACE_CASING.get();
        Block functionalPart = ModBlocks.ME_PATTERN_ASSEMBLY.get();

        assertTrue(CtmConnectionRules.connects(
                core.defaultBlockState(), casing.defaultBlockState()));
        assertTrue(CtmConnectionRules.connects(
                casing.defaultBlockState(), functionalPart.defaultBlockState()));
    }

    @Test
    void unrelatedBlocksDoNotConnectToFurnaceParts() {
        Block casing = ModBlocks.OMNIVERSAL_FURNACE_CASING.get();
        Block unrelated = Blocks.STONE;
        assertFalse(CtmConnectionRules.connects(
                casing.defaultBlockState(), unrelated.defaultBlockState()));
    }

    @Test
    void coilsConnectOnlyWithinTheSameTier() {
        Block tierOneA = ModBlocks.USELESS_COILS.get(1).get();
        Block tierOneB = ModBlocks.USELESS_COILS.get(1).get();
        Block tierTwo = ModBlocks.USELESS_COILS.get(2).get();

        assertTrue(CtmConnectionRules.connects(
                tierOneA.defaultBlockState(), tierOneB.defaultBlockState()));
        assertFalse(CtmConnectionRules.connects(
                tierOneA.defaultBlockState(), tierTwo.defaultBlockState()));
    }
}
