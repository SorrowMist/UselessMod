package com.sorrowmist.useless.content.menus;

import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FurnaceMenuAccessTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void controlPacketsRequireLoadedNearbyMatchingOpenMachine() {
        var player = mock(ServerPlayer.class);
        var level = mock(ServerLevel.class);
        var menu = mock(MultiblockAlloyFurnaceMenu.class);
        var pos = new BlockPos(1, 2, 3);
        player.containerMenu = menu;
        when(player.level()).thenReturn(level);
        when(level.hasChunkAt(pos)).thenReturn(true);
        when(menu.stillValid(player)).thenReturn(true);
        when(menu.getBlockPos()).thenReturn(pos);
        assertTrue(FurnaceMenuAccess.canUse(player, pos));
        when(player.isSpectator()).thenReturn(true);
        assertFalse(FurnaceMenuAccess.canUse(player, pos));
        when(player.isSpectator()).thenReturn(false);
        when(level.hasChunkAt(pos)).thenReturn(false);
        assertFalse(FurnaceMenuAccess.canUse(player, pos));
        when(level.hasChunkAt(pos)).thenReturn(true);
        when(player.distanceToSqr(1.5D, 2.5D, 3.5D)).thenReturn(65.0D);
        assertFalse(FurnaceMenuAccess.canUse(player, pos));
        when(player.distanceToSqr(1.5D, 2.5D, 3.5D)).thenReturn(0.0D);
        when(menu.stillValid(player)).thenReturn(false);
        assertFalse(FurnaceMenuAccess.canUse(player, pos));
        when(menu.stillValid(player)).thenReturn(true);
        when(menu.getBlockPos()).thenReturn(pos.above());
        assertFalse(FurnaceMenuAccess.canUse(player, pos));
        player.containerMenu = mock(AbstractContainerMenu.class);
        when(player.containerMenu.stillValid(player)).thenReturn(true);
        assertFalse(FurnaceMenuAccess.canUse(player, pos));
        assertFalse(FurnaceMenuAccess.canUse(null, pos));
    }
}
