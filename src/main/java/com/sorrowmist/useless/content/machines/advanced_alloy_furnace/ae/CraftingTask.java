package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.execution.AlloyFurnaceRecipeExecutor;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 高级合金炉中的单个 AE 合成任务。
 * 负责 AE 请求的配方匹配、批次推进、进度同步、取消返还和产物输出。
 */
public class CraftingTask {
    private static final int PROGRESS_SYNC_INTERVAL = 5;
    private static final String STATUS_PROCESSING =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing";
    private static final String STATUS_WAITING_RECIPE =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_recipe";
    private static final String STATUS_WAITING_INVALID_PATTERN =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_invalid_pattern";
    private static final String STATUS_WAITING_OUTPUT_MISMATCH =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_output_mismatch";
    private static final String STATUS_WAITING_CONDITIONS =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_conditions";
    private static final String STATUS_WAITING_ENERGY =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_energy";
    private static final String WAITING_ENERGY_DETAIL =
            "gui.useless_mod.advanced_alloy_furnace.ae_task_waiting_energy_detail";

    private final int taskId;
    private final IPatternDetails pattern;
    private final CraftingTaskContext context;
    private final List<ItemStack> taskInputItems = new ArrayList<>();
    private final List<FluidStack> taskInputFluids = new ArrayList<>();
    private final List<OutputKey> taskInputKeys = new ArrayList<>();
    private Set<AEKey> componentInputKeys = Set.of();
    private final List<CraftingSubTask> queuedSubTasks = new ArrayList<>();
    // 待输出缓冲：产物先进缓冲，由 flushPendingOutputs 逐 tick 写出，写不下时保留重试，避免静默丢失
    private final List<ItemStack> pendingOutputItems = new ArrayList<>();
    private final List<FluidStack> pendingOutputFluids = new ArrayList<>();
    private final List<OutputKey> pendingOutputKeys = new ArrayList<>();
    private boolean awaitingOutputFlush = false;
    // 当前子任务已扣除的能量，用于完成结算（与本体 settleCompletionEnergy 语义对齐）
    private long accumulatedEnergy = 0;
    private long craftCount = 1L;
    private long displayedCraftCount = 1L;
    // A manually scaled AE pattern may represent multiple base recipe operations per push.
    private long patternOperationsPerPush = 1L;
    private boolean patternOperationsResolved = false;
    private boolean cancelled = false;
    private boolean processingComplete = false;
    private boolean initialized = false;
    private int baseProcessTime = 200;
    private long baseEnergyPerTick = 200L;
    private long maxParallel = 1L;
    private int batches = 1;
    private long lastBatchSize = 1L;
    private int processTime = 200;
    private int progress = 0;
    private int progressUpdateCounter = 0;
    private boolean progressRegistered = false;
    private AdvancedAlloyFurnaceAeManager.AETaskProgress taskProgressRef = null;
    private String waitingStatusKey = STATUS_WAITING_CONDITIONS;
    private String waitingDetail = "";
    // 缓存已解析的配方，避免 tick() 每帧重复查找（AE 任务的输入在批次内固定）
    private AdvancedAlloyFurnaceRecipe cachedRecipe = null;

    public CraftingTask(int taskId, IPatternDetails pattern, KeyCounter[] inputHolder, long totalCrafts,
                        CraftingTaskContext context) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
        this.craftCount = Math.max(1L, totalCrafts);
        this.displayedCraftCount = this.craftCount;
        storeInputMaterials(inputHolder, this.taskInputItems, this.taskInputFluids,
                this.taskInputKeys, context != null && context.supportsLongAeAmounts());
        this.componentInputKeys = AdvancedAlloyFurnacePatternPolicy.componentInputKeys(pattern, inputHolder);
    }

    /** 从一个拆分出的子任务构造独立任务（用于空闲线程再分配） */
    private CraftingTask(int taskId, IPatternDetails pattern, CraftingTaskContext context,
                         CraftingSubTask subTask, long patternOperationsPerPush) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
        this.craftCount = Math.max(1L, subTask.craftCount);
        this.displayedCraftCount = this.craftCount;
        this.patternOperationsPerPush = Math.max(1L, patternOperationsPerPush);
        this.patternOperationsResolved = true;
        this.taskInputItems.addAll(subTask.items);
        this.taskInputFluids.addAll(subTask.fluids);
        this.taskInputKeys.addAll(subTask.keys);
        this.componentInputKeys = resolveComponentInputKeys();
    }

    /** 用于从 NBT 恢复的空任务构造 */
    private CraftingTask(int taskId, IPatternDetails pattern, CraftingTaskContext context) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
    }


    public boolean isProcessingComplete() {
        return processingComplete;
    }

    public boolean isAwaitingOutputFlush() {
        return awaitingOutputFlush;
    }

    public IPatternDetails getPattern() {
        return pattern;
    }

    Set<AEKey> getComponentInputKeys() {
        return componentInputKeys;
    }

    /**
     * 直接合并一批 AE 输入（跳过中间 KeyCounter 对象分配），追加为一个子任务
     */
    public boolean addMergedBatch(List<KeyCounter[]> allInputs, long operationsPerPush) {
        if (processingComplete || cancelled) {
            return false;
        }
        CraftingSubTask merged = createMergedSubTask(allInputs, operationsPerPush);
        if (queuedSubTasks.isEmpty()) {
            queuedSubTasks.add(merged);
        } else {
            // 并入队尾尚未启动的子任务，减少批次数（每个子任务独立收一份结算能量）
            CraftingSubTask last = queuedSubTasks.getLast();
            last.items.addAll(merged.items);
            last.fluids.addAll(merged.fluids);
            last.keys.addAll(merged.keys);
            long combined = saturatingAdd(last.craftCount, merged.craftCount);
            queuedSubTasks.set(queuedSubTasks.size() - 1, new CraftingSubTask(last.items, last.fluids, last.keys, combined));
        }
        updateDisplayedCraftCount();
        updateTaskProgressTotals();
        context.markChanged();
        context.sendAETaskProgressToClients();
        return true;
    }

    public boolean canStartNow() {
        return prepareTaskRecipe() != null;
    }

    public int getTaskId() {
        return this.taskId;
    }

    /** 是否还有排队中的子任务可拆分到空闲线程 */
    public boolean hasQueuedSubTasks() {
        return !this.processingComplete && !this.cancelled && !this.queuedSubTasks.isEmpty();
    }

    /**
     * 拆出队尾的一个子任务，构造为独立任务在空闲线程运行。
     * 当前任务保留正在处理的批次不受影响。
     *
     * @param newTaskId 新任务 ID
     * @return 拆分出的独立任务，若无可拆分子任务则返回 null
     */
    @Nullable
    public CraftingTask splitLastSubTask(int newTaskId) {
        if (!hasQueuedSubTasks()) {
            return null;
        }
        CraftingSubTask subTask = queuedSubTasks.removeLast();
        updateDisplayedCraftCount();
        updateTaskProgressTotals();
        context.markChanged();
        context.sendAETaskProgressToClients();
        return new CraftingTask(newTaskId, pattern, context, subTask, patternOperationsPerPush);
    }

    public String getWaitingDetail() {
        return this.waitingDetail;
    }

    public String getWaitingStatusKey() {
        return this.waitingStatusKey;
    }

    /** 从 KeyCounter[] 创建单次子任务（仅构造时使用） */
    private CraftingSubTask createSubTask(KeyCounter[] counters, long crafts) {
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<OutputKey> keys = new ArrayList<>();
        storeInputMaterials(counters, items, fluids, keys, context.supportsLongAeAmounts());
        return new CraftingSubTask(items, fluids, keys, crafts);
    }

    /** 批量合并 KeyCounter 为单子任务，跳过中间 KeyCounter 分配 */
    private CraftingSubTask createMergedSubTask(
            List<KeyCounter[]> allInputs, long operationsPerPush) {
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<OutputKey> keys = new ArrayList<>();
        for (KeyCounter[] counters : allInputs) {
            storeInputMaterials(counters, items, fluids, keys, context.supportsLongAeAmounts());
        }
        long operations = saturatingMultiply(
                saturatingMultiply(Math.max(1L, allInputs.size()), Math.max(1L, operationsPerPush)),
                patternOperationsPerPush);
        return new CraftingSubTask(items, fluids, keys, operations);
    }

    private static void storeInputMaterials(
            KeyCounter[] counters, List<ItemStack> items, List<FluidStack> fluids,
            List<OutputKey> keys, boolean keepLongAmounts) {
        if (counters == null) return;

        for (KeyCounter counter : counters) {
            if (counter == null) continue;

            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (keepLongAmounts) {
                    keys.add(new OutputKey(key, amount));
                    continue;
                }

                // ItemStack/FluidStack 数量是 int：512M 级请求直接强转会溢出为负，
                // 导致材料变空、配方匹配失败。超出部分按 int 上限分块存放。
                if (key instanceof AEItemKey itemKey) {
                    long remaining = amount;
                    while (remaining > 0) {
                        int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                        items.add(itemKey.toStack(chunk));
                        remaining -= chunk;
                    }
                } else if (key instanceof AEFluidKey fluidKey) {
                    long remaining = amount;
                    while (remaining > 0) {
                        int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                        fluids.add(new FluidStack(fluidKey.getFluid(), chunk));
                        remaining -= chunk;
                    }
                } else {
                    keys.add(new OutputKey(key, amount));
                }
            }
        }
    }

    /**
     * 使用本体模具与样板目标输出统一查找配方。
     * 结果缓存于 cachedRecipe，避免 tick() 每帧重复查找；
     * 输入变化时（新子任务/新批次）由调用方通过 invalidateRecipeCache() 使其失效。
     */
    private AdvancedAlloyFurnaceRecipe findTaskRecipe() {
        if (context.getLevel() == null) return null;
        if (cachedRecipe != null) return cachedRecipe;

        List<ItemStack> tempInputs = new ArrayList<>(taskInputItems);
        List<FluidStack> tempFluids = new ArrayList<>(taskInputFluids);

        cachedRecipe = context.resolveTaskRecipe(
                pattern, tempInputs, tempFluids, toGenericStacks(taskInputKeys), craftCount);
        return cachedRecipe;
    }

    @Nullable
    private AdvancedAlloyFurnaceRecipe prepareTaskRecipe() {
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        if (!isRecipeValid(recipe) || !resolvePatternOperations(recipe)) {
            return null;
        }
        return recipe;
    }

    /**
     * A normal AE processing pattern has no operation-count metadata. When a player manually
     * scales its inputs and outputs, derive the scale from the declared outputs and charge the
     * corresponding number of base recipe operations instead of treating the whole pattern as one.
     */
    private boolean resolvePatternOperations(AdvancedAlloyFurnaceRecipe recipe) {
        if (patternOperationsResolved) {
            return true;
        }

        ManualPatternOperationResolver.Resolution resolution =
                ManualPatternOperationResolver.resolve(recipe, pattern);
        if (!resolution.valid()) {
            setWaiting(STATUS_WAITING_INVALID_PATTERN, "");
            return false;
        }

        long resolvedCraftCount = saturatingMultiply(craftCount, resolution.operationsPerPattern());
        RecipeInputs inputs = materializeRecipeInputs();
        if (!AlloyFurnaceRecipeManager.matchesInputsForOperations(
                recipe, inputs.items(), inputs.fluids(), inputs.keys(), resolvedCraftCount)) {
            setWaiting(STATUS_WAITING_INVALID_PATTERN, "");
            return false;
        }

        patternOperationsPerPush = resolution.operationsPerPattern();
        patternOperationsResolved = true;
        craftCount = resolvedCraftCount;
        updateDisplayedCraftCount();
        return true;
    }

    private RecipeInputs materializeRecipeInputs() {
        List<ItemStack> items = new ArrayList<>(taskInputItems);
        List<FluidStack> fluids = new ArrayList<>(taskInputFluids);
        List<GenericStack> keys = new ArrayList<>();
        for (OutputKey input : taskInputKeys) {
            long remaining = input.amount;
            if (input.key instanceof AEItemKey itemKey) {
                while (remaining > 0L) {
                    int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                    items.add(itemKey.toStack(chunk));
                    remaining -= chunk;
                }
            } else if (input.key instanceof AEFluidKey fluidKey) {
                while (remaining > 0L) {
                    int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                    fluids.add(new FluidStack(fluidKey.getFluid(), chunk));
                    remaining -= chunk;
                }
            } else if (remaining > 0L) {
                keys.add(new GenericStack(input.key, remaining));
            }
        }
        return new RecipeInputs(items, fluids, keys);
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
            setWaiting(pattern == null ? STATUS_WAITING_INVALID_PATTERN : STATUS_WAITING_CONDITIONS, "");
            return false;
        }

        if (recipe == null) {
            setWaiting(STATUS_WAITING_RECIPE, "");
            return false;
        }

        if (pattern.getOutputs().isEmpty()) {
            setWaiting(STATUS_WAITING_INVALID_PATTERN, "");
            return false;
        }

        CraftingTaskContext.TaskAvailability availability = context.getTaskAvailability(recipe);
        if (!availability.available()) {
            setWaiting(availability.statusKey(), availability.statusDetail());
            return false;
        }

        if (!AlloyFurnaceRecipeManager.matchesOutputConstraints(
                recipe, AdvancedAlloyFurnacePatternPolicy.outputConstraints(pattern))) {
            setWaiting(STATUS_WAITING_OUTPUT_MISMATCH, "");
            return false;
        }

        clearWaiting();
        return true;
    }

    private void setWaiting(String statusKey, String detail) {
        this.waitingStatusKey = statusKey == null || statusKey.isBlank()
                ? STATUS_WAITING_CONDITIONS : statusKey;
        this.waitingDetail = detail == null ? "" : detail;
    }

    private void clearWaiting() {
        this.waitingStatusKey = STATUS_PROCESSING;
        this.waitingDetail = "";
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
        // 已结算但尚未写出的产物同样不能丢，随取消一并返还
        itemsToReturn.addAll(pendingOutputItems);
        fluidsToReturn.addAll(pendingOutputFluids);
        keysToReturn.addAll(pendingOutputKeys);
        pendingOutputItems.clear();
        pendingOutputFluids.clear();
        pendingOutputKeys.clear();
        awaitingOutputFlush = false;
        taskInputItems.clear();
        taskInputFluids.clear();
        taskInputKeys.clear();
        queuedSubTasks.clear();

        returnMaterials(context, itemsToReturn, fluidsToReturn, keysToReturn);
    }

    /** 返还尚未转为任务的 AE 原始输入（待启动批次取消时使用） */
    public static void returnInputsToAE(List<KeyCounter[]> allInputs, CraftingTaskContext context) {
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<OutputKey> keys = new ArrayList<>();
        for (KeyCounter[] counters : allInputs) {
            storeInputMaterials(counters, items, fluids, keys, context.supportsLongAeAmounts());
        }
        returnMaterials(context, items, fluids, keys);
    }

    /**
     * 返还材料，与产物一致地尊重“产物返回AE”开关：开关开启时优先写回 AE 网络，
     * 其次本地输入槽/流体槽。任何路径都不允许静默丢失 ——
     * 物品放不下时掉落到机器上方；流体最后尝试输出流体槽，仍有剩余则进暂存缓冲重试；
     * key 类材料（如化学品）没有本地槽位可回退，无视开关直接尝试 AE，失败部分进暂存缓冲。
     */
    private static void returnMaterials(CraftingTaskContext context, List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys) {
        Level level = context.getLevel();
        if (level == null || level.isClientSide) return;
        if (items.isEmpty() && fluids.isEmpty() && keys.isEmpty()) return;

        FurnaceOutputPort.AeOutput port = context.createAeOutputPort();

        for (OutputKey keyToReturn : keys) {
            long inserted = context.tryOutputKeyToAE(keyToReturn.key, keyToReturn.amount);
            long remaining = keyToReturn.amount - inserted;
            if (remaining > 0) {
                context.stashUnreturnedInput(keyToReturn.key, remaining);
            }
        }

        for (ItemStack stack : items) {
            ItemStack leftover = FurnaceOutputPort.outputItemWithRemainder(stack, port,
                    context.getItemHandler(), context.getInputSlotsStart(), context.getInputSlotsCount());
            if (!leftover.isEmpty()) {
                context.handleUnreturnedItem(leftover);
            }
        }

        for (FluidStack fluidStack : fluids) {
            FluidStack leftover = FurnaceOutputPort.outputFluidWithRemainder(fluidStack, port,
                    context.getInputFluidTanks(), context.getFluidTankCount());
            if (!leftover.isEmpty()) {
                leftover = FurnaceOutputPort.outputFluidWithRemainder(leftover, port,
                        context.getOutputFluidTanks(), context.getFluidTankCount());
            }
            if (!leftover.isEmpty()) {
                context.handleUnreturnedFluid(leftover);
            }
        }
        context.markChanged();
    }

    private int getRecipeProcessTime(AdvancedAlloyFurnaceRecipe recipe) {
        return context.getTaskProcessTime(recipe, context.resolveTaskEffect(recipe));
    }

    public boolean tick() {
        if (processingComplete) {
            return true;
        }
        if (cancelled || context.getLevel() == null || context.getLevel().isClientSide) {
            processingComplete = true;
            return true;
        }
        if (!context.isTaskExecutionEnabled()) return false;
        if (!initialized && !initialize()) {
            return true;
        }

        if (!awaitingOutputFlush) {
            AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
            ResolvedCatalystEffect resolvedCatalystEffect = getCatalystEffect(recipe);

            if (progress == 0 && accumulatedEnergy == 0L && !isRecipeValid(recipe)) {
                updateWaitingProgress();
                return false;
            } else if (taskProgressRef != null && !STATUS_WAITING_ENERGY.equals(waitingStatusKey)) {
                taskProgressRef.setStatus(STATUS_PROCESSING, "");
            }

            if (progress < processTime) {
                ProgressEnergyStep energyStep = getProgressEnergyStep(recipe, resolvedCatalystEffect);
                AlloyFurnaceRecipeExecutor.TickResult tickResult =
                        AlloyFurnaceRecipeExecutor.consumeProgressEnergy(
                                context.getEnergyManager(), energyStep.targetEnergy(),
                                energyStep.progress(), energyStep.duration(), accumulatedEnergy);
                accumulatedEnergy += tickResult.energyConsumed();
                if (!tickResult.progressAdvanced()) {
                    if (tickResult.energyConsumed() > 0L) {
                        context.markChanged();
                    }
                    setWaiting(STATUS_WAITING_ENERGY, WAITING_ENERGY_DETAIL);
                    if (taskProgressRef != null) {
                        taskProgressRef.setStatus(waitingStatusKey, waitingDetail);
                        context.sendAETaskProgressToClients();
                    }
                    return false;
                }

                clearWaiting();
                progress++;
                if (energyStep.resetAfterAdvance()
                        && energyStep.progress() + 1 >= energyStep.duration()) {
                    accumulatedEnergy = 0L;
                }
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

                if (progress < processTime) {
                    return false;
                }
            }

            generatePendingOutputs(craftCount);
            taskInputItems.clear();
            taskInputFluids.clear();
            taskInputKeys.clear();
            awaitingOutputFlush = true;
        }

        // 产物写不下时保留在缓冲中逐 tick 重试，避免静默丢失
        if (!flushPendingOutputs()) {
            return false;
        }
        awaitingOutputFlush = false;

        displayedCraftCount -= craftCount;
        updateTaskProgressTotals();
        if (startNextSubTask()) {
            context.markChanged();
            context.sendAETaskProgressToClients();
            return false;
        }
        finishTask();
        return true;
    }

    private ProgressEnergyStep getProgressEnergyStep(
            @Nullable AdvancedAlloyFurnaceRecipe recipe,
            ResolvedCatalystEffect resolvedCatalystEffect) {
        long recipeEnergy = recipe != null
                ? Math.max(0L, recipe.energy())
                : saturatingMultiply(baseEnergyPerTick, Math.max(1, baseProcessTime));
        long targetEnergy = AlloyFurnaceRecipeExecutor.calculateTargetTotalEnergy(
                recipeEnergy, craftCount, resolvedCatalystEffect);
        return new ProgressEnergyStep(targetEnergy, progress, processTime, false);
    }

    private boolean initialize() {
        AdvancedAlloyFurnaceRecipe recipe = prepareTaskRecipe();
        if (recipe == null) {
            this.updateWaitingProgress();
            return false;
        }

        refreshCatalystLayout();
        recalculateBatchLayout();

        long totalOutputCount = calculateDisplayedOutputCount();
        taskProgressRef = new AdvancedAlloyFurnaceAeManager.AETaskProgress(getProductName(), processTime, displayedCraftCount, totalOutputCount);
        clearWaiting();
        context.getAETaskProgressMap().put(taskId, taskProgressRef);
        context.getTotalAEMaxProgressAtomic().addAndGet(processTime);
        progressRegistered = true;
        initialized = true;
        context.markChanged();
        context.sendAETaskProgressToClients();
        return true;
    }

    private void updateWaitingProgress() {
        long totalOutputCount = calculateDisplayedOutputCount();
        taskProgressRef = new AdvancedAlloyFurnaceAeManager.AETaskProgress(getProductName(), 0, 1, displayedCraftCount, totalOutputCount,
                waitingStatusKey, waitingDetail);
        context.getAETaskProgressMap().put(taskId, taskProgressRef);
        context.markChanged();
        context.sendAETaskProgressToClients();
    }

    private long calculateDisplayedOutputCount() {
        long outputCount = 1L;
        if (pattern != null && !pattern.getOutputs().isEmpty()) {
            outputCount = Math.max(1L, pattern.getOutputs().getFirst().amount());
        }
        return saturatingMultiply(outputCount,
                calculatePatternOutputRuns(displayedCraftCount, patternOperationsPerPush));
    }

    static long calculatePatternOutputRuns(long operations, long operationsPerPattern) {
        long safeOperations = Math.max(1L, operations);
        long safePatternOperations = Math.max(1L, operationsPerPattern);
        return Math.max(1L, safeOperations / safePatternOperations);
    }

    private void recalculateBatchLayout() {
        long parallel = Math.max(1L, maxParallel);
        long batchesLong = 1L + (Math.max(1L, craftCount) - 1L) / parallel;
        batches = (int) Math.min(batchesLong, Integer.MAX_VALUE);
        long processTimeLong = saturatingMultiply(Math.max(1, baseProcessTime), batchesLong);
        processTime = (int) Math.min(processTimeLong, Integer.MAX_VALUE);
        lastBatchSize = 1L + (Math.max(1L, craftCount) - 1L) % parallel;
    }

    private void updateDisplayedCraftCount() {
        this.displayedCraftCount = this.craftCount;
        for (CraftingSubTask subTask : queuedSubTasks) {
            this.displayedCraftCount = saturatingAdd(this.displayedCraftCount, subTask.craftCount);
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
        componentInputKeys = resolveComponentInputKeys();
        invalidateRecipeCache(); // 输入已变更，配方缓存失效
        craftCount = subTask.craftCount;
        progress = 0;
        accumulatedEnergy = 0;
        refreshCatalystLayout(); // 催化剂可能在执行期间被更换，按当前催化剂重算批次布局
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
        return context.resolveTaskEffect(recipe);
    }

    /** 按当前催化剂重算时长、每 tick 能耗与并行上限（初始化及每个子任务启动时调用） */
    private void refreshCatalystLayout() {
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        baseProcessTime = getRecipeProcessTime(recipe);
        if (recipe != null && recipe.processTime() > 0) {
            baseEnergyPerTick = Math.max(1L, recipe.energy() / Math.max(1, recipe.processTime()));
        } else {
            baseEnergyPerTick = 200L;
        }
        maxParallel = context.getTaskParallel(recipe, getCatalystEffect(recipe));
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

    /**
     * 生成本批产物到待输出缓冲。数量全程 long 分块，防止 512M 级请求 int 溢出。
     * 实际写出由 flushPendingOutputs 逐 tick 执行。
     */
    private void generatePendingOutputs(long craftCount) {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        if (recipe != null && AdvancedAlloyFurnacePatternPolicy.usesRecipeOutputs(pattern)) {
            for (ItemStack output : recipe.outputs()) {
                addPendingItemOrKey(output, saturatingMultiply(output.getCount(), craftCount));
            }
            for (FluidStack output : recipe.outputFluids()) {
                addPendingFluidOrKey(output, saturatingMultiply(output.getAmount(), craftCount));
            }
            for (GenericStack output : recipe.keyOutputs()) {
                if (output != null && output.what() != null && output.amount() > 0) {
                    pendingOutputKeys.add(new OutputKey(
                            output.what(), saturatingMultiply(output.amount(), craftCount)));
                }
            }
            context.markChanged();
            return;
        }

        long patternOutputRuns = calculatePatternOutputRuns(craftCount, patternOperationsPerPush);
        for (var output : pattern.getOutputs()) {
            if (context.supportsLongAeAmounts()) {
                pendingOutputKeys.add(new OutputKey(
                        output.what(), saturatingMultiply(output.amount(), patternOutputRuns)));
            } else if (output.what() instanceof AEItemKey itemKey) {
                addPendingItem(itemKey.toStack(), saturatingMultiply(output.amount(), patternOutputRuns));
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                long remaining = saturatingMultiply(output.amount(), patternOutputRuns);
                while (remaining > 0) {
                    int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                    pendingOutputFluids.add(new FluidStack(fluidKey.getFluid(), chunk));
                    remaining -= chunk;
                }
            } else {
                pendingOutputKeys.add(new OutputKey(
                        output.what(), saturatingMultiply(output.amount(), patternOutputRuns)));
            }
        }

        if (recipe != null) {
            for (GenericStack keyOutput : recipe.keyOutputs()) {
                // 样板输出已包含该 key（走上面的 else 分支输出），跳过以避免重复产出
                boolean alreadyInPattern = false;
                for (var patternOutput : pattern.getOutputs()) {
                    if (keyOutput.what().equals(patternOutput.what())) {
                        alreadyInPattern = true;
                        break;
                    }
                }
                if (!alreadyInPattern) {
                    pendingOutputKeys.add(new OutputKey(
                            keyOutput.what(), saturatingMultiply(keyOutput.amount(), craftCount)));
                }
            }
        }
        context.markChanged();
    }

    private void addPendingItem(ItemStack template, long amount) {
        long remaining = amount;
        while (!template.isEmpty() && remaining > 0) {
            int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
            pendingOutputItems.add(template.copyWithCount(chunk));
            remaining -= chunk;
        }
    }

    private void addPendingItemOrKey(ItemStack template, long amount) {
        AEItemKey key = AEItemKey.of(template);
        if (context.supportsLongAeAmounts() && key != null) {
            pendingOutputKeys.add(new OutputKey(key, amount));
        } else {
            addPendingItem(template, amount);
        }
    }

    private void addPendingFluid(FluidStack template, long amount) {
        long remaining = amount;
        while (!template.isEmpty() && remaining > 0) {
            int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
            pendingOutputFluids.add(template.copyWithAmount(chunk));
            remaining -= chunk;
        }
    }

    private void addPendingFluidOrKey(FluidStack template, long amount) {
        AEFluidKey key = AEFluidKey.of(template);
        if (context.supportsLongAeAmounts() && key != null) {
            pendingOutputKeys.add(new OutputKey(key, amount));
        } else {
            addPendingFluid(template, amount);
        }
    }

    /**
     * 尝试把待输出缓冲写入 AE 网络/本地槽位，写不下的部分保留在缓冲中下 tick 重试。
     *
     * @return 是否已全部写出
     */
    private boolean flushPendingOutputs() {
        FurnaceOutputPort.AeOutput port = context.createAeOutputPort();

        for (int i = 0; i < pendingOutputItems.size(); ) {
            ItemStack leftover = FurnaceOutputPort.outputItemWithRemainder(pendingOutputItems.get(i), port,
                    context.getItemHandler(), context.getOutputSlotsStart(), context.getOutputSlotsCount());
            if (leftover.isEmpty()) {
                pendingOutputItems.remove(i);
            } else {
                pendingOutputItems.set(i, leftover);
                i++;
            }
        }

        for (int i = 0; i < pendingOutputFluids.size(); ) {
            FluidStack leftover = FurnaceOutputPort.outputFluidWithRemainder(pendingOutputFluids.get(i), port,
                    context.getOutputFluidTanks(), context.getFluidTankCount());
            if (leftover.isEmpty()) {
                pendingOutputFluids.remove(i);
            } else {
                pendingOutputFluids.set(i, leftover);
                i++;
            }
        }

        for (int i = 0; i < pendingOutputKeys.size(); ) {
            OutputKey pending = pendingOutputKeys.get(i);
            long inserted = port.insertKey(pending.key, pending.amount);
            if (inserted >= pending.amount) {
                pendingOutputKeys.remove(i);
            } else {
                pendingOutputKeys.set(i, new OutputKey(pending.key, pending.amount - Math.max(0, inserted)));
                i++;
            }
        }

        boolean flushed = pendingOutputItems.isEmpty() && pendingOutputFluids.isEmpty() && pendingOutputKeys.isEmpty();
        context.markChanged();
        return flushed;
    }

    public void cancel() {
        if (processingComplete) {
            return;
        }
        this.cancelled = true;
        returnMaterialsToAE();
        finishTask();
    }

    // ==================== 持久化 ====================

    /**
     * 将任务序列化为 NBT。
     * 保存输入材料、子任务队列以及当前批次的运行进度，使重载后能从原进度继续。
     */
    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 2);
        tag.putInt("TaskId", taskId);
        tag.put("Pattern", pattern.getDefinition().toTag(registries));
        tag.putLong("CraftCount", craftCount);
        tag.putLong("PatternOperationsPerPush", patternOperationsPerPush);
        tag.putBoolean("PatternOperationsResolved", patternOperationsResolved);
        tag.put("Inputs", writeStacks(registries, taskInputItems, taskInputFluids, taskInputKeys));

        ListTag subTasksTag = new ListTag();
        for (CraftingSubTask subTask : queuedSubTasks) {
            CompoundTag subTag = new CompoundTag();
            subTag.putLong("CraftCount", subTask.craftCount);
            subTag.put("Inputs", writeStacks(registries, subTask.items, subTask.fluids, subTask.keys));
            subTasksTag.add(subTag);
        }
        tag.put("SubTasks", subTasksTag);

        // 保存已初始化后的运行进度，使重载后从原进度继续，而非归零重算
        tag.putBoolean("Initialized", initialized);
        if (initialized) {
            tag.putInt("Progress", progress);
            tag.putInt("ProcessTime", processTime);
            tag.putInt("BaseProcessTime", baseProcessTime);
            tag.putLong("BaseEnergyPerTick", baseEnergyPerTick);
            tag.putLong("MaxParallel", maxParallel);
            tag.putInt("Batches", batches);
            tag.putLong("LastBatchSize", lastBatchSize);
            tag.putLong("AccumulatedEnergy", accumulatedEnergy);
            tag.putBoolean("AwaitingOutputFlush", awaitingOutputFlush);
            tag.put("PendingOutputs", writeStacks(registries, pendingOutputItems, pendingOutputFluids, pendingOutputKeys));
        }
        return tag;
    }

    /**
     * 从 NBT 恢复任务。样板解码失败时返回 null。
     * 若任务在存档前已初始化，则恢复运行进度并重新注册进全局进度统计。
     */
    @Nullable
    public static CraftingTask load(CompoundTag tag, Level level, CraftingTaskContext context, HolderLookup.Provider registries) {
        AEItemKey definition = AEItemKey.fromTag(registries, tag.getCompound("Pattern"));
        if (definition == null) {
            return null;
        }
        IPatternDetails pattern = AdvancedAlloyFurnacePatternResolver.decode(definition.toStack(), level);
        if (pattern == null) {
            return null;
        }

        int taskId = tag.getInt("TaskId");
        CraftingTask task = new CraftingTask(taskId, pattern, context);
        task.craftCount = Math.max(1L, tag.getLong("CraftCount"));
        task.patternOperationsPerPush = Math.max(1L, tag.getLong("PatternOperationsPerPush"));
        task.patternOperationsResolved = tag.getBoolean("PatternOperationsResolved");
        readStacks(registries, tag.getList("Inputs", Tag.TAG_COMPOUND),
                task.taskInputItems, task.taskInputFluids, task.taskInputKeys,
                context.supportsLongAeAmounts());
        task.componentInputKeys = task.resolveComponentInputKeys();

        ListTag subTasksTag = tag.getList("SubTasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < subTasksTag.size(); i++) {
            CompoundTag subTag = subTasksTag.getCompound(i);
            List<ItemStack> items = new ArrayList<>();
            List<FluidStack> fluids = new ArrayList<>();
            List<OutputKey> keys = new ArrayList<>();
            readStacks(registries, subTag.getList("Inputs", Tag.TAG_COMPOUND),
                    items, fluids, keys, context.supportsLongAeAmounts());
            task.queuedSubTasks.add(new CraftingSubTask(
                    items, fluids, keys, Math.max(1L, subTag.getLong("CraftCount"))));
        }
        task.updateDisplayedCraftCount();

        if (tag.getBoolean("Initialized")) {
            task.restoreProgress(tag, registries);
        }
        return task;
    }

    /** Returns all resources owned by a task tag when its pattern can no longer be decoded. */
    public static void returnSavedMaterials(CompoundTag tag, CraftingTaskContext context,
                                            HolderLookup.Provider registries) {
        List<ItemStack> items = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();
        List<OutputKey> keys = new ArrayList<>();
        readStacks(registries, tag.getList("Inputs", Tag.TAG_COMPOUND),
                items, fluids, keys, context.supportsLongAeAmounts());

        ListTag subTasksTag = tag.getList("SubTasks", Tag.TAG_COMPOUND);
        for (int index = 0; index < subTasksTag.size(); index++) {
            CompoundTag subTask = subTasksTag.getCompound(index);
            readStacks(registries, subTask.getList("Inputs", Tag.TAG_COMPOUND),
                    items, fluids, keys, context.supportsLongAeAmounts());
        }
        readStacks(registries, tag.getList("PendingOutputs", Tag.TAG_COMPOUND),
                items, fluids, keys, context.supportsLongAeAmounts());
        returnMaterials(context, items, fluids, keys);
    }

    /**
     * 恢复已初始化任务的运行进度，并将进度重新注册进全局进度统计与进度显示对象。
     * 全局 atomic 与 taskProgressRef 不随存档持久化，需要在加载时重建。
     */
    private void restoreProgress(CompoundTag tag, HolderLookup.Provider registries) {
        this.baseProcessTime = Math.max(1, tag.getInt("BaseProcessTime"));
        this.baseEnergyPerTick = Math.max(1L, tag.getLong("BaseEnergyPerTick"));
        this.maxParallel = Math.max(1L, tag.getLong("MaxParallel"));
        this.batches = Math.max(1, tag.getInt("Batches"));
        this.lastBatchSize = Math.max(1L, tag.getLong("LastBatchSize"));
        this.processTime = Math.max(1, tag.getInt("ProcessTime"));
        this.progress = Math.max(0, Math.min(tag.getInt("Progress"), this.processTime));
        this.accumulatedEnergy = Math.max(0L, tag.getLong("AccumulatedEnergy"));
        this.awaitingOutputFlush = tag.getBoolean("AwaitingOutputFlush");
        readStacks(registries, tag.getList("PendingOutputs", Tag.TAG_COMPOUND),
                this.pendingOutputItems, this.pendingOutputFluids, this.pendingOutputKeys,
                context.supportsLongAeAmounts());

        long totalOutputCount = calculateDisplayedOutputCount();
        this.taskProgressRef = new AdvancedAlloyFurnaceAeManager.AETaskProgress(getProductName(), processTime, displayedCraftCount, totalOutputCount);
        this.taskProgressRef.setProgress(progress);
        context.getAETaskProgressMap().put(taskId, taskProgressRef);
        context.getTotalAEProgressAtomic().addAndGet(progress);
        context.getTotalAEMaxProgressAtomic().addAndGet(processTime);
        this.progressRegistered = true;
        this.initialized = true;
    }

    private static ListTag writeStacks(HolderLookup.Provider registries, List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys) {
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            GenericStack gs = GenericStack.fromItemStack(stack);
            if (gs != null) list.add(GenericStack.writeTag(registries, gs));
        }
        for (FluidStack stack : fluids) {
            GenericStack gs = GenericStack.fromFluidStack(stack);
            if (gs != null) list.add(GenericStack.writeTag(registries, gs));
        }
        for (OutputKey key : keys) {
            list.add(GenericStack.writeTag(registries, new GenericStack(key.key, key.amount)));
        }
        return list;
    }

    private static void readStacks(
            HolderLookup.Provider registries, ListTag list, List<ItemStack> items,
            List<FluidStack> fluids, List<OutputKey> keys, boolean keepLongAmounts) {
        for (int i = 0; i < list.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, list.getCompound(i));
            if (gs == null) continue;
            if (keepLongAmounts) {
                keys.add(new OutputKey(gs.what(), gs.amount()));
            } else if (gs.what() instanceof AEItemKey itemKey) {
                items.add(itemKey.toStack((int) gs.amount()));
            } else if (gs.what() instanceof AEFluidKey fluidKey) {
                fluids.add(new FluidStack(fluidKey.getFluid(), (int) gs.amount()));
            } else {
                keys.add(new OutputKey(gs.what(), gs.amount()));
            }
        }
    }

    // 辅助类用于存储输出数据
    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0L || multiplier <= 0L) {
            return 0L;
        }
        return amount > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : amount * multiplier;
    }

    static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record ProgressEnergyStep(
            long targetEnergy, int progress, int duration, boolean resetAfterAdvance) {
    }

    private record OutputKey(AEKey key, long amount) {
    }

    private record RecipeInputs(List<ItemStack> items, List<FluidStack> fluids, List<GenericStack> keys) {
    }

    private record CraftingSubTask(List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys, long craftCount) {
    }

    private List<GenericStack> toGenericStacks(List<OutputKey> keys) {
        List<GenericStack> stacks = new ArrayList<>();
        for (OutputKey key : keys) {
            stacks.add(new GenericStack(key.key, key.amount));
        }
        return stacks;
    }

    private Set<AEKey> resolveComponentInputKeys() {
        if (context != null && context.supportsLongAeAmounts()) {
            return AdvancedAlloyFurnacePatternPolicy.componentInputKeysFromGenericStacks(
                    pattern, toGenericStacks(taskInputKeys));
        }
        return AdvancedAlloyFurnacePatternPolicy.componentInputKeys(pattern, taskInputItems);
    }

}
