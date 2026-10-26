package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.upgrades.UpgradeInventories;
import cn.dancingsnow.neoecoae.config.NEConfig;
import com.sorrowmist.useless.InventoryTestBootstrap;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowFluidInputHatchBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs unchanged against both the declared ECO dependency and the reported runtime jar. */
class CompactF9InputTankCompatTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void nativeTankIsSharedAndCoolingMutationsDirtyTheRealHost() throws Exception {
        var source = createHatch();
        var nativeTank = source.sharedTank();
        var changes = new AtomicInteger();
        source.initializeSharedTank(128_000, changes::incrementAndGet);
        var otherHost = createHatch();
        otherHost.shareTankWith(source);
        var handler = new CompactF9FluidHandler(nativeTank,
                CompactF9FluidHandler.createFleetTank(8, changes::incrementAndGet));

        assertSame(nativeTank, source.sharedTank());
        assertSame(nativeTank, otherHost.sharedTank());
        assertEquals(128_000, handler.getTankCapacity(0));
        var water = new FluidStack(Fluids.WATER, 200_000);
        assertEquals(128_000, handler.fill(water, FluidAction.SIMULATE));
        assertEquals(0, changes.get());
        assertEquals(0, nativeTank.getFluidAmount());
        assertEquals(128_000, handler.fill(water, FluidAction.EXECUTE));
        assertEquals(200_000, water.getAmount());
        assertEquals(1, changes.get());
        assertEquals(0, handler.fill(water, FluidAction.EXECUTE));
        assertEquals(1, changes.get());
        assertEquals(100, otherHost.sharedTank().drain(100, FluidAction.EXECUTE).getAmount());
        assertEquals(127_900, nativeTank.getFluidAmount());
        assertEquals(2, changes.get());
        // On modern ECO, also check its independent long counter, not just the int view.
        try {
            assertEquals(127_900L, nativeTank.getClass().getMethod("getStoredAmount").invoke(nativeTank));
        } catch (NoSuchMethodException legacyEco) {
            assertEquals(128_000, nativeTank.getCapacity());
        }
    }

    @Test
    void nativeTankRoundTripsAndAcceptsLegacyFluidNbt() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var source = createHatch();
        source.initializeSharedTank(128_000, () -> {});
        var handler = new CompactF9FluidHandler(source.sharedTank(),
                CompactF9FluidHandler.createFleetTank(8, () -> {}));
        handler.fill(new FluidStack(Fluids.WATER, 42_000), FluidAction.EXECUTE);
        var saved = source.sharedTank().writeToNBT(registries, new CompoundTag());
        var restored = createHatch();
        restored.initializeSharedTank(128_000, () -> {});
        restored.sharedTank().readFromNBT(registries, saved);
        assertEquals(42_000, restored.sharedTank().getFluidAmount());
        assertEquals(42_000, restored.sharedTank().drain(50_000, FluidAction.SIMULATE).getAmount());

        saved.remove("StoredAmount");
        restored.sharedTank().readFromNBT(registries, saved);
        assertEquals(42_000, restored.sharedTank().getFluidAmount());
        assertEquals(42_000, restored.sharedTank().drain(50_000, FluidAction.EXECUTE).getAmount());
        assertTrue(restored.sharedTank().isEmpty());
    }

    private static ShadowFluidInputHatchBlockEntity createHatch() {
        try (var grid = mockStatic(GridHelper.class);
             var config = mockStatic(NEConfig.class);
             var upgrades = mockStatic(UpgradeInventories.class, RETURNS_MOCKS)) {
            grid.when(() -> GridHelper.createManagedNode(any(), any()))
                    .thenReturn(mock(IManagedGridNode.class, RETURNS_SELF));
            var type = mock(BlockEntityType.class);
            when(type.isValid(any())).thenReturn(true);
            return new ShadowFluidInputHatchBlockEntity(type, BlockPos.ZERO, Blocks.AIR.defaultBlockState());
        }
    }
}
