package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.api.enums.FurnaceFace;
import com.sorrowmist.useless.api.enums.FurnaceFaceMode;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout.FLUID_TANK_COUNT;

/**
 * 根据面模式限制的方向感知流体处理器。
 * <p>
 * 仅当对应面模式激活时才允许填充/抽取操作。
 */
public record FurnaceSidedFluidHandler(FluidTank[] inputTanks, FluidTank[] outputTanks,
                                       Direction side, FurnaceFaceAccessor owner) implements IFluidHandler {

    @Nullable
    private FurnaceFaceMode getMode() {
        FurnaceFace face = FurnaceFace.fromDirection(side, owner.getFacing());
        if (face == null) return FurnaceFaceMode.DISABLED;
        return owner.getFaceMode(face);
    }

    @Override
    public int getTanks() {
        return FLUID_TANK_COUNT * 2;
    }

    @Override
    public @NotNull FluidStack getFluidInTank(int tank) {
        if (tank < FLUID_TANK_COUNT) {
            return inputTanks[tank].getFluid();
        } else if (tank < FLUID_TANK_COUNT * 2) {
            return outputTanks[tank - FLUID_TANK_COUNT].getFluid();
        }
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        if (tank < FLUID_TANK_COUNT) {
            return inputTanks[tank].getCapacity();
        } else if (tank < FLUID_TANK_COUNT * 2) {
            return outputTanks[tank - FLUID_TANK_COUNT].getCapacity();
        }
        return 0;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
        if (tank < FLUID_TANK_COUNT) {
            return inputTanks[tank].isFluidValid(stack);
        } else if (tank < FLUID_TANK_COUNT * 2) {
            return outputTanks[tank - FLUID_TANK_COUNT].isFluidValid(stack);
        }
        return false;
    }

    @Override
    public int fill(@NotNull FluidStack resource, @NotNull FluidAction action) {
        FurnaceFaceMode mode = getMode();
        if (mode == null || !mode.allowsMaterialInput()) return 0;
        return FurnaceCombinedFluidTankHandler.fillInput(inputTanks, resource, action);
    }

    @Override
    public @NotNull FluidStack drain(@NotNull FluidStack resource, @NotNull FluidAction action) {
        FurnaceFaceMode mode = getMode();
        if (mode == null || !mode.allowsMaterialOutput()) return FluidStack.EMPTY;
        for (FluidTank tank : outputTanks) {
            FluidStack drained = tank.drain(resource, action);
            if (!drained.isEmpty()) return drained;
        }
        return FluidStack.EMPTY;
    }

    @Override
    public @NotNull FluidStack drain(int maxDrain, @NotNull FluidAction action) {
        FurnaceFaceMode mode = getMode();
        if (mode == null || !mode.allowsMaterialOutput()) return FluidStack.EMPTY;
        for (FluidTank tank : outputTanks) {
            if (!tank.getFluid().isEmpty()) {
                return tank.drain(maxDrain, action);
            }
        }
        return FluidStack.EMPTY;
    }
}
