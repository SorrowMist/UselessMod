package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.init.ModItems;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class IngotItemTest {
    @Test
    void usefulIngotTargetsTheLongEnergyFurnaceTier() {
        IngotItem usefulIngot = assertInstanceOf(IngotItem.class, ModItems.USEFUL_INGOT.get());

        assertEquals(AdvancedAlloyFurnaceBlockEntity.USEFUL_INGOT_FURNACE_TIER,
                usefulIngot.getFurnaceTier());
        assertEquals(9, assertInstanceOf(IngotItem.class,
                ModItems.USELESS_INGOT_TIER_9.get()).getFurnaceTier());
    }
}
