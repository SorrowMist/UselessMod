package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;

/**
 * 单向流体处理器：包装一组流体槽，可选择是否允许填充。
 */
public record FurnaceFluidTankHandler(FluidTank[] tanks, boolean allowFill) implements IFluidHandler {

    @Override
    public int getTanks() {
        return FLUID_TANK_COUNT;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return this.tanks[tank].getFluid();
    }

    @Override
    public int getTankCapacity(int tank) {
        return this.tanks[tank].getCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return this.tanks[tank].isFluidValid(stack);
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        if (!this.allowFill) return 0;
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidTank tank = this.tanks[i];
            if (tank.isFluidValid(resource)) {
                if (tank.getFluid().isEmpty()
                        || FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                    int filled = tank.fill(resource, action);
                    if (filled > 0) return filled;
                }
            }
        }
        return 0;
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack drained = this.tanks[i].drain(resource, action);
            if (!drained.isEmpty()) return drained;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            if (!this.tanks[i].getFluid().isEmpty()) {
                return this.tanks[i].drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }
}
