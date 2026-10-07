package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import com.sorrowmist.useless.InventoryTestBootstrap;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CompactF9FluidHandlerTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void eightHostCapacityAndSimulationDoNotDirtyOrConsumeFluid() {
        var changes = new AtomicInteger();
        var input = CompactF9FluidHandler.createFleetTank(8, changes::incrementAndGet);
        var output = CompactF9FluidHandler.createFleetTank(8, changes::incrementAndGet);
        var handler = new CompactF9FluidHandler(input, output);
        FluidStack water = new FluidStack(Fluids.WATER, 200_000);
        assertEquals(128_000, handler.getTankCapacity(0));
        assertEquals(128_000, handler.getTankCapacity(1));
        assertEquals(128_000, handler.fill(water, FluidAction.SIMULATE));
        assertEquals(0, input.getFluidAmount());
        assertEquals(0, changes.get());
        assertEquals(128_000, handler.fill(water, FluidAction.EXECUTE));
        assertEquals(128_000, input.getFluidAmount());
        assertEquals(1, changes.get());
        assertTrue(handler.drain(1000, FluidAction.EXECUTE).isEmpty());
        assertEquals(128_000, input.getFluidAmount());
    }

    @Test
    void outputExtractionSharesThePoolUsedByCoolingAndMarksActualChanges() {
        var changes = new AtomicInteger();
        var input = CompactF9FluidHandler.createFleetTank(8, changes::incrementAndGet);
        var output = CompactF9FluidHandler.createFleetTank(8, changes::incrementAndGet);
        var handler = new CompactF9FluidHandler(input, output);
        output.fill(new FluidStack(Fluids.WATER, 64_000), FluidAction.EXECUTE);
        int before = changes.get();
        assertEquals(32_000, handler.drain(32_000, FluidAction.SIMULATE).getAmount());
        assertEquals(64_000, output.getFluidAmount());
        assertEquals(before, changes.get());
        assertEquals(32_000, handler.drain(new FluidStack(Fluids.WATER, 32_000), FluidAction.EXECUTE).getAmount());
        assertEquals(32_000, output.getFluidAmount());
        assertEquals(before + 1, changes.get());
        assertEquals(0, input.getFluidAmount());
    }
}
