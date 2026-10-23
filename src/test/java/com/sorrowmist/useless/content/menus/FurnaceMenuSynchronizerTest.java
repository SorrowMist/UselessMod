package com.sorrowmist.useless.content.menus;

import net.minecraft.world.inventory.SimpleContainerData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.sorrowmist.useless.InventoryTestBootstrap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnaceMenuSynchronizerTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @Test void combinedPageAndHatchDataUsesFullIntsAndRejectsWrongShapeWithoutMutation() {
        var pages = new SimpleContainerData(3);
        var hatch = new SimpleContainerData(2);
        var sync = new FurnaceMenuSynchronizer();
        sync.addData(pages); sync.addData(hatch);
        assertTrue(sync.applyData(List.of(2, 50, 49, Integer.MAX_VALUE, 100000)));
        assertEquals(2, pages.get(0));
        assertEquals(Integer.MAX_VALUE, hatch.get(0));
        assertEquals(100000, hatch.get(1));
        assertFalse(sync.applyData(List.of(9, 10)));
        assertEquals(2, pages.get(0));
        assertEquals(Integer.MAX_VALUE, hatch.get(0));
        assertTrue(sync.applyData(List.of()));
    }
}
