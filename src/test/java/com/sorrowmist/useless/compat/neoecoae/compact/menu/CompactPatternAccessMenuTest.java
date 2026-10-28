package com.sorrowmist.useless.compat.neoecoae.compact.menu;

import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.neoecoae.compact.entity.CompactF9BlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompactPatternAccessMenuTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    private record Fixture(Player player, Level level, CompactF9BlockEntity host, BlockPos pos) {}

    private static Fixture fixture(double distanceSquared) {
        var player = mock(Player.class);
        var level = mock(Level.class);
        var host = mock(CompactF9BlockEntity.class);
        var pos = new BlockPos(10, 64, 20);
        when(player.level()).thenReturn(level);
        when(host.getLevel()).thenReturn(level);
        when(host.getBlockPos()).thenReturn(pos);
        when(level.hasChunkAt(pos)).thenReturn(true);
        when(level.getBlockEntity(pos)).thenReturn(host);
        when(player.distanceToSqr(10.5, 64.5, 20.5)).thenReturn(distanceSquared);
        return new Fixture(player, level, host, pos);
    }

    @Test
    void allowsTheOriginalHostAtTheNativeEightBlockBoundary() {
        var f = fixture(64.0);
        assertTrue(CompactPatternAccessMenu.canAccess(f.player(), f.host()));
    }

    @Test
    void rejectsAnOutOfRangeViewer() {
        var f = fixture(64.001);
        assertFalse(CompactPatternAccessMenu.canAccess(f.player(), f.host()));
    }

    @Test
    void rejectsAReplacedOrRemovedHost() {
        var f = fixture(1.0);
        when(f.level().getBlockEntity(f.pos())).thenReturn(mock(CompactF9BlockEntity.class));
        assertFalse(CompactPatternAccessMenu.canAccess(f.player(), f.host()));
        when(f.level().getBlockEntity(f.pos())).thenReturn(f.host());
        when(f.host().isRemoved()).thenReturn(true);
        assertFalse(CompactPatternAccessMenu.canAccess(f.player(), f.host()));
    }

    @Test
    void rejectsADifferentDimensionOrUnloadedChunk() {
        var f = fixture(1.0);
        when(f.host().getLevel()).thenReturn(mock(Level.class));
        assertFalse(CompactPatternAccessMenu.canAccess(f.player(), f.host()));
        when(f.host().getLevel()).thenReturn(f.level());
        when(f.level().hasChunkAt(f.pos())).thenReturn(false);
        assertFalse(CompactPatternAccessMenu.canAccess(f.player(), f.host()));
    }
}
