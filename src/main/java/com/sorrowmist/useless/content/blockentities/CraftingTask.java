package com.sorrowmist.useless.content.blockentities;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import com.sorrowmist.useless.utils.CatalystParallelManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 多线程合成任务类 - 处理 AE 网络的合成请求
 */
public class CraftingTask {
    private final int taskId;
    private final IPatternDetails pattern;
    private final CraftingTaskContext context;
    private final ReentrantLock taskLock = new ReentrantLock();
    private final List<ItemStack> taskInputItems = new ArrayList<>();
    private final List<FluidStack> taskInputFluids = new ArrayList<>();
    private final AtomicInteger craftCount = new AtomicInteger(1);
    private volatile boolean cancelled = false;
    private volatile boolean processingComplete = false;
    private AdvancedAlloyFurnaceBlockEntity.AETaskProgress taskProgressRef = null;

    public CraftingTask(int taskId, IPatternDetails pattern, KeyCounter[] inputHolder, int totalCrafts,
                        CraftingTaskContext context) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
        this.craftCount.set(Math.max(1, totalCrafts));
        this.storeInputMaterials(inputHolder);
    }

    public boolean isSamePattern(IPatternDetails otherPattern) {
        if (this.pattern == null || otherPattern == null) {
            return false;
        }
        var thisOutputs = this.pattern.getOutputs();
        var otherOutputs = otherPattern.getOutputs();
        if (thisOutputs.size() != otherOutputs.size()) {
            return false;
        }
        for (int i = 0; i < thisOutputs.size(); i++) {
            if (!thisOutputs.get(i).what().equals(otherOutputs.get(i).what())) {
                return false;
            }
        }
        return true;
    }

    public boolean isProcessingComplete() {
        return processingComplete;
    }

    public void addMaterials(KeyCounter[] additionalInput) {
        taskLock.lock();
        try {
            if (processingComplete) {
                return;
            }
            craftCount.incrementAndGet();
            storeInputMaterials(additionalInput);
            if (taskProgressRef != null) {
                taskProgressRef.updateCraftCount(craftCount.get());
                context.markChanged();
                context.sendAETaskProgressToClients();
            }
        } finally {
            taskLock.unlock();
        }
    }

    private void storeInputMaterials(KeyCounter[] counters) {
        if (counters == null) return;

        for (KeyCounter counter : counters) {
            if (counter == null) continue;

            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();

                if (key instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.toStack((int) amount);
                    taskInputItems.add(stack);
                } else if (key instanceof AEFluidKey fluidKey) {
                    FluidStack stack = new FluidStack(fluidKey.getFluid(), (int) amount);
                    taskInputFluids.add(stack);
                }
            }
        }
    }

    /**
     * 使用本体模具/催化剂统一查找配方，与机器本体匹配逻辑一致
     */
    private AdvancedAlloyFurnaceRecipe findTaskRecipe() {
        if (context.getLevel() == null) return null;

        List<ItemStack> tempInputs = new ArrayList<>(taskInputItems);
        List<FluidStack> tempFluids = new ArrayList<>(taskInputFluids);

        ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
        if (!catalystStack.isEmpty()) {
            tempInputs.add(catalystStack.copy());
        }

        ItemStack moldStack = context.getItemHandler().getStackInSlot(context.getMoldSlot());

        return AlloyFurnaceRecipeManager.getInstance().findRecipe(
                context.getLevel(), tempInputs, tempFluids, moldStack
        );
    }

    private String getProductName() {
        if (pattern == null || pattern.getOutputs().isEmpty()) {
            return "Unknown";
        }

        var output = pattern.getOutputs().getFirst();
        if (output.what() instanceof AEItemKey itemKey) {
            return itemKey.getItem().getDescriptionId();
        } else if (output.what() instanceof AEFluidKey fluidKey) {
            return fluidKey.toString();
        }

        return "Unknown";
    }

    private boolean validateRecipe() {
        if (context.getLevel() == null || pattern == null) {
            returnMaterialsToAE();
            return false;
        }

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        if (recipe == null) {
            returnMaterialsToAE();
            return false;
        }

        if (pattern.getOutputs().isEmpty()) {
            returnMaterialsToAE();
            return false;
        }

        if (recipe.outputs().isEmpty() && recipe.outputFluids().isEmpty()) {
            returnMaterialsToAE();
            return false;
        }

        for (var patternOutput : pattern.getOutputs()) {
            boolean matched = false;

            if (patternOutput.what() instanceof AEItemKey itemKey) {
                ItemStack patternStack = itemKey.toStack((int) patternOutput.amount());
                for (ItemStack recipeOutput : recipe.outputs()) {
                    if (ItemStack.isSameItem(patternStack, recipeOutput)) {
                        matched = true;
                        break;
                    }
                }
            } else if (patternOutput.what() instanceof AEFluidKey fluidKey) {
                for (FluidStack recipeFluid : recipe.outputFluids()) {
                    if (fluidKey.getFluid().isSame(recipeFluid.getFluid())) {
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                returnMaterialsToAE();
                return false;
            }
        }

        return true;
    }

    private void returnMaterialsToAE() {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        // 在工作线程中复制需要返回的材料，避免在主线程中访问taskInputItems
        final List<ItemStack> itemsToReturn;
        final List<FluidStack> fluidsToReturn;

        taskLock.lock();
        try {
            itemsToReturn = new ArrayList<>(taskInputItems);
            fluidsToReturn = new ArrayList<>(taskInputFluids);
            taskInputItems.clear();
            taskInputFluids.clear();
        } finally {
            taskLock.unlock();
        }

        context.getLevel().getServer().execute(() -> {
            // 将任务输入物品返回给AE网络
            for (ItemStack stack : itemsToReturn) {
                if (!stack.isEmpty()) {
                    // 尝试输出到AE网络
                    long inserted = context.tryOutputToAE(stack);
                    int remainingCount = (int) (stack.getCount() - inserted);

                    // 如果AE网络没存下，尝试放入机器的输入槽
                    if (remainingCount > 0) {
                        ItemStack remainingStack = stack.copy();
                        remainingStack.setCount(remainingCount);

                        // 使用tryLock避免阻塞主线程
                        boolean locked = context.getCraftingLock().tryLock();
                        if (locked) {
                            try {
                                int inputSlotsStart = context.getInputSlotsStart();
                                int inputSlotsCount = context.getInputSlotsCount();
                                for (int i = inputSlotsStart; i < inputSlotsStart + inputSlotsCount; i++) {
                                    ItemStack slotStack = context.getItemHandler().getStackInSlot(i);
                                    if (slotStack.isEmpty()) {
                                        context.getItemHandler().setStackInSlot(i, remainingStack.copy());
                                        break;
                                    } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack) &&
                                            slotStack.getCount() < slotStack.getMaxStackSize()) {
                                        int addAmount = Math.min(remainingCount,
                                                slotStack.getMaxStackSize() - slotStack.getCount()
                                        );
                                        slotStack.grow(addAmount);
                                        remainingCount -= addAmount;
                                        if (remainingCount <= 0) break;
                                    }
                                }
                            } finally {
                                context.getCraftingLock().unlock();
                            }
                        }
                    }
                }
            }

            // 将任务输入流体返回给AE网络
            for (FluidStack fluidStack : fluidsToReturn) {
                if (!fluidStack.isEmpty()) {
                    // 尝试输出到AE网络
                    long inserted = context.tryOutputFluidToAE(fluidStack);
                    int remainingAmount = (int) (fluidStack.getAmount() - inserted);

                    // 如果AE网络没存下，尝试放入机器的输入流体槽
                    if (remainingAmount > 0) {
                        FluidStack remainingFluid = fluidStack.copy();
                        remainingFluid.setAmount(remainingAmount);

                        // 使用tryLock避免阻塞主线程
                        boolean locked = context.getCraftingLock().tryLock();
                        if (locked) {
                            try {
                                int fluidTankCount = context.getFluidTankCount();
                                FluidTank[] inputFluidTanks = context.getInputFluidTanks();
                                for (int i = 0; i < fluidTankCount; i++) {
                                    FluidTank tank = inputFluidTanks[i];
                                    if (tank.isEmpty() || tank.getFluid().getFluid().isSame(remainingFluid.getFluid())) {
                                        int filled = tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                                        remainingAmount -= filled;
                                        if (remainingAmount <= 0) break;
                                    }
                                }
                            } finally {
                                context.getCraftingLock().unlock();
                            }
                        }
                    }
                }
            }
        });
    }

    private int getRecipeProcessTime() {
        if (context.getLevel() == null) return 200;

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        if (recipe != null && recipe.processTime() > 0) {
            ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
            return CatalystParallelManager.calculateProcessTimeWithCatalyst(recipe.processTime(), catalystStack);
        }

        return 200;
    }

    public void run() {
        if (cancelled || context.getLevel() == null || context.getLevel().isClientSide) {
            return;
        }

        // 验证配方：检查输入材料是否能通过有效的配方合成出样板定义的产物
        if (!validateRecipe()) {
            // 找不到有效的配方，取消任务
            processingComplete = true;
            return;
        }

        // 保存基础处理时间用于异常处理
        final int baseProcessTime = getRecipeProcessTime();

        // 获取配方基础能量消耗（每tick），优先使用配方自身能量
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        final int baseEnergyPerTick;
        if (recipe != null && recipe.processTime() > 0) {
            baseEnergyPerTick = Math.max(1, recipe.energy() / recipe.processTime());
        } else {
            baseEnergyPerTick = 200;
        }

        try {
            int currentCraftCount = craftCount.get(); // 获取当前需要合成的次数
            // 使用机器本体催化剂槽位中的催化剂来决定最大并行数
            int maxParallel = context.getCatalystMaxParallel();
            if (maxParallel <= 0) {
                maxParallel = 1; // 至少为1
            }

            // 获取合成产物名称和单次产出数量
            String productName = getProductName();
            int outputCount = 1;
            if (pattern != null && !pattern.getOutputs().isEmpty()) {
                var output = pattern.getOutputs().getFirst();
                outputCount = (int) output.amount();
            }

            ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
            boolean useUsefulIngot = !catalystStack.isEmpty() && CatalystParallelManager.isUsefulIngot(catalystStack);

            // 只有不用有用锭时才用能量限制并行（用有用锭时能量不限制并行）
            if (baseEnergyPerTick > 0 && !useUsefulIngot) {
                int maxEnergyParallel = context.getEnergyManager().getMaxEnergyStored() / baseEnergyPerTick;
                maxParallel = Math.min(maxParallel, Math.max(1, maxEnergyParallel));
            }

            // 总处理时间 = 基础时间 × ceil(合成次数 / 最大并行数)
            int batches = (int) Math.ceil((double) currentCraftCount / maxParallel);
            int processTime = baseProcessTime * batches;
            int lastBatchSize = currentCraftCount - maxParallel * (batches - 1);

            int progress = 0;

            int totalOutputCount = currentCraftCount * outputCount; // 最终产物总数 = 合成次数 × 单次产出数量

            // 创建任务进度信息并添加到地图中
            AdvancedAlloyFurnaceBlockEntity.AETaskProgress taskProgress = new AdvancedAlloyFurnaceBlockEntity.AETaskProgress(
                    productName, processTime, currentCraftCount, totalOutputCount
            );
            context.getAETaskProgressMap().put(taskId, taskProgress);
            // 保存任务进度引用，用于任务合并时更新
            this.taskProgressRef = taskProgress;

            // 更新总进度
            context.getTotalAEMaxProgressAtomic().addAndGet(processTime);
            context.markChanged();

            // 发送初始任务进度到客户端
            context.sendAETaskProgressToClients();

            int progressUpdateCounter = 0;
            boolean energyFailed = false;
            while (progress < processTime && !cancelled) {
                // 先检查是否已取消（使用volatile读取，不需要锁）
                if (cancelled) break;

                // 根据当前批次计算实际并行数（最后一批可能不满maxParallel）
                int batchIndex = baseProcessTime > 0 ? progress / baseProcessTime : 0;
                int actualBatchParallel = (batchIndex < batches - 1) ? maxParallel : lastBatchSize;
                long energyRequiredLong = useUsefulIngot ? (long) baseEnergyPerTick :
                        (long) baseEnergyPerTick * actualBatchParallel;
                int energyRequired =
                        energyRequiredLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) energyRequiredLong;

                // 尝试消耗能量（不需要锁）
                if (!context.getEnergyManager().tryConsumeEnergy(energyRequired)) {
                    energyFailed = true;
                    break;
                }

                // 更新进度（需要锁保护）
                taskLock.lock();
                try {
                    if (cancelled) break;
                    progress++;
                    context.getTotalAEProgressAtomic().incrementAndGet();
                    // 更新单个任务的进度
                    taskProgress.setProgress(progress);
                    context.markChanged();

                    // 每20 ticks发送一次进度更新（大约每秒一次）
                    progressUpdateCounter++;
                    if (progressUpdateCounter >= 20) {
                        context.sendAETaskProgressToClients();
                        progressUpdateCounter = 0;
                    }
                } finally {
                    taskLock.unlock();
                }

                Thread.sleep(50);
            }

            if (energyFailed) {
                returnMaterialsToAE();
                processingComplete = true;
                context.getTotalAEProgressAtomic().addAndGet(-progress);
                context.getTotalAEMaxProgressAtomic().addAndGet(-processTime);
                context.getAETaskProgressMap().remove(taskId);
                context.markChanged();
                return;
            }

            if (!cancelled && progress >= processTime) {
                // 使用最新的 craftCount 值，确保包含所有合并的材料
                completeCrafting(craftCount.get());
            }

            // 标记任务已完成处理（不再接受新材料合并）
            processingComplete = true;

            // 任务完成或取消后更新总进度
            context.getTotalAEProgressAtomic().addAndGet(-progress);
            context.getTotalAEMaxProgressAtomic().addAndGet(-processTime);
            // 移除任务进度信息
            context.getAETaskProgressMap().remove(taskId);
            context.markChanged();

            // 发送任务完成的进度更新到客户端
            context.sendAETaskProgressToClients();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // 任务中断后更新总进度
            processingComplete = true;
            int maxParallel = context.getCatalystMaxParallel();
            if (maxParallel <= 0) {
                maxParallel = 1;
            }
            if (baseEnergyPerTick > 0) {
                int maxEnergyParallel = context.getEnergyManager().getMaxEnergyStored() / baseEnergyPerTick;
                maxParallel = Math.min(maxParallel, Math.max(1, maxEnergyParallel));
            }
            int batches = (int) Math.ceil((double) craftCount.get() / maxParallel);
            context.getTotalAEMaxProgressAtomic().addAndGet(-baseProcessTime * batches);
            context.markChanged();
        }
    }

    private void completeCrafting(int craftCount) {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        // 在工作线程中预先准备输出数据，避免在主线程中等待锁
        final List<OutputItem> outputItems = new ArrayList<>();
        final List<OutputFluid> outputFluids = new ArrayList<>();

        for (var output : pattern.getOutputs()) {
            if (output.what() instanceof AEItemKey itemKey) {
                ItemStack outputStack = itemKey.toStack((int) (output.amount() * craftCount));
                outputItems.add(new OutputItem(outputStack));
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                FluidStack outputFluid = new FluidStack(fluidKey.getFluid(), (int) (output.amount() * craftCount));
                outputFluids.add(new OutputFluid(outputFluid));
            }
        }

        // 清空任务的独立存储空间（在工作线程中完成，不需要锁）
        taskLock.lock();
        try {
            taskInputItems.clear();
            taskInputFluids.clear();
        } finally {
            taskLock.unlock();
        }

        // 在主线程中执行AE网络输出和槽位操作，使用tryLock避免阻塞
        context.getLevel().getServer().execute(() -> {
            // 处理物品输出
            for (OutputItem outputItem : outputItems) {
                ItemStack outputStack = outputItem.stack;

                // 优先输出到AE网络
                long inserted = context.tryOutputToAE(outputStack);
                int remainingCount = (int) (outputStack.getCount() - inserted);

                // 如果AE网络没存下，输出到自己的输出栏
                if (remainingCount > 0) {
                    ItemStack remainingStack = outputStack.copy();
                    remainingStack.setCount(remainingCount);
                    // 使用tryLock避免阻塞主线程
                    boolean locked = context.getCraftingLock().tryLock();
                    if (locked) {
                        try {
                            int outputSlotsStart = context.getOutputSlotsStart();
                            int outputSlotsCount = context.getOutputSlotsCount();
                            for (int i = outputSlotsStart; i < outputSlotsStart + outputSlotsCount; i++) {
                                ItemStack slotStack = context.getItemHandler().getStackInSlot(i);
                                if (slotStack.isEmpty()) {
                                    context.getItemHandler().setStackInSlot(i, remainingStack.copy());
                                    break;
                                } else if (ItemStack.isSameItemSameComponents(slotStack, remainingStack)) {
                                    slotStack.grow(remainingStack.getCount());
                                    break;
                                }
                            }
                        } finally {
                            context.getCraftingLock().unlock();
                        }
                    }
                    // 如果获取不到锁，物品会丢失（但这种情况很少发生，且比卡死游戏好）
                }
            }

            // 处理流体输出
            for (OutputFluid outputFluid : outputFluids) {
                FluidStack fluidStack = outputFluid.stack;

                // 优先输出到AE网络
                long inserted = context.tryOutputFluidToAE(fluidStack);
                int remainingAmount = (int) (fluidStack.getAmount() - inserted);

                // 如果AE网络没存下，输出到自己的流体输出槽
                if (remainingAmount > 0) {
                    FluidStack remainingFluid = new FluidStack(fluidStack.getFluid(), remainingAmount);
                    // 使用tryLock避免阻塞主线程
                    boolean locked = context.getCraftingLock().tryLock();
                    if (locked) {
                        try {
                            int fluidTankCount = context.getFluidTankCount();
                            FluidTank[] outputFluidTanks = context.getOutputFluidTanks();
                            for (int i = 0; i < fluidTankCount; i++) {
                                FluidTank tank = outputFluidTanks[i];
                                if (tank.isEmpty() || tank.getFluid().getFluid().isSame(remainingFluid.getFluid())) {
                                    tank.fill(remainingFluid, IFluidHandler.FluidAction.EXECUTE);
                                    break;
                                }
                            }
                        } finally {
                            context.getCraftingLock().unlock();
                        }
                    }
                }
            }
            context.markChanged();
        });
    }

    public void cancel() {
        this.cancelled = true;
    }

    // 辅助类用于存储输出数据
    private record OutputItem(ItemStack stack) {
    }

    private record OutputFluid(FluidStack stack) {
    }
}
