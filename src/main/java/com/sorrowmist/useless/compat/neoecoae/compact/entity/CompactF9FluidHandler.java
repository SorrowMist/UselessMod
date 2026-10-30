package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;

/** 紧凑 F9 对外的流体接口：填充进入冷却液输入仓，抽取来自冷却副产物输出仓。 */
public final class CompactF9FluidHandler implements IFluidHandler {

    static FluidTank createFleetTank(int hosts, Runnable contentsChanged) {
        return new FluidTank(Math.multiplyExact(16_000, hosts)) {
            @Override
            protected void onContentsChanged() {
                contentsChanged.run();
            }
        };
    }

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
        // Native long tanks can accept more than their int capacity; keep the fleet's
        // original bounded pool of eight 16,000 mB input hatches.
        int remaining = Math.max(0, input.getCapacity() - input.getFluidAmount());
        int accepted = Math.min(resource.getAmount(), remaining);
        if (accepted <= 0) {
            return 0;
        }
        FluidStack limited = resource.copy();
        limited.setAmount(accepted);
        return input.fill(limited, action);
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        return output.drain(resource, action);
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        return output.drain(maxDrain, action);
    }

    /** Player-only recovery view for coolant accidentally left in the input pool. */
    public IFluidHandler inputRecoveryHandler() {
        return new IFluidHandler() {
            @Override
            public int getTanks() { return 1; }

            @Override
            public @NotNull FluidStack getFluidInTank(int tank) {
                return tank == 0 ? input.getFluid() : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return tank == 0 ? input.getCapacity() : 0;
            }

            @Override
            public boolean isFluidValid(int tank, @NotNull FluidStack stack) { return false; }

            @Override
            public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) { return 0; }

            @Override
            public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
                return input.drain(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
                return input.drain(maxDrain, action);
            }
        };
    }
}
