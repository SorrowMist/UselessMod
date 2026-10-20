package com.sorrowmist.useless.compat.forge1201.capability;

import com.sorrowmist.useless.compat.forge1201.RecipeTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NativeCapabilityTest {
    @BeforeAll static void bootstrap() { RecipeTestBootstrap.initialize(); }
    // Forge capability constants require launcher transformers, unavailable in this headless test source set.
    @SuppressWarnings("unchecked") private static <T> Capability<T> capability() {
        return (Capability<T>) mock(Capability.class);
    }
    @SuppressWarnings("unchecked") private static BlockEntityType<BlockEntity> type() {
        return (BlockEntityType<BlockEntity>) mock(BlockEntityType.class);
    }
    private static BlockEntity entity(BlockEntityType<BlockEntity> type) {
        BlockEntity entity=mock(BlockEntity.class); doReturn(type).when(entity).getType(); return entity;
    }

    @Test void capabilityTokenPreservesSidedAndUnsidedAccess() {
        var registry=new BlockCapabilityRegistry(); var type=type(); var entity=entity(type);
        Capability<IEnergyStorage> energyCapability=capability();
        Capability<Object> otherCapability=capability();
        var energy=new EnergyStorage(10000);
        registry.register(energyCapability,type,(be,side)->side==Direction.UP||side==null?energy:null);
        var provider=registry.provider(entity); assertNotNull(provider);
        var up=provider.getCapability(energyCapability,Direction.UP);
        assertSame(energy,up.orElseThrow(IllegalStateException::new));
        assertSame(up,provider.getCapability(energyCapability,Direction.UP));
        assertSame(energy,provider.getCapability(energyCapability,null).orElseThrow(IllegalStateException::new));
        assertFalse(provider.getCapability(energyCapability,Direction.DOWN).isPresent());
        assertFalse(provider.getCapability(otherCapability,null).isPresent());
        assertEquals(1200,up.orElseThrow(IllegalStateException::new).receiveEnergy(1200,false)); assertEquals(1200,energy.getEnergyStored());
    }

    @Test void withdrawingAndReenablingAConfigurableSideInvalidatesOldView() {
        var registry=new BlockCapabilityRegistry(); var type=type(); var entity=entity(type);
        Capability<IEnergyStorage> energyCapability=capability();
        var active=new AtomicReference<IEnergyStorage>(new EnergyStorage(1000));
        registry.register(energyCapability,type,(be,side)->active.get());
        var provider=registry.provider(entity); var old=provider.getCapability(energyCapability,Direction.NORTH);
        var invalidated=new AtomicBoolean(); old.addListener(ignored->invalidated.set(true));
        active.set(null);
        assertFalse(provider.getCapability(energyCapability,Direction.NORTH).isPresent());
        assertTrue(invalidated.get()); assertFalse(old.isPresent());
        var next=new EnergyStorage(2000); active.set(next);
        assertSame(next,provider.getCapability(energyCapability,Direction.NORTH).orElseThrow(IllegalStateException::new));
    }

    @Test void unloadInvalidatesAllResolvedOptionsAndRemovalPreventsTransfers() {
        var registry=new BlockCapabilityRegistry(); var type=type(); var entity=entity(type);
        Capability<IEnergyStorage> energyCapability=capability();
        registry.register(energyCapability,type,(be,side)->new EnergyStorage(1000));
        var provider=registry.provider(entity);
        var up=provider.getCapability(energyCapability,Direction.UP);
        var down=provider.getCapability(energyCapability,Direction.DOWN);
        assertTrue(up.isPresent()); assertTrue(down.isPresent());
        provider.invalidate(); assertFalse(up.isPresent()); assertFalse(down.isPresent());
        when(entity.isRemoved()).thenReturn(true);
        assertFalse(provider.getCapability(energyCapability,null).isPresent());
    }

    @Test void queryDoesNotLoadChunksAndPreservesRequestedSide() {
        Capability<IEnergyStorage> energyCapability=capability();
        var level=mock(Level.class); var entity=mock(BlockEntity.class); var pos=new BlockPos(42,64,7);
        assertNull(CapabilityAccess.get(level,energyCapability,pos,Direction.WEST));
        verify(level,never()).getBlockEntity(any());
        when(level.isLoaded(pos)).thenReturn(true); when(level.getBlockEntity(pos)).thenReturn(entity);
        var energy=new EnergyStorage(1000);
        when(entity.getCapability(energyCapability,Direction.WEST)).thenReturn(LazyOptional.of(()->energy));
        assertSame(energy,CapabilityAccess.get(level,energyCapability,pos,Direction.WEST));
        when(entity.isRemoved()).thenReturn(true);
        assertNull(CapabilityAccess.get(level,energyCapability,pos,Direction.WEST));
    }

    @Test void staleBlockEntityFromBeforeReplacementIsNeverUsed() {
        Capability<IEnergyStorage> energyCapability=capability();
        var level=mock(Level.class); var pos=BlockPos.ZERO;
        when(level.isLoaded(pos)).thenReturn(true);
        var stale=mock(BlockEntity.class); var current=mock(BlockEntity.class);
        when(level.getBlockEntity(pos)).thenReturn(current);
        var energy=new EnergyStorage(1000);
        when(current.getCapability(energyCapability,null)).thenReturn(LazyOptional.of(()->energy));
        assertSame(energy,CapabilityAccess.get(level,energyCapability,pos,null,stale,null));
        verify(stale,never()).getCapability(any(),any());
    }

    @Test void duplicateRegistrationsFailRatherThanSilentlyChoosingAnotherHandler() {
        var registry=new BlockCapabilityRegistry(); var type=type();
        Capability<IEnergyStorage> energyCapability=capability();
        registry.register(energyCapability,type,(be,side)->new EnergyStorage(1));
        assertThrows(IllegalStateException.class,()->registry.register(energyCapability,type,(be,side)->new EnergyStorage(2)));
        assertNull(registry.provider(entity(type())));
    }
}
