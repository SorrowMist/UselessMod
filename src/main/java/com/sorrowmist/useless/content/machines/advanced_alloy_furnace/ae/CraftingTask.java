package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
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
    // 待输出缓冲：产物先进缓冲，由 flushPendingOutputs 逐 tick 写出，写不下时保留重试，避免静默丢失
    private final List<ItemStack> pendingOutputItems = new ArrayList<>();
    private final List<FluidStack> pendingOutputFluids = new ArrayList<>();
    private final List<OutputKey> pendingOutputKeys = new ArrayList<>();
    private boolean awaitingOutputFlush = false;
    // 当前子任务已扣除的能量，用于完成结算（与本体 settleCompletionEnergy 语义对齐）
    private long accumulatedEnergy = 0;
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
        storeInputMaterials(inputHolder, this.taskInputItems, this.taskInputFluids, this.taskInputKeys);
    }

    /** 从一个拆分出的子任务构造独立任务（用于空闲线程再分配） */
    private CraftingTask(int taskId, IPatternDetails pattern, CraftingTaskContext context, CraftingSubTask subTask) {
        this.taskId = taskId;
        this.pattern = pattern;
        this.context = context;
        this.craftCount = Math.max(1, subTask.craftCount);
        this.displayedCraftCount = this.craftCount;
        this.taskInputItems.addAll(subTask.items);
        this.taskInputFluids.addAll(subTask.fluids);
        this.taskInputKeys.addAll(subTask.keys);
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
        CraftingSubTask merged = createMergedSubTask(allInputs);
        if (queuedSubTasks.isEmpty()) {
            queuedSubTasks.add(merged);
        } else {
            // 并入队尾尚未启动的子任务，减少批次数（每个子任务独立收一份结算能量）
            CraftingSubTask last = queuedSubTasks.getLast();
            last.items.addAll(merged.items);
            last.fluids.addAll(merged.fluids);
            last.keys.addAll(merged.keys);
            int combined = (int) Math.min((long) last.craftCount + merged.craftCount, Integer.MAX_VALUE);
            queuedSubTasks.set(queuedSubTasks.size() - 1, new CraftingSubTask(last.items, last.fluids, last.keys, combined));
        }
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
        return new CraftingTask(newTaskId, pattern, context, subTask);
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

    private static void storeInputMaterials(KeyCounter[] counters, List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys) {
        if (counters == null) return;

        for (KeyCounter counter : counters) {
            if (counter == null) continue;

            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();

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

        ItemStack moldStack = context.getItemHandler().getStackInSlot(context.getMoldSlot());

        cachedRecipe = AlloyFurnaceRecipeManager.getInstance().findRecipeForCrafting(
                context.getLevel(), tempInputs, tempFluids, toGenericStacks(taskInputKeys), moldStack,
                pattern == null ? List.of() : pattern.getOutputs(), craftCount
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

        return AlloyFurnaceRecipeManager.matchesExpectedOutputs(recipe, pattern.getOutputs());
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
            storeInputMaterials(counters, items, fluids, keys);
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
                var pos = context.getBlockPos();
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, leftover);
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
                AEFluidKey fluidKey = AEFluidKey.of(leftover);
                if (fluidKey != null) {
                    context.stashUnreturnedInput(fluidKey, leftover.getAmount());
                }
            }
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

        if (!awaitingOutputFlush) {
            AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
            ResolvedCatalystEffect resolvedCatalystEffect = getCatalystEffect(recipe);
            long energyTarget = getSubTaskEnergyTarget(recipe, resolvedCatalystEffect);

            if (progress < processTime) {
                int batchIndex = baseProcessTime > 0 ? progress / baseProcessTime : 0;
                int actualBatchParallel = batchIndex < batches - 1 ? maxParallel : lastBatchSize;
                int tickEnergy = AlloyFurnaceRecipeExecutor.calculateTickEnergy(baseEnergyPerTick, actualBatchParallel, resolvedCatalystEffect);
                // 每 tick 扣能以结算目标为上限，避免总额超出本体语义（有用锭=一份配方能量）
                int required = (int) Math.min(tickEnergy, Math.max(0L, energyTarget - accumulatedEnergy));
                if (required > 0) {
                    if (!context.getEnergyManager().tryConsumeEnergy(required)) {
                        return false;
                    }
                    accumulatedEnergy += required;
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

                if (progress < processTime) {
                    return false;
                }
            }

            // 完成结算：与本体 settleCompletionEnergy 语义对齐 ——
            // 有用锭补足到一份配方能量，其余催化剂补足到 energy × craftCount
            if (!settleSubTaskEnergy(energyTarget)) {
                return false;
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

    /**
     * 当前子任务的能量结算目标，与本体 settleCompletionEnergy 的目标一致：
     * 有用锭（不随并行倍增）时整个子任务只收一份配方能量；
     * 其余催化剂按 energy × craftCount 收取完整份额。
     */
    private long getSubTaskEnergyTarget(@Nullable AdvancedAlloyFurnaceRecipe recipe, ResolvedCatalystEffect resolvedCatalystEffect) {
        long recipeEnergy = recipe != null
                ? Math.max(0, recipe.energy())
                : (long) baseEnergyPerTick * Math.max(1, baseProcessTime);
        return resolvedCatalystEffect.energyMultipliesWithParallel()
                ? recipeEnergy * Math.max(1, craftCount)
                : recipeEnergy;
    }

    /**
     * 子任务完成时补扣能量到结算目标。能量不足时尽量扣取现有存量并保持等待，
     * 直到补足为止（材料已消耗，无法像本体那样回退并行数）。
     *
     * @return 是否已补足到目标
     */
    private boolean settleSubTaskEnergy(long energyTarget) {
        long deficit = energyTarget - accumulatedEnergy;
        if (deficit <= 0) {
            return true;
        }
        int consumable = (int) Math.min(deficit, Integer.MAX_VALUE);
        if (context.getEnergyManager().tryConsumeEnergy(consumable)) {
            accumulatedEnergy += consumable;
            return accumulatedEnergy >= energyTarget;
        }
        int available = context.getEnergyManager().getEnergyStored();
        if (available > 0 && context.getEnergyManager().tryConsumeEnergy(available)) {
            accumulatedEnergy += available;
            context.markChanged();
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

        refreshCatalystLayout();
        recalculateBatchLayout();

        int outputCount = 1;
        if (pattern != null && !pattern.getOutputs().isEmpty()) {
            var output = pattern.getOutputs().getFirst();
            outputCount = (int) output.amount();
        }

        int totalOutputCount = (int) Math.min((long) displayedCraftCount * outputCount, Integer.MAX_VALUE);
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
        int totalOutputCount = (int) Math.min((long) displayedCraftCount * outputCount, Integer.MAX_VALUE);
        taskProgressRef = new AdvancedAlloyFurnaceAeManager.AETaskProgress(getProductName(), 0, 1, displayedCraftCount, totalOutputCount,
                "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_mold", this.waitingDetail);
        context.getAETaskProgressMap().put(taskId, taskProgressRef);
        context.markChanged();
        context.sendAETaskProgressToClients();
    }

    private void recalculateBatchLayout() {
        // 全程 long 运算并按 int 上限饱和：512M 级 craftCount 下 int 乘法会溢出为负，
        // 导致 processTime 被 Math.max 钳到 1、整批瞬间完成
        long parallel = Math.max(1L, maxParallel);
        long batchesLong = ((long) craftCount + parallel - 1) / parallel;
        batches = (int) Math.min(batchesLong, Integer.MAX_VALUE);
        long processTimeLong = (long) Math.max(1, baseProcessTime) * batches;
        processTime = (int) Math.min(processTimeLong, Integer.MAX_VALUE);
        lastBatchSize = (int) Math.max(1L, craftCount - parallel * (batches - 1L));
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
        ItemStack catalystStack = context.getItemHandler().getStackInSlot(context.getCatalystSlot());
        int baseTime = recipe != null ? recipe.processTime() : 200;
        return CatalystEffectResolver.resolve(recipe, catalystStack, baseTime);
    }

    /** 按当前催化剂重算时长、每 tick 能耗与并行上限（初始化及每个子任务启动时调用） */
    private void refreshCatalystLayout() {
        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();
        baseProcessTime = getRecipeProcessTime(recipe);
        if (recipe != null && recipe.processTime() > 0) {
            baseEnergyPerTick = Math.max(1, recipe.energy() / Math.max(1, recipe.processTime()));
        } else {
            baseEnergyPerTick = 200;
        }
        maxParallel = AlloyFurnaceParallelCalculator.calculateAeTaskParallel(context.getEnergyManager(), baseEnergyPerTick,
                                                                             getCatalystEffect(recipe)
        );
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
    private void generatePendingOutputs(int craftCount) {
        if (context.getLevel() == null || context.getLevel().isClientSide) return;

        AdvancedAlloyFurnaceRecipe recipe = findTaskRecipe();

        for (var output : pattern.getOutputs()) {
            if (output.what() instanceof AEItemKey itemKey) {
                long remaining = output.amount() * (long) craftCount;
                while (remaining > 0) {
                    int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                    pendingOutputItems.add(itemKey.toStack(chunk));
                    remaining -= chunk;
                }
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                long remaining = output.amount() * (long) craftCount;
                while (remaining > 0) {
                    int chunk = (int) Math.min(remaining, Integer.MAX_VALUE);
                    pendingOutputFluids.add(new FluidStack(fluidKey.getFluid(), chunk));
                    remaining -= chunk;
                }
            } else {
                pendingOutputKeys.add(new OutputKey(output.what(), output.amount() * craftCount));
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
                    pendingOutputKeys.add(new OutputKey(keyOutput.what(), keyOutput.amount() * craftCount));
                }
            }
        }
        context.markChanged();
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
        tag.putInt("TaskId", taskId);
        tag.put("Pattern", pattern.getDefinition().toTag(registries));
        tag.putInt("CraftCount", craftCount);
        tag.put("Inputs", writeStacks(registries, taskInputItems, taskInputFluids, taskInputKeys));

        ListTag subTasksTag = new ListTag();
        for (CraftingSubTask subTask : queuedSubTasks) {
            CompoundTag subTag = new CompoundTag();
            subTag.putInt("CraftCount", subTask.craftCount);
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
            tag.putInt("BaseEnergyPerTick", baseEnergyPerTick);
            tag.putInt("MaxParallel", maxParallel);
            tag.putInt("Batches", batches);
            tag.putInt("LastBatchSize", lastBatchSize);
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
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(definition.toStack(), level);
        if (pattern == null) {
            return null;
        }

        int taskId = tag.getInt("TaskId");
        CraftingTask task = new CraftingTask(taskId, pattern, context);
        task.craftCount = Math.max(1, tag.getInt("CraftCount"));
        readStacks(registries, tag.getList("Inputs", Tag.TAG_COMPOUND), task.taskInputItems, task.taskInputFluids, task.taskInputKeys);

        ListTag subTasksTag = tag.getList("SubTasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < subTasksTag.size(); i++) {
            CompoundTag subTag = subTasksTag.getCompound(i);
            List<ItemStack> items = new ArrayList<>();
            List<FluidStack> fluids = new ArrayList<>();
            List<OutputKey> keys = new ArrayList<>();
            readStacks(registries, subTag.getList("Inputs", Tag.TAG_COMPOUND), items, fluids, keys);
            task.queuedSubTasks.add(new CraftingSubTask(items, fluids, keys, Math.max(1, subTag.getInt("CraftCount"))));
        }
        task.updateDisplayedCraftCount();

        if (tag.getBoolean("Initialized")) {
            task.restoreProgress(tag, registries);
        }
        return task;
    }

    /**
     * 恢复已初始化任务的运行进度，并将进度重新注册进全局进度统计与进度显示对象。
     * 全局 atomic 与 taskProgressRef 不随存档持久化，需要在加载时重建。
     */
    private void restoreProgress(CompoundTag tag, HolderLookup.Provider registries) {
        this.baseProcessTime = Math.max(1, tag.getInt("BaseProcessTime"));
        this.baseEnergyPerTick = Math.max(1, tag.getInt("BaseEnergyPerTick"));
        this.maxParallel = Math.max(1, tag.getInt("MaxParallel"));
        this.batches = Math.max(1, tag.getInt("Batches"));
        this.lastBatchSize = Math.max(1, tag.getInt("LastBatchSize"));
        this.processTime = Math.max(1, tag.getInt("ProcessTime"));
        this.progress = Math.max(0, Math.min(tag.getInt("Progress"), this.processTime));
        this.accumulatedEnergy = Math.max(0L, tag.getLong("AccumulatedEnergy"));
        this.awaitingOutputFlush = tag.getBoolean("AwaitingOutputFlush");
        readStacks(registries, tag.getList("PendingOutputs", Tag.TAG_COMPOUND),
                this.pendingOutputItems, this.pendingOutputFluids, this.pendingOutputKeys);

        int outputCount = 1;
        if (pattern != null && !pattern.getOutputs().isEmpty()) {
            outputCount = (int) pattern.getOutputs().getFirst().amount();
        }
        int totalOutputCount = (int) Math.min((long) displayedCraftCount * outputCount, Integer.MAX_VALUE);
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

    private static void readStacks(HolderLookup.Provider registries, ListTag list, List<ItemStack> items, List<FluidStack> fluids, List<OutputKey> keys) {
        for (int i = 0; i < list.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, list.getCompound(i));
            if (gs == null) continue;
            if (gs.what() instanceof AEItemKey itemKey) {
                items.add(itemKey.toStack((int) gs.amount()));
            } else if (gs.what() instanceof AEFluidKey fluidKey) {
                fluids.add(new FluidStack(fluidKey.getFluid(), (int) gs.amount()));
            } else {
                keys.add(new OutputKey(gs.what(), gs.amount()));
            }
        }
    }

    // 辅助类用于存储输出数据
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
