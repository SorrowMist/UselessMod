package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.ItemIngredientAllocator;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * 高级合金炉统一输入端口。
 * 负责按配方并行数从本地物品输入槽和流体输入槽中消耗材料。
 */
public final class FurnaceInputPort {
    private FurnaceInputPort() {
    }

    /**
     * 按指定并行数消耗配方所需的物品和流体输入。
     */
    public static boolean consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel, ItemStackHandler itemHandler, int inputSlotsStart, int inputSlotsCount, FluidTank[] inputFluidTanks, int fluidTankCount) {
        PreparedInputs prepared = prepareInputs(
                recipe, parallel, itemHandler, inputSlotsStart, inputSlotsCount, inputFluidTanks, fluidTankCount);
        if (prepared == null) return false;

        for (int i = 0; i < prepared.itemAllocation().inputCount(); i++) {
            long consumed = prepared.itemAllocation().consumedFromInput(i);
            if (consumed <= 0) continue;

            int slot = inputSlotsStart + i;
            ItemStack remaining = itemHandler.getStackInSlot(slot).copy();
            remaining.shrink((int) consumed);
            itemHandler.setStackInSlot(slot, remaining);
        }

        for (int i = 0; i < prepared.fluidConsumedByTank().length; i++) {
            long remaining = prepared.fluidConsumedByTank()[i];
            while (remaining > 0) {
                int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                FluidStack drained = inputFluidTanks[i].drain(chunk, IFluidHandler.FluidAction.EXECUTE);
                if (drained.getAmount() != chunk) {
                    throw new IllegalStateException("Validated alloy furnace fluid input changed during consumption");
                }
                remaining -= chunk;
            }
        }
        return true;
    }

    public static boolean canConsumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel, ItemStackHandler itemHandler, int inputSlotsStart, int inputSlotsCount, FluidTank[] inputFluidTanks, int fluidTankCount) {
        return prepareInputs(
                recipe, parallel, itemHandler, inputSlotsStart, inputSlotsCount, inputFluidTanks, fluidTankCount) != null;
    }

    public static int calculateMaterialParallel(AdvancedAlloyFurnaceRecipe recipe, ItemStackHandler itemHandler, int inputSlotsStart, int inputSlotsCount, FluidTank[] inputFluidTanks, int fluidTankCount) {
        if (recipe == null) return 0;

        List<ItemStack> itemInputs = snapshotItems(itemHandler, inputSlotsStart, inputSlotsCount);
        int itemParallel = ItemIngredientAllocator.maxOperations(recipe.inputs(), itemInputs);
        int fluidParallel = maxFluidOperations(recipe.inputFluids(), inputFluidTanks, fluidTankCount);
        boolean hasItemDemand = recipe.inputs().stream().anyMatch(input -> input != null && input.count() > 0);
        boolean hasFluidDemand = recipe.inputFluids().stream().anyMatch(input -> input != null && !input.isEmpty() && input.getAmount() > 0);
        if (!hasItemDemand && !hasFluidDemand) return 1;
        return Math.min(itemParallel, fluidParallel);
    }

    private static PreparedInputs prepareInputs(AdvancedAlloyFurnaceRecipe recipe, long operations, ItemStackHandler itemHandler, int inputSlotsStart, int inputSlotsCount, FluidTank[] inputFluidTanks, int fluidTankCount) {
        if (recipe == null || operations <= 0 || itemHandler == null || inputFluidTanks == null) return null;

        List<ItemStack> itemInputs = snapshotItems(itemHandler, inputSlotsStart, inputSlotsCount);
        ItemIngredientAllocator.Allocation itemAllocation = ItemIngredientAllocator.allocate(
                recipe.inputs(), itemInputs, operations);
        if (itemAllocation == null) return null;

        long[] fluidAllocation = allocateFluids(recipe.inputFluids(), operations, inputFluidTanks, fluidTankCount);
        if (fluidAllocation == null) return null;
        return new PreparedInputs(itemAllocation, fluidAllocation);
    }

    private static List<ItemStack> snapshotItems(ItemStackHandler itemHandler, int inputSlotsStart, int inputSlotsCount) {
        int count = Math.max(0, Math.min(inputSlotsCount, itemHandler.getSlots() - inputSlotsStart));
        List<ItemStack> inputs = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            inputs.add(itemHandler.getStackInSlot(inputSlotsStart + i));
        }
        return inputs;
    }

    private static long[] allocateFluids(List<FluidStack> requirements, long operations, FluidTank[] tanks, int fluidTankCount) {
        int count = Math.max(0, Math.min(fluidTankCount, tanks.length));
        long[] consumedByTank = new long[count];
        for (FluidDemand demand : mergeFluidDemands(requirements)) {
            long remaining = saturatingMultiply(demand.amount(), operations);
            for (int i = 0; i < count && remaining > 0; i++) {
                FluidStack available = tanks[i].getFluid();
                if (!FluidStack.isSameFluidSameComponents(available, demand.fluid())) continue;
                long consumed = Math.min(remaining, (long) available.getAmount() - consumedByTank[i]);
                if (consumed <= 0) continue;
                consumedByTank[i] += consumed;
                remaining -= consumed;
            }
            if (remaining > 0) return null;
        }
        return consumedByTank;
    }

    private static int maxFluidOperations(List<FluidStack> requirements, FluidTank[] tanks, int fluidTankCount) {
        List<FluidDemand> demands = mergeFluidDemands(requirements);
        if (demands.isEmpty()) return Integer.MAX_VALUE;

        int count = Math.max(0, Math.min(fluidTankCount, tanks.length));
        int maximum = Integer.MAX_VALUE;
        for (FluidDemand demand : demands) {
            long availableAmount = 0;
            for (int i = 0; i < count; i++) {
                FluidStack available = tanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(available, demand.fluid())) {
                    availableAmount = saturatingAdd(availableAmount, available.getAmount());
                }
            }
            long operations = availableAmount / demand.amount();
            maximum = Math.min(maximum, operations > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) operations);
        }
        return maximum;
    }

    private static List<FluidDemand> mergeFluidDemands(List<FluidStack> requirements) {
        List<FluidDemand> demands = new ArrayList<>();
        if (requirements == null) return demands;
        for (FluidStack requirement : requirements) {
            if (requirement == null || requirement.isEmpty() || requirement.getAmount() <= 0) continue;
            boolean merged = false;
            for (int i = 0; i < demands.size(); i++) {
                FluidDemand existing = demands.get(i);
                if (FluidStack.isSameFluidSameComponents(existing.fluid(), requirement)) {
                    demands.set(i, new FluidDemand(existing.fluid(), saturatingAdd(existing.amount(), requirement.getAmount())));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                demands.add(new FluidDemand(requirement.copyWithAmount(1), requirement.getAmount()));
            }
        }
        return demands;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }

    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0 || multiplier <= 0) return 0;
        if (amount > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return amount * multiplier;
    }

    private record FluidDemand(FluidStack fluid, long amount) {
    }

    private record PreparedInputs(ItemIngredientAllocator.Allocation itemAllocation, long[] fluidConsumedByTank) {
    }
}
