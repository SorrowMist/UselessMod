package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

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
    public static void consumeRecipeInputs(AdvancedAlloyFurnaceRecipe recipe, int parallel, ItemStackHandler itemHandler, int inputSlotsStart, int inputSlotsCount, FluidTank[] inputFluidTanks, int fluidTankCount) {
        for (var countedIng : recipe.inputs()) {
            long toConsume = countedIng.count() * (long) parallel;
            var ingredient = countedIng.ingredient();
            for (int i = inputSlotsStart; i < inputSlotsStart + inputSlotsCount && toConsume > 0; i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (ingredient.test(stack)) {
                    int consumed = (int) Math.min(toConsume, stack.getCount());
                    stack.shrink(consumed);
                    toConsume -= consumed;
                }
            }
        }

        for (FluidStack requiredFluid : recipe.inputFluids()) {
            long toDrainLong = (long) requiredFluid.getAmount() * parallel;
            int toDrain = toDrainLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) toDrainLong;
            for (int i = 0; i < fluidTankCount && toDrain > 0; i++) {
                FluidStack tankFluid = inputFluidTanks[i].getFluid();
                if (FluidStack.isSameFluidSameComponents(tankFluid, requiredFluid)) {
                    FluidStack drained = inputFluidTanks[i].drain(toDrain, IFluidHandler.FluidAction.EXECUTE);
                    toDrain -= drained.getAmount();
                }
            }
        }
    }
}
