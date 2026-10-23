package com.sorrowmist.useless.compat.ae;

import appeng.api.networking.IInWorldGridNodeHost;
import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AeHostLookupTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void nativeInterfaceResolvesWithoutRequiringACapability() {
        var level = mock(Level.class);
        var pos = BlockPos.ZERO;
        when(level.isLoaded(pos)).thenReturn(true);
        var nativeMachine = mock(BlockEntity.class, withSettings().extraInterfaces(IInWorldGridNodeHost.class));
        when(level.getBlockEntity(pos)).thenReturn(nativeMachine);
        assertSame(nativeMachine, AeNetworks.host(level, pos));
        verify(nativeMachine, never()).getCapability(any(), any());
    }

    @Test void unloadedAndRemovedMachinesCannotBecomeEndpoints() {
        var level = mock(Level.class);
        assertNull(AeNetworks.host(level, BlockPos.ZERO));
        verify(level, never()).getBlockEntity(any());
        when(level.isLoaded(BlockPos.ZERO)).thenReturn(true);
        var removed = mock(BlockEntity.class, withSettings().extraInterfaces(IInWorldGridNodeHost.class));
        when(removed.isRemoved()).thenReturn(true);
        when(level.getBlockEntity(BlockPos.ZERO)).thenReturn(removed);
        assertNull(AeNetworks.host(level, BlockPos.ZERO));
        assertNull(AeNetworks.host(null, BlockPos.ZERO));
        assertNull(AeNetworks.host(level, null));
    }
}
