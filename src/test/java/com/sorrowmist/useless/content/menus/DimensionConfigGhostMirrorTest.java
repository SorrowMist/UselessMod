package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DimensionConfigGhostMirrorTest {
    @BeforeAll
    static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void pendingServerMirrorDoesNotRejectLocallyEnabledSlotsUsingItsStaleMode() {
        var menu = mock(DimensionConfigMenu.class, CALLS_REAL_METHODS);
        var slot = new DimensionConfigMenu.GhostSlot(new ResourceLocation("minecraft:dirt"), 0, 0);
        doReturn(slot).when(menu).getGhostSlot(DimensionConfigMenu.ROAD_A_SLOT);
        doReturn(false).when(menu).isGhostSlotActive(DimensionConfigMenu.ROAD_A_SLOT);
        try (var policy = mockStatic(com.sorrowmist.useless.world.dimension.DimensionGenerationConfig.class)) {
            policy.when(() -> com.sorrowmist.useless.world.dimension.DimensionGenerationConfig.isValidBlockId(
                    org.mockito.ArgumentMatchers.any())).thenCallRealMethod();
            policy.when(() -> com.sorrowmist.useless.world.dimension.DimensionGenerationConfig.isAllowedBlockId(
                    new ResourceLocation("minecraft:stone"))).thenReturn(true);
            assertTrue(menu.setGhostBlockId(DimensionConfigMenu.ROAD_A_SLOT, new ResourceLocation("minecraft:stone")));
            assertTrue(slot.getItem().is(Items.STONE));
            verify(menu, never()).isGhostSlotActive(DimensionConfigMenu.ROAD_A_SLOT);
        }
    }

    @Test
    void serverMirrorStillRejectsUnknownSlotsAirAndDisallowedBlocks() {
        var menu = mock(DimensionConfigMenu.class, CALLS_REAL_METHODS);
        var slot = new DimensionConfigMenu.GhostSlot(new ResourceLocation("minecraft:dirt"), 0, 0);
        doReturn(slot).when(menu).getGhostSlot(DimensionConfigMenu.BORDER_SLOT);
        doReturn(null).when(menu).getGhostSlot(-1);
        doReturn(null).when(menu).getGhostSlot(9);
        assertFalse(menu.setGhostBlockId(-1, new ResourceLocation("minecraft:stone")));
        assertFalse(menu.setGhostBlockId(9, new ResourceLocation("minecraft:stone")));
        assertFalse(menu.setGhostBlockId(0, new ResourceLocation("minecraft:air")));
        try (var policy = mockStatic(com.sorrowmist.useless.world.dimension.DimensionGenerationConfig.class)) {
            policy.when(() -> com.sorrowmist.useless.world.dimension.DimensionGenerationConfig.isAllowedBlockId(
                    new ResourceLocation("minecraft:stone"))).thenReturn(false);
            assertFalse(menu.setGhostBlockId(0, new ResourceLocation("minecraft:stone")));
        }
        assertTrue(slot.getItem().is(Items.DIRT));
        assertTrue(menu.setGhostBlockId(0, null));
        assertTrue(slot.getItem().isEmpty());
    }
}
