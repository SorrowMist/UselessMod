package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

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
        outputRecipe(recipe, parallel, aeOutput, itemHandler, outputSlotsStart, outputSlotsCount,
                outputFluidTanks, fluidTankCount, FurnaceChemicalStorage.DISABLED,
                ChemicalKeyProvider.NONE, null);
    }

    public static void outputRecipe(AdvancedAlloyFurnaceRecipe recipe, int parallel, AeOutput aeOutput,
                                    ItemStackHandler itemHandler, int outputSlotsStart, int outputSlotsCount,
                                    FluidTank[] outputFluidTanks, int fluidTankCount,
                                    FurnaceChemicalStorage outputChemicalStorage,
                                    ChemicalKeyProvider chemicalKeyProvider) {
        outputRecipe(recipe, parallel, aeOutput, itemHandler, outputSlotsStart, outputSlotsCount,
                outputFluidTanks, fluidTankCount, outputChemicalStorage, chemicalKeyProvider, null);
    }

    /** Same as the normal recipe output path, retaining key material that fits nowhere locally. */
    public static void outputRecipe(AdvancedAlloyFurnaceRecipe recipe, int parallel, AeOutput aeOutput,
                                    ItemStackHandler itemHandler, int outputSlotsStart, int outputSlotsCount,
                                    FluidTank[] outputFluidTanks, int fluidTankCount,
                                    FurnaceChemicalStorage outputChemicalStorage,
                                    ChemicalKeyProvider chemicalKeyProvider,
                                    BiConsumer<AEKey, Long> onKeyRemainder) {
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
            if (keyOutput == null || keyOutput.what() == null || keyOutput.amount() <= 0L) continue;
            GenericStack remainder = outputKeyWithRemainder(
                    new GenericStack(keyOutput.what(), saturatingMultiply(keyOutput.amount(), parallel)),
                    aeOutput, outputChemicalStorage, chemicalKeyProvider);
            if (remainder != null && onKeyRemainder != null) {
                onKeyRemainder.accept(remainder.what(), remainder.amount());
            }
        }
    }

    /**
     * 输出单个物品堆，AE 写入失败的剩余部分回退到本地槽位。
     */
    public static void outputItem(ItemStack stack, AeOutput aeOutput, ItemStackHandler itemHandler, int slotsStart, int slotsCount) {
        outputItemWithRemainder(stack, aeOutput, itemHandler, slotsStart, slotsCount);
    }

    /**
     * 输出单个物品堆，AE 写入失败的剩余部分回退到本地槽位。
     *
     * @return 本地槽位也放不下的剩余部分（全部放入则为 EMPTY），由调用方决定去向
     */
    public static ItemStack outputItemWithRemainder(ItemStack stack, AeOutput aeOutput, ItemStackHandler itemHandler, int slotsStart, int slotsCount) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        long inserted = aeOutput.insertItem(stack);
        int remainingCount = (int) Math.max(0, stack.getCount() - inserted);
        if (remainingCount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack remainingStack = stack.copy();
        remainingStack.setCount(remainingCount);
        int leftover = insertItemIntoSlots(remainingStack, itemHandler, slotsStart, slotsCount);
        if (leftover <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack leftoverStack = stack.copy();
        leftoverStack.setCount(leftover);
        return leftoverStack;
    }

    /**
     * 输出单个流体栈，AE 写入失败的剩余部分回退到本地流体槽。
     */
    public static void outputFluid(FluidStack stack, AeOutput aeOutput, FluidTank[] tanks, int tankCount) {
        outputFluidWithRemainder(stack, aeOutput, tanks, tankCount);
    }

    /**
     * 输出单个流体栈，AE 写入失败的剩余部分回退到本地流体槽。
     *
     * @return 流体槽也装不下的剩余部分（全部装入则为 EMPTY），由调用方决定去向
     */
    public static FluidStack outputFluidWithRemainder(FluidStack stack, AeOutput aeOutput, FluidTank[] tanks, int tankCount) {
        if (stack.isEmpty()) {
            return FluidStack.EMPTY;
        }
        long inserted = aeOutput.insertFluid(stack);
        int remainingAmount = (int) Math.max(0, stack.getAmount() - inserted);
        if (remainingAmount <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack remainingFluid = stack.copy();
        remainingFluid.setAmount(remainingAmount);
        return fillFluidTanks(remainingFluid, tanks, tankCount);
    }

    /**
     * 输出 AEKey 类型产物。
     */
    public static void outputKey(AEKey key, long amount, AeOutput aeOutput) {
        aeOutput.insertKey(key, amount);
    }

    /**
     * Outputs an AE key, then falls back to the local chemical slots when the
     * registered provider recognizes it as a chemical key.
     *
     * @return the portion that could not be written to either destination
     */
    public static GenericStack outputKeyWithRemainder(GenericStack stack, AeOutput aeOutput,
                                                       FurnaceChemicalStorage outputChemicalStorage,
                                                       ChemicalKeyProvider chemicalKeyProvider) {
        return outputExactKey(
                stack, aeOutput, null, 0, 0, null, 0,
                outputChemicalStorage, chemicalKeyProvider);
    }

    /**
     * Outputs one exact AE key while projecting only the amount that can fit into the finite local destinations.
     * The retained remainder stays compact regardless of its numeric size.
     */
    public static GenericStack outputKeyWithRemainder(
            GenericStack stack,
            AeOutput aeOutput,
            ItemStackHandler itemHandler,
            int slotsStart,
            int slotsCount,
            FluidTank[] fluidTanks,
            int fluidTankCount,
            FurnaceChemicalStorage outputChemicalStorage,
            ChemicalKeyProvider chemicalKeyProvider) {
        return outputExactKey(
                stack, aeOutput, itemHandler, slotsStart, slotsCount, fluidTanks, fluidTankCount,
                outputChemicalStorage, chemicalKeyProvider);
    }

    private static GenericStack outputExactKey(
            GenericStack stack,
            AeOutput aeOutput,
            @Nullable ItemStackHandler itemHandler,
            int slotsStart,
            int slotsCount,
            FluidTank @Nullable [] fluidTanks,
            int fluidTankCount,
            FurnaceChemicalStorage outputChemicalStorage,
            ChemicalKeyProvider chemicalKeyProvider) {
        if (stack == null || stack.what() == null || stack.amount() <= 0L) return null;

        long requested = stack.amount();
        long inserted = clampInserted(aeOutput.insertKey(stack.what(), requested), requested);
        long remaining = requested - inserted;
        if (remaining <= 0L) return null;

        if (stack.what() instanceof AEItemKey itemKey && itemHandler != null) {
            remaining = insertItemKeyIntoSlots(itemKey, remaining, itemHandler, slotsStart, slotsCount);
        } else if (stack.what() instanceof AEFluidKey fluidKey && fluidTanks != null) {
            remaining = fillFluidKeyIntoTanks(fluidKey, remaining, fluidTanks, fluidTankCount);
        } else if (chemicalKeyProvider != null && chemicalKeyProvider.isChemicalKey(stack.what())) {
            var view = chemicalKeyProvider.fromGenericStack(new GenericStack(stack.what(), remaining));
            if (view != null && outputChemicalStorage != null) {
                var remainder = outputChemicalStorage.insertChemical(view, false);
                long local = Math.max(0L, remaining - remainder.amount());
                remaining -= local;
            }
        }
        return remaining <= 0L ? null : new GenericStack(stack.what(), remaining);
    }

    private static long insertItemKeyIntoSlots(
            AEItemKey key,
            long amount,
            ItemStackHandler itemHandler,
            int slotsStart,
            int slotsCount) {
        long remaining = amount;
        int slotsEnd = Math.min(itemHandler.getSlots(), Math.addExact(slotsStart, slotsCount));
        for (int slot = slotsStart; slot < slotsEnd && remaining > 0L; slot++) {
            ItemStack slotStack = itemHandler.getStackInSlot(slot);
            if (slotStack.isEmpty() || !key.equals(AEItemKey.of(slotStack))) {
                continue;
            }
            int space = itemHandler.getSlotLimit(slot) - slotStack.getCount();
            if (space <= 0) {
                continue;
            }
            int inserted = (int) Math.min(remaining, space);
            slotStack.grow(inserted);
            remaining -= inserted;
        }
        for (int slot = slotsStart; slot < slotsEnd && remaining > 0L; slot++) {
            if (!itemHandler.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            int slotLimit = itemHandler.getSlotLimit(slot);
            if (slotLimit <= 0) {
                continue;
            }
            int inserted = (int) Math.min(remaining, slotLimit);
            itemHandler.setStackInSlot(slot, key.toStack(inserted));
            remaining -= inserted;
        }
        return remaining;
    }

    private static long fillFluidKeyIntoTanks(
            AEFluidKey key,
            long amount,
            FluidTank[] tanks,
            int tankCount) {
        long remaining = amount;
        int tanksEnd = Math.min(tanks.length, tankCount);
        for (int index = 0; index < tanksEnd && remaining > 0L; index++) {
            int offered = (int) Math.min(remaining, Integer.MAX_VALUE);
            int inserted = tanks[index].fill(key.toStack(offered), IFluidHandler.FluidAction.EXECUTE);
            if (inserted > 0) {
                remaining -= inserted;
            }
        }
        return remaining;
    }

    private static long clampInserted(long inserted, long requested) {
        return Math.max(0L, Math.min(requested, inserted));
    }

    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0L || multiplier <= 0L) return 0L;
        return amount > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : amount * multiplier;
    }

    /** @return 槽位放不下的剩余数量 */
    private static int insertItemIntoSlots(ItemStack stack, ItemStackHandler itemHandler, int slotsStart, int slotsCount) {
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
        return remainingCount;
    }

    /** @return 流体槽装不下的剩余部分（全部装入则为 EMPTY） */
    private static FluidStack fillFluidTanks(FluidStack fluidStack, FluidTank[] tanks, int tankCount) {
        FluidStack remainingFluid = fluidStack.copy();
        for (int i = 0; i < tankCount && !remainingFluid.isEmpty(); i++) {
            FluidTank tank = tanks[i];
            FluidStack tankFluid = tank.getFluid();
            if (tankFluid.isEmpty() || FluidStack.isSameFluidSameComponents(tankFluid, remainingFluid)) {
                int filled = tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                remainingFluid.shrink(filled);
            }
        }
        return remainingFluid;
    }
}
