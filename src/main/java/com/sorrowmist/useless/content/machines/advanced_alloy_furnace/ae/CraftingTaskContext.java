package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.stacks.AEKey;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AdapterUtils;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalKeyProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.energy.IEnergyManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * CraftingTask 的上下文接口，提供对 AdvancedAlloyFurnaceBlockEntity 必要成员的访问
 */
public interface CraftingTaskContext {
    
    // 槽位常量
    int getInputSlotsStart();
    int getInputSlotsCount();
    int getOutputSlotsStart();
    int getOutputSlotsCount();
    int getCatalystSlot();
    int getMoldSlot();
    int getFluidTankCount();
    
    // 基础访问
    Level getLevel();
    net.minecraft.core.BlockPos getBlockPos();
    ItemStackHandler getItemHandler();
    IEnergyManager getEnergyManager();
    
    // 状态更新
    void markChanged();
    void sendAETaskProgressToClients();
    
    // 催化剂相关
    int getCatalystMaxParallel();
    
    // AE网络输出
    long tryOutputToAE(net.minecraft.world.item.ItemStack stack);
    long tryOutputFluidToAE(FluidStack stack);
    long tryOutputKeyToAE(AEKey key, long amount);

    default long tryOutputChemicalToAE(ChemicalStackView stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        GenericStack generic = getChemicalKeyProvider().toGenericStack(stack);
        return generic == null ? 0L : tryOutputKeyToAE(generic.what(), generic.amount());
    }

    default FurnaceChemicalStorage getInputChemicalStorage() {
        return FurnaceChemicalStorage.DISABLED;
    }

    default FurnaceChemicalStorage getOutputChemicalStorage() {
        return FurnaceChemicalStorage.DISABLED;
    }

    default ChemicalKeyProvider getChemicalKeyProvider() {
        return ChemicalKeyProviders.get();
    }

    // 产物输出模式
    boolean isReturnOutputToAe();

    /** 暂存未能返还的输入（AE 写入失败时防丢失，由管理器逐 tick 重试写回） */
    void stashUnreturnedInput(AEKey key, long amount);

    /** Stores a produced key that could not be returned to AE or an output slot. */
    default void stashUnreturnedOutput(AEKey key, long amount) {
        stashUnreturnedInput(key, amount);
    }

    /**
     * 统一 AE 输出端口：受“产物返回AE”开关约束，开关关闭时不写入 AE 网络，
     * 剩余部分由调用方回退到本地槽位。
     */
    default FurnaceOutputPort.AeOutput createAeOutputPort() {
        return new FurnaceOutputPort.AeOutput() {
            @Override
            public long insertItem(ItemStack stack) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputToAE(stack);
            }

            @Override
            public long insertFluid(FluidStack stack) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputFluidToAE(stack);
            }

            @Override
            public long insertKey(AEKey key, long amount) {
                if (!isReturnOutputToAe()) return 0;
                return tryOutputKeyToAE(key, amount);
            }
        };
    }
    
    // 任务进度管理
    ConcurrentHashMap<Integer, AdvancedAlloyFurnaceAeManager.AETaskProgress> getAETaskProgressMap();
    AtomicInteger getTotalAEMaxProgressAtomic();
    AtomicInteger getTotalAEProgressAtomic();
    
    // 锁和流体罐
    ReentrantLock getCraftingLock();
    FluidTank[] getInputFluidTanks();
    FluidTank[] getOutputFluidTanks();

    default AdvancedAlloyFurnaceRecipe resolveTaskRecipe(
            IPatternDetails pattern, List<ItemStack> items, List<FluidStack> fluids,
            List<GenericStack> keys, long operations) {
        if (pattern != null) {
            IPatternDetails original = SmartDoublingPatterns.unwrap(pattern);
            if (original instanceof OmniversalPatternDetails omniversal) {
                return omniversal.recipe();
            }
        }

        ItemStack mold = getItemHandler().getStackInSlot(getMoldSlot());
        return AlloyFurnaceRecipeManager.getInstance().findRecipeForCraftingWithConstraints(
                getLevel(), items, fluids, keys, mold,
                AdvancedAlloyFurnacePatternPolicy.outputConstraints(pattern), operations);
    }

    default boolean isTaskRecipeAvailable(AdvancedAlloyFurnaceRecipe recipe) {
        return getTaskAvailability(recipe).available();
    }

    default TaskAvailability getTaskAvailability(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe == null) {
            return TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_recipe", "");
        }
        if (recipe.mold() == null || recipe.mold().isEmpty()) return TaskAvailability.ready();
        ItemStackHandler itemHandler = getItemHandler();
        int moldSlot = getMoldSlot();
        if (itemHandler == null || moldSlot < 0 || moldSlot >= itemHandler.getSlots()) {
            return TaskAvailability.unavailable(
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                    describeRequiredMold(recipe.mold()));
        }
        ItemStack mold = itemHandler.getStackInSlot(moldSlot);
        return AdapterUtils.matchesMold(recipe.mold(), mold)
                ? TaskAvailability.ready()
                : TaskAvailability.unavailable(
                        "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_missing_mold",
                        describeRequiredMold(recipe.mold()));
    }

    static String describeRequiredMold(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return "";
        for (ItemStack candidate : ingredient.getItems()) {
            if (!candidate.isEmpty()) return candidate.getHoverName().getString();
        }
        return "";
    }

    record TaskAvailability(boolean available, String statusKey, String statusDetail) {
        public TaskAvailability {
            statusKey = statusKey == null ? "" : statusKey;
            statusDetail = statusDetail == null ? "" : statusDetail;
        }

        public static TaskAvailability ready() {
            return new TaskAvailability(true, "", "");
        }

        public static TaskAvailability unavailable(String statusKey, String statusDetail) {
            return new TaskAvailability(false, statusKey, statusDetail);
        }
    }

    default ResolvedCatalystEffect resolveTaskEffect(AdvancedAlloyFurnaceRecipe recipe) {
        int baseTime = recipe == null ? 200 : Math.max(1, recipe.processTime());
        return CatalystEffectResolver.resolve(recipe, getItemHandler().getStackInSlot(getCatalystSlot()), baseTime);
    }

    default int getTaskProcessTime(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        return effect == null ? Math.max(1, recipe == null ? 200 : recipe.processTime())
                : Math.max(1, effect.processTime());
    }

    default long getTaskParallel(AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect effect) {
        return recipe == null || effect == null ? 1
                : AlloyFurnaceParallelCalculator.calculateAeTaskParallel(recipe, effect);
    }

    /** Long-count AE contexts keep item and fluid amounts as AE keys instead of int-sized stacks. */
    default boolean supportsLongAeAmounts() {
        return false;
    }

    default boolean isTaskExecutionEnabled() {
        return true;
    }

    default void handleUnreturnedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || getLevel() == null) return;
        var pos = getBlockPos();
        net.minecraft.world.Containers.dropItemStack(
                getLevel(), pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, stack);
    }

    default void handleUnreturnedFluid(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return;
        var key = appeng.api.stacks.AEFluidKey.of(stack);
        if (key != null) stashUnreturnedInput(key, stack.getAmount());
    }
}
