package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;

/**
 * 复合流体处理器：同时暴露输入槽与输出槽。
 * 填充仅作用于输入槽，抽取优先从输出槽进行。
 */
public record FurnaceCombinedFluidTankHandler(FluidTank[] inputTanks, FluidTank[] outputTanks) implements IFluidHandler {
    @Override
    public int getTanks() {
        return FLUID_TANK_COUNT * 2;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        if (tank < FLUID_TANK_COUNT) {
            return this.inputTanks[tank].getFluid();
        } else if (tank < FLUID_TANK_COUNT * 2) {
            return this.outputTanks[tank - FLUID_TANK_COUNT].getFluid();
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank < FLUID_TANK_COUNT) {
            return this.inputTanks[tank].getCapacity();
        } else if (tank < FLUID_TANK_COUNT * 2) {
            return this.outputTanks[tank - FLUID_TANK_COUNT].getCapacity();
        }
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        if (tank < FLUID_TANK_COUNT) {
            return this.inputTanks[tank].isFluidValid(stack);
        } else if (tank < FLUID_TANK_COUNT * 2) {
            return this.outputTanks[tank - FLUID_TANK_COUNT].isFluidValid(stack);
        }
        return false;
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return fillInput(inputTanks, resource, action);
    }

    /**
     * 静态辅助方法，用于 SidedFluidHandler 复用。
     */
    public static int fillInput(FluidTank[] inputTanks, FluidStack resource, FluidAction action) {
        for (FluidTank tank : inputTanks) {
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
            FluidStack drained = this.outputTanks[i].drain(resource, action);
            if (!drained.isEmpty()) return drained;
        }
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            FluidStack drained = this.inputTanks[i].drain(resource, action);
            if (!drained.isEmpty()) return drained;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            if (!this.outputTanks[i].getFluid().isEmpty()) {
                return this.outputTanks[i].drain(maxDrain, action);
            }
        }
        for (int i = 0; i < FLUID_TANK_COUNT; i++) {
            if (!this.inputTanks[i].getFluid().isEmpty()) {
                return this.inputTanks[i].drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }
}
