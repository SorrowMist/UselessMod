package com.sorrowmist.useless.content.recipe.adapters.anvilcraft;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.content.recipe.ExpectedOutputScaler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilExpectedOutputTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void independentProbabilitiesUseACommonBatch() {
        var result = ExpectedOutputScaler.scale(List.of(
                new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(Items.STONE), 1, 1, 0.5),
                new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(Items.GRAVEL), 1, 1, 0.25)))
                .orElseThrow();

        assertEquals(4, result.operations());
        assertEquals(2, result.outputs().get(0).getCount());
        assertEquals(1, result.outputs().get(1).getCount());
        assertTrue(result.outputs().get(0).is(Items.STONE));
        assertTrue(result.outputs().get(1).is(Items.GRAVEL));
    }

    @Test
    void squeezingProducesExpectedFluidAmountWithoutUnnecessaryBatching() {
        var result = ExpectedOutputScaler.scale(List.of(
                new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(Items.STRUCTURE_VOID), 250, 250, 0.1f)))
                .orElseThrow();

        assertEquals(1, result.operations());
        assertEquals(25, result.outputs().getFirst().getCount());
    }

    @Test
    void failedSmearingReturnsTheUnchangedInput() {
        var result = ExpectedOutputScaler.scale(List.of(
                new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(Items.MOSSY_COBBLESTONE), 1, 1, 0.25),
                new ExpectedOutputScaler.WeightedItemOutput(new ItemStack(Items.COBBLESTONE), 1, 1, 0.75)))
                .orElseThrow();

        assertEquals(4, result.operations());
        assertEquals(1, result.outputs().get(0).getCount());
        assertEquals(3, result.outputs().get(1).getCount());
    }
}
