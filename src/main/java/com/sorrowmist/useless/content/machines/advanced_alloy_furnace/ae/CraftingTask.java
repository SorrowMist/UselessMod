package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.CatalystEffectResolver;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution.AlloyFurnaceRecipeExecutor;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.parallel.AlloyFurnaceParallelCalculator;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 高级合金炉中的单个 AE 合成任务。
 * 负责 AE 请求的配方匹配、批次推进、进度同步、取消返还和产物输出。
 */
public class CraftingTask {
    private static final int PROGRESS_SYNC_INTERVAL = 5;

    private final int taskId;
    private final IPatternDetails pattern;
    private final CraftingTaskContext context;
    private final List<ItemStack> taskInputItems = new ArrayList<>();
    private final List<FluidStack> taskInputFluids = new ArrayList<>();
    private final List<OutputKey> taskInputKeys = new ArrayList<>();
    private final List<CraftingSubTask> queuedSubTasks = new ArrayList<>();
    private int craftCount = 1;
    private int displayedCraftCount = 1;
    private boolean cancelled = false;
    private boolean processingComplete = false;
    private boolean initialized = false;
    private int baseProcessTime = 200;
    private int baseEnergyPerTick = 200;
    private int maxParallel = 1;
    private int batches = 1;
    private int lastBatchSize = 1;
    private int processTime = 200;
    private int progress = 0;
    private int progressUpdateCounter = 0;
    private boolean progressRegistered = false;
    private AdvancedAlloyFurnaceAeManager.AETaskProgress taskProgressRef = null;
    private String waitingDetail = "";
    // 缓存已解析的配方，避免 tick() 每帧重复查找（AE 任务的输入在批次内固定）
    private AdvancedAlloyFurnaceRecipe cachedRecipe = null;

    public CraftingTask(int taskId, IPatternDetails pattern, KeyCounter[] inputHolder, int totalCrafts,
                        CraftingTaskContext context) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
        this.craftCount = Math.max(1, totalCrafts);
        this.displayedCraftCount = this.craftCount;
        this.storeInputMaterials(inputHolder, this.taskInputItems, this.taskInputFluids, this.taskInputKeys);
    }

    public boolean isProcessingComplete() {
        return processingComplete;
    }

    public IPatternDetails getPattern() {
        return pattern;
    }

    /**
     * 直接合并一批 AE 输入（跳过中间 KeyCounter 对象分配），追加为一个子任务
     */
    public boolean addMergedBatch(List<KeyCounter[]> allInputs) {
        if (processingComplete || cancelled) {
            return false;
        }
        queuedSubTasks.add(createMergedSubTask(allInputs));
        updateDisplayedCraftCount();
        updateTaskProgressTotals();
        context.markChanged();
        context.sendAETaskProgressToClients();
        return true;
    }

    public boolean canStartNow() {
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        boolean valid = isRecipeValid(recipe);
        this.waitingDetail = "";
        return valid;
    }

    public String getWaitingDetail() {
        return this.waitingDetail;
    }

    /** 从 KeyCounter[] 创建单次子任务（仅构造时使用） */
    private CraftingSubTask createSubTask(KeyCounter[] counters, int crafts) {
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<OutputKey> keys = new ArrayList<>();
        storeInputMaterials(counters, items, fluids, keys);
        return new CraftingSubTask(items, fluids, keys, crafts);
    }

    /** 批量合并 KeyCounter 为单子任务，跳过中间 KeyCounter 分配 */
    private CraftingSubTask createMergedSubTask(List<KeyCounter[]> allInputs) {
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<OutputKey> keys = new ArrayList<>();
        for (KeyCounter[] counters : allInputs) {
            storeInputMaterials(counters, items, fluids, keys);
        }
        return new CraftingSubTask(items, fluids, keys, Math.max(1, allInputs.size()));
    }

    private void storeInputMaterials(KeyCounter[] counters, List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys) {
        if (counters == null) return;

        for (KeyCounter counter : counters) {
            if (counter == null) continue;

            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();

                if (key instanceof AEItemKey itemKey) {
                    ItemStack stack = itemKey.toStack((int) amount);
                    items.add(stack);
                } else if (key instanceof AEFluidKey fluidKey) {
                    FluidStack stack = new FluidStack(fluidKey.getFluid(), (int) amount);
                    fluids.add(stack);
                } else {
                    keys.add(new OutputKey(key, amount));
                }
            }
        }
    }

    /**
     * 使用本体模具/催化剂统一查找配方，与机器本体匹配逻辑一致。
     * 结果缓存于 cachedRecipe，避免 tick() 每帧重复查找；
     * 输入变化时（新子任务/新批次）由调用方通过 invalidateRecipeCache() 使其失效。
     */
    private AdvancedAlloyFurnaceRecipe findTaskRecipe() {
        if (context.getLevel() == null) return null;
        if (cachedRecipe != null) return cachedRecipe;

        List<ItemStack> tempInputs = new ArrayList<>(taskInputItems);
        List<FluidStack> tempFluids = new ArrayList<>(taskInputFluids);

        ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
        if (!catalystStack.isEmpty()) {
            tempInputs.add(catalystStack.copy());
        }

        ItemStack moldStack = context.getItemHandler().getStackInSlot(context.getMoldSlot());

        cachedRecipe = AlloyFurnaceRecipeManager.getInstance().findRecipe(
                context.getLevel(), tempInputs, tempFluids, toGenericStacks(taskInputKeys), moldStack
        );
        return cachedRecipe;
    }

    /** 使配方缓存失效（输入/模具/催化剂变化时调用） */
    private void invalidateRecipeCache() {
        this.cachedRecipe = null;
    }

    private String getProductName() {
        if (pattern == null || pattern.getOutputs().isEmpty()) {
            return "Unknown";
        }

        var output = pattern.getOutputs().getFirst();
        if (output.what() instanceof AEItemKey itemKey) {
            return itemKey.getDisplayName().getString();
        } else if (output.what() instanceof AEFluidKey fluidKey) {
            return fluidKey.getDisplayName().getString();
        }

        return output.what().getDisplayName().getString();
    }

    private boolean isRecipeValid(AdvancedAlloyFurnaceRecipe recipe) {
        if (context.getLevel() == null || pattern == null) {
            return false;
        }

        if (recipe == null) {
            return false;
        }

        if (pattern.getOutputs().isEmpty()) {
            return false;
        }

        if (recipe.outputs().isEmpty() && recipe.outputFluids().isEmpty() && recipe.keyOutputs().isEmpty()) {
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
            } else {
                for (GenericStack keyOutput : recipe.keyOutputs()) {
                    if (keyOutput.what().equals(patternOutput.what()) && keyOutput.amount() >= patternOutput.amount()) {
                        matched = true;
                        break;
                    }
                }
            }

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private void returnMaterialsToAE() {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        List<ItemStack> itemsToReturn = new ArrayList<>(taskInputItems);
        List<FluidStack> fluidsToReturn = new ArrayList<>(taskInputFluids);
        List<OutputKey> keysToReturn = new ArrayList<>(taskInputKeys);
        for (CraftingSubTask subTask : queuedSubTasks) {
            itemsToReturn.addAll(subTask.items);
            fluidsToReturn.addAll(subTask.fluids);
            keysToReturn.addAll(subTask.keys);
        }
        taskInputItems.clear();
        taskInputFluids.clear();
        taskInputKeys.clear();
        queuedSubTasks.clear();

        for (OutputKey keyToReturn : keysToReturn) {
            FurnaceOutputPort.outputKey(keyToReturn.key, keyToReturn.amount, createAeOutputPort());
        }

        for (ItemStack stack : itemsToReturn) {
            FurnaceOutputPort.outputItem(stack, createAeOutputPort(), context.getItemHandler(), context.getInputSlotsStart(), context.getInputSlotsCount());
        }

        for (FluidStack fluidStack : fluidsToReturn) {
            FurnaceOutputPort.outputFluid(fluidStack, createAeOutputPort(), context.getInputFluidTanks(), context.getFluidTankCount());
        }
        context.markChanged();
    }

    private int getRecipeProcessTime(AdvancedAlloyFurnaceRecipe recipe) {
        if (recipe != null && recipe.processTime() > 0) {
            ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
            return CatalystEffectResolver.resolve(recipe, catalystStack, recipe.processTime()).processTime();
        }

        return 200;
    }

    public boolean tick() {
        if (processingComplete) {
            return true;
        }
        if (cancelled || context.getLevel() == null || context.getLevel().isClientSide) {
            processingComplete = true;
            return true;
        }
        if (!initialized && !initialize()) {
            return true;
        }

        int batchIndex = baseProcessTime > 0 ? progress / baseProcessTime : 0;
        int actualBatchParallel = batchIndex < batches - 1 ? maxParallel : lastBatchSize;
        ResolvedCatalystEffect resolvedCatalystEffect = getCatalystEffect(findTaskRecipe());
        AlloyFurnaceRecipeExecutor.TickResult tickResult = AlloyFurnaceRecipeExecutor.consumeTickEnergy(context.getEnergyManager(), baseEnergyPerTick, actualBatchParallel,
                                                                                                        resolvedCatalystEffect
        );

        if (!tickResult.consumedEnergy()) {
            return false;
        }

        progress++;
        context.getTotalAEProgressAtomic().incrementAndGet();
        if (taskProgressRef != null) {
            taskProgressRef.setProgress(progress);
        }
        context.markChanged();

        progressUpdateCounter++;
        if (progressUpdateCounter >= PROGRESS_SYNC_INTERVAL) {
            context.sendAETaskProgressToClients();
            progressUpdateCounter = 0;
        }

        if (progress >= processTime) {
            completeCrafting(craftCount);
            displayedCraftCount -= craftCount;
            updateTaskProgressTotals();
            taskInputItems.clear();
            taskInputFluids.clear();
            taskInputKeys.clear();
            if (startNextSubTask()) {
                context.markChanged();
                context.sendAETaskProgressToClients();
                return false;
            }
            finishTask();
            return true;
        }
        return false;
    }

    private boolean initialize() {
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        if (!isRecipeValid(recipe)) {
            this.waitingDetail = "";
            this.updateWaitingProgress();
            return false;
        }

        baseProcessTime = getRecipeProcessTime(recipe);
        if (recipe != null && recipe.processTime() > 0) {
            baseEnergyPerTick = Math.max(1, recipe.energy() / Math.max(1, recipe.processTime()));
        } else {
            baseEnergyPerTick = 200;
        }

        ResolvedCatalystEffect resolvedCatalystEffect = getCatalystEffect(recipe);
        maxParallel = AlloyFurnaceParallelCalculator.calculateAeTaskParallel(context.getEnergyManager(), baseEnergyPerTick,
                                                                             resolvedCatalystEffect
        );

        recalculateBatchLayout();

        int outputCount = 1;
        if (pattern != null && !pattern.getOutputs().isEmpty()) {
            var output = pattern.getOutputs().getFirst();
            outputCount = (int) output.amount();
        }

        int totalOutputCount = displayedCraftCount * outputCount;
        taskProgressRef = new AdvancedAlloyFurnaceAeManager.AETaskProgress(getProductName(), processTime, displayedCraftCount, totalOutputCount);
        this.waitingDetail = "";
        context.getAETaskProgressMap().put(taskId, taskProgressRef);
        context.getTotalAEMaxProgressAtomic().addAndGet(processTime);
        progressRegistered = true;
        initialized = true;
        context.markChanged();
        context.sendAETaskProgressToClients();
        return true;
    }

    private void updateWaitingProgress() {
        int outputCount = 1;
        if (pattern != null && !pattern.getOutputs().isEmpty()) {
            outputCount = (int) pattern.getOutputs().getFirst().amount();
        }
        int totalOutputCount = displayedCraftCount * outputCount;
        taskProgressRef = new AdvancedAlloyFurnaceAeManager.AETaskProgress(getProductName(), 0, 1, displayedCraftCount, totalOutputCount,
                "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_mold", this.waitingDetail);
        context.getAETaskProgressMap().put(taskId, taskProgressRef);
        context.markChanged();
        context.sendAETaskProgressToClients();
    }

    private void recalculateBatchLayout() {
        batches = (int) Math.ceil((double) craftCount / maxParallel);
        processTime = Math.max(1, baseProcessTime * batches);
        lastBatchSize = craftCount - maxParallel * (batches - 1);
    }

    private void updateDisplayedCraftCount() {
        this.displayedCraftCount = this.craftCount;
        for (CraftingSubTask subTask : queuedSubTasks) {
            this.displayedCraftCount += subTask.craftCount;
        }
    }

    private void updateTaskProgressTotals() {
        if (taskProgressRef != null) {
            taskProgressRef.updateCraftCount(displayedCraftCount);
            taskProgressRef.setStatus("gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        }
    }

    private boolean startNextSubTask() {
        if (queuedSubTasks.isEmpty()) {
            return false;
        }
        if (progressRegistered) {
            context.getTotalAEProgressAtomic().addAndGet(-progress);
            context.getTotalAEMaxProgressAtomic().addAndGet(-processTime);
        }
        CraftingSubTask subTask = queuedSubTasks.removeFirst();
        taskInputItems.clear();
        taskInputItems.addAll(subTask.items);
        taskInputFluids.clear();
        taskInputFluids.addAll(subTask.fluids);
        taskInputKeys.clear();
        taskInputKeys.addAll(subTask.keys);
        invalidateRecipeCache(); // 输入已变更，配方缓存失效
        craftCount = subTask.craftCount;
        progress = 0;
        recalculateBatchLayout();
        if (progressRegistered) {
            context.getTotalAEMaxProgressAtomic().addAndGet(processTime);
        }
        if (taskProgressRef != null) {
            taskProgressRef.setProgress(0);
            taskProgressRef.setMaxProgress(processTime);
        }
        return true;
    }

    private ResolvedCatalystEffect getCatalystEffect(AdvancedAlloyFurnaceRecipe recipe) {
        ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
        int baseTime = recipe != null ? recipe.processTime() : 200;
        return CatalystEffectResolver.resolve(recipe, catalystStack, baseTime);
    }

    private void finishTask() {
        processingComplete = true;
        if (progressRegistered) {
            context.getTotalAEProgressAtomic().addAndGet(-progress);
            context.getTotalAEMaxProgressAtomic().addAndGet(-processTime);
            progressRegistered = false;
        }
        context.getAETaskProgressMap().remove(taskId);
        context.markChanged();
        context.sendAETaskProgressToClients();
    }

    private void completeCrafting(int craftCount) {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        List<OutputFluid> outputFluids = new ArrayList<>();
        List<OutputKey> outputKeys = new ArrayList<>();

        for (var output : pattern.getOutputs()) {
            if (output.what() instanceof AEItemKey itemKey) {
                ItemStack outputStack = itemKey.toStack((int) (output.amount() * craftCount));
                FurnaceOutputPort.outputItem(outputStack, createAeOutputPort(), context.getItemHandler(), context.getOutputSlotsStart(), context.getOutputSlotsCount());
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                FluidStack outputFluid = new FluidStack(fluidKey.getFluid(), (int) (output.amount() * craftCount));
                outputFluids.add(new OutputFluid(outputFluid));
            } else {
                outputKeys.add(new OutputKey(output.what(), output.amount() * craftCount));
            }
        }

        if (recipe != null) {
            for (GenericStack keyOutput : recipe.keyOutputs()) {
                outputKeys.add(new OutputKey(keyOutput.what(), keyOutput.amount() * craftCount));
            }
        }

        for (OutputFluid outputFluid : outputFluids) {
            FurnaceOutputPort.outputFluid(outputFluid.stack, createAeOutputPort(), context.getOutputFluidTanks(), context.getFluidTankCount());
        }

        for (OutputKey outputKey : outputKeys) {
            FurnaceOutputPort.outputKey(outputKey.key, outputKey.amount, createAeOutputPort());
        }
        context.markChanged();
    }

    private FurnaceOutputPort.AeOutput createAeOutputPort() {
        return new FurnaceOutputPort.AeOutput() {
            @Override
            public long insertItem(ItemStack stack) {
                return context.tryOutputToAE(stack);
            }

            @Override
            public long insertFluid(FluidStack stack) {
                return context.tryOutputFluidToAE(stack);
            }

            @Override
            public long insertKey(AEKey key, long amount) {
                return context.tryOutputKeyToAE(key, amount);
            }
        };
    }

    public void cancel() {
        if (processingComplete) {
            return;
        }
        this.cancelled = true;
        returnMaterialsToAE();
        finishTask();
    }

    // 辅助类用于存储输出数据
    private record OutputFluid(FluidStack stack) {
    }

    private record OutputKey(AEKey key, long amount) {
    }

    private record CraftingSubTask(List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys, int craftCount) {
    }

    private List<GenericStack> toGenericStacks(List<OutputKey> keys) {
        List<GenericStack> stacks = new ArrayList<>();
        for (OutputKey key : keys) {
            stacks.add(new GenericStack(key.key, key.amount));
        }
        return stacks;
    }

}
