package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

/** 紧凑 F9 对外的流体接口：填充进入冷却液输入仓，抽取来自冷却副产物输出仓。 */
public final class CompactF9FluidHandler implements IFluidHandler {

    private final FluidTank input;
    private final FluidTank output;

    public CompactF9FluidHandler(FluidTank input, FluidTank output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public int getTanks() {
        return 2;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        return switch (tank) {
            case 0 -> input.getFluid();
            case 1 -> output.getFluid();
            default -> FluidStack.EMPTY;
        };
    }

    @Override
    public int getTankCapacity(int tank) {
        return switch (tank) {
            case 0 -> input.getCapacity();
            case 1 -> output.getCapacity();
            default -> 0;
        };
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        return switch (tank) {
            case 0 -> input.isFluidValid(stack);
            case 1 -> output.isFluidValid(stack);
            default -> false;
        };
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return input.fill(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return output.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        return output.drain(maxDrain, action);
    }
}
