package com.sorrowmist.useless.content.menus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionConfigGhostSlotTest {
    @Test
    void ghostSlotCopiesBlocksWithoutTakingTheSourceStack() {
        DimensionConfigMenu.GhostSlot slot = new DimensionConfigMenu.GhostSlot(
                ResourceLocation.withDefaultNamespace("stone"), 0, 0);
        ItemStack source = new ItemStack(Items.DIRT, 12);

        slot.set(source);

        assertEquals(12, source.getCount());
        assertTrue(slot.hasItem());
        assertEquals(Items.DIRT, slot.getItem().getItem());
        assertEquals(1, slot.getItem().getCount());
        assertTrue(slot.remove(1).isEmpty());
        assertFalse(slot.mayPickup(null));
    }

    @Test
    void ghostSlotRejectsNonBlockItems() {
        DimensionConfigMenu.GhostSlot slot = new DimensionConfigMenu.GhostSlot(
                ResourceLocation.withDefaultNamespace("stone"), 0, 0);

        assertFalse(slot.mayPlace(new ItemStack(Items.DIAMOND)));
    }
}
