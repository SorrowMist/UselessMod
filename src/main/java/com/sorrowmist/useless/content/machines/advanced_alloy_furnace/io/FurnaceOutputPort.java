package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import appeng.api.stacks.AEKey;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 高级合金炉统一输出端口。
 * 负责把产物优先写入 AE 网络，剩余部分再回退到本地物品槽或流体槽。
 */
public final class FurnaceOutputPort {
    private FurnaceOutputPort() {
    }

    /**
     * AE 输出适配接口，用于把方块实体或 AE 任务上下文适配为统一输出入口。
     */
    public interface AeOutput {
        long insertItem(ItemStack stack);

        long insertFluid(FluidStack stack);

        long insertKey(AEKey key, long amount);
    }

    /**
     * 按配方批量输出物品、流体和 AEKey 产物。
     */
    public static void outputRecipe(AdvancedAlloyFurnaceRecipe recipe, int parallel, AeOutput aeOutput, ItemStackHandler itemHandler, int outputSlotsStart, int outputSlotsCount, FluidTank[] outputFluidTanks, int fluidTankCount) {
        for (ItemStack output : recipe.outputs()) {
            long totalCountLong = (long) output.getCount() * parallel;
            int totalCount = totalCountLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalCountLong;
            if (totalCount <= 0) {
                continue;
            }
            ItemStack toOutput = output.copy();
            toOutput.setCount(totalCount);
            outputItem(toOutput, aeOutput, itemHandler, outputSlotsStart, outputSlotsCount);
        }

        for (FluidStack outputFluid : recipe.outputFluids()) {
            long totalAmountLong = (long) outputFluid.getAmount() * parallel;
            int totalAmount = totalAmountLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalAmountLong;
            if (totalAmount <= 0) {
                continue;
            }
            FluidStack toOutput = outputFluid.copy();
            toOutput.setAmount(totalAmount);
            outputFluid(toOutput, aeOutput, outputFluidTanks, fluidTankCount);
        }

        for (var keyOutput : recipe.keyOutputs()) {
            outputKey(keyOutput.what(), keyOutput.amount() * parallel, aeOutput);
        }
    }

    /**
     * 输出单个物品堆，AE 写入失败的剩余部分回退到本地槽位。
     */
    public static void outputItem(ItemStack stack, AeOutput aeOutput, ItemStackHandler itemHandler, int slotsStart, int slotsCount) {
        if (stack.isEmpty()) {
            return;
        }
        long inserted = aeOutput.insertItem(stack);
        int remainingCount = (int) Math.max(0, stack.getCount() - inserted);
        if (remainingCount <= 0) {
            return;
        }
        ItemStack remainingStack = stack.copy();
        remainingStack.setCount(remainingCount);
        insertItemIntoSlots(remainingStack, itemHandler, slotsStart, slotsCount);
    }

    /**
     * 输出单个流体栈，AE 写入失败的剩余部分回退到本地流体槽。
     */
    public static void outputFluid(FluidStack stack, AeOutput aeOutput, FluidTank[] tanks, int tankCount) {
        if (stack.isEmpty()) {
            return;
        }
        long inserted = aeOutput.insertFluid(stack);
        int remainingAmount = (int) Math.max(0, stack.getAmount() - inserted);
        if (remainingAmount <= 0) {
            return;
        }
        FluidStack remainingFluid = stack.copy();
        remainingFluid.setAmount(remainingAmount);
        fillFluidTanks(remainingFluid, tanks, tankCount);
    }

    /**
     * 输出 AEKey 类型产物。
     */
    public static void outputKey(AEKey key, long amount, AeOutput aeOutput) {
        aeOutput.insertKey(key, amount);
    }

    private static void insertItemIntoSlots(ItemStack stack, ItemStackHandler itemHandler, int slotsStart, int slotsCount) {
        int remainingCount = stack.getCount();
        for (int i = slotsStart; i < slotsStart + slotsCount && remainingCount > 0; i++) {
            ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (ItemStack.isSameItemSameComponents(slotStack, stack)) {
                int space = itemHandler.getSlotLimit(i) - slotStack.getCount();
                int toAdd = Math.min(space, remainingCount);
                slotStack.grow(toAdd);
                remainingCount -= toAdd;
            }
        }

        for (int i = slotsStart; i < slotsStart + slotsCount && remainingCount > 0; i++) {
            ItemStack slotStack = itemHandler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                ItemStack insertedStack = stack.copy();
                int toAdd = Math.min(itemHandler.getSlotLimit(i), remainingCount);
                insertedStack.setCount(toAdd);
                itemHandler.setStackInSlot(i, insertedStack);
                remainingCount -= toAdd;
            }
        }
    }

    private static void fillFluidTanks(FluidStack fluidStack, FluidTank[] tanks, int tankCount) {
        FluidStack remainingFluid = fluidStack.copy();
        for (int i = 0; i < tankCount && !remainingFluid.isEmpty(); i++) {
            FluidTank tank = tanks[i];
            FluidStack tankFluid = tank.getFluid();
            if (tankFluid.isEmpty() || FluidStack.isSameFluidSameComponents(tankFluid, remainingFluid)) {
                int filled = tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                remainingFluid.shrink(filled);
            }
        }
    }
}
