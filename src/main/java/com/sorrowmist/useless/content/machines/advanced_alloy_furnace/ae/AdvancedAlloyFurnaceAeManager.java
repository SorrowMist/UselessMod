package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;

/**
 * 高级合金炉的 AE 任务调度器。
 * 负责管理样板、任务队列、批量合并、活跃任务和客户端进度同步。
 */
public final class AdvancedAlloyFurnaceAeManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int BATCH_RIPE_TICKS = 10;
    private static final int UNRETURNED_RETRY_TICKS = 20;

    private final AlloyFurnaceAeHost owner;
    private final ConcurrentHashMap<Integer, CraftingTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<PatternExecutionKey, List<CraftingTask>> activeTasksByPattern = new HashMap<>();
    private final ReentrantLock craftingLock = new ReentrantLock();
    private final ConcurrentHashMap<Integer, AETaskProgress> aeTaskProgressMap = new ConcurrentHashMap<>();
    private final List<AETaskProgress> clientTaskProgressList = new ArrayList<>();
    private final Map<PendingPatternExecutionKey, PendingAEBatch> aePendingBatches = new HashMap<>();
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private boolean patternRefreshPending;
    private final AtomicInteger activeAETaskCount = new AtomicInteger(0);
    private final AtomicInteger totalAEProgress = new AtomicInteger(0);
    private final AtomicInteger totalAEMaxProgress = new AtomicInteger(0);
    // 返还失败的输入暂存（防丢失），逐 tick 重试写回 AE 网络；仅服务端主线程访问
    private final List<GenericStack> unreturnedInputs = new ArrayList<>();
    private final List<GenericStack> unreturnedOutputs = new ArrayList<>();
    private int unreturnedInputRetryTimer = 0;
    private int unreturnedOutputRetryTimer = 0;
    private int patternPriority = 0;
    private int nextTaskId = 0;
    // 延迟加载的任务数据（loadTag 时 level 尚不可用，需推迟到首 tick 解码样板）
    @org.jetbrains.annotations.Nullable
    private CompoundTag deferredTasksTag = null;

    public AdvancedAlloyFurnaceAeManager(AlloyFurnaceAeHost owner) {
        this.owner = owner;
    }

    /**
     * 方块实体被移除时的清理。
     * 注意：不做取消返还 —— 任务与批次已随 NBT 持久化（区块卸载后会恢复），
     * 在这里返还会造成卸载复制；且 setRemoved 时 AE 节点已销毁，材料无法写回网络。
     * 主动取消只有两个入口：玩家在 GUI 点取消（AECancelPacket），以及方块被真正破坏时
     * AdvancedAlloyFurnaceBlock.onRemove 在节点销毁前调用。
     */
    public void shutdown() {
    }

    public void cancelAllTasks() {
        this.activeTasks.values().forEach(CraftingTask::cancel);
        this.activeTasks.clear();
        this.activeTasksByPattern.clear();
        this.activeAETaskCount.set(0);
        this.totalAEProgress.set(0);
        this.totalAEMaxProgress.set(0);
        this.aeTaskProgressMap.clear();

        // 待启动批次的输入尚未转为任务，同样需要返还，否则材料会随 clear() 丢失
        List<KeyCounter[]> pendingInputs = new ArrayList<>();
        synchronized (this.aePendingBatches) {
            for (PendingAEBatch batch : this.aePendingBatches.values()) {
                pendingInputs.addAll(batch.drain());
            }
            this.aePendingBatches.clear();
        }
        CraftingTask.returnInputsToAE(pendingInputs, this.owner);

        this.owner.markChanged();
        // 全部清空后主动同步一次，否则客户端会残留已取消的排队任务
        this.sendAETaskProgressToClients();
    }

    // ==================== 未返还输入暂存 ====================

    /** 暂存返还失败的输入（如 AE 不可达时的化学品/流体），逐 tick 重试写回网络 */
    public void stashUnreturnedInput(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        this.unreturnedInputs.add(new GenericStack(key, amount));
        this.owner.markChanged();
    }

    /** Stores normal recipe output that fits neither AE nor an output chemical slot yet. */
    public void stashUnreturnedOutput(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        this.unreturnedOutputs.add(new GenericStack(key, amount));
        this.owner.markChanged();
    }

    /** 定期把暂存的未返还输入重试写回 AE 网络 */
    public void tickUnreturnedInputs() {
        if (this.unreturnedInputs.isEmpty()) {
            return;
        }
        if (++this.unreturnedInputRetryTimer < UNRETURNED_RETRY_TICKS) {
            return;
        }
        this.unreturnedInputRetryTimer = 0;

        boolean changed = false;
        var it = this.unreturnedInputs.listIterator();
        while (it.hasNext()) {
            GenericStack gs = it.next();
            long requested = gs.amount();
            GenericStack remainder;
            if (gs.what() instanceof AEItemKey || gs.what() instanceof AEFluidKey) {
                remainder = FurnaceOutputPort.outputKeyWithRemainder(
                        gs,
                        this.owner.createAeOutputPort(),
                        this.owner.getItemHandler(),
                        this.owner.getInputSlotsStart(),
                        this.owner.getInputSlotsCount(),
                        this.owner.getInputFluidTanks(),
                        this.owner.getFluidTankCount(),
                        this.owner.getInputChemicalStorage(),
                        this.owner.getChemicalKeyProvider());
            } else {
                long inserted = clampInserted(this.owner.tryOutputKeyToAE(gs.what(), requested), requested);
                long remaining = requested - inserted;
                if (remaining > 0L && this.owner.getChemicalKeyProvider().isChemicalKey(gs.what())) {
                    remaining -= insertChemicalFallback(gs.what(), remaining);
                }
                remainder = remaining > 0L ? new GenericStack(gs.what(), remaining) : null;
            }
            if (remainder != null && remainder.amount() == requested) {
                continue;
            }
            changed = true;
            if (remainder == null) {
                it.remove();
            } else {
                it.set(remainder);
            }
        }
        if (changed) {
            this.owner.markChanged();
        }
    }

    /** Retries normal chemical outputs without ever placing them into input slots. */
    public void tickUnreturnedOutputs() {
        if (this.unreturnedOutputs.isEmpty()) {
            return;
        }
        if (++this.unreturnedOutputRetryTimer < UNRETURNED_RETRY_TICKS) {
            return;
        }
        this.unreturnedOutputRetryTimer = 0;

        boolean changed = false;
        var it = this.unreturnedOutputs.listIterator();
        while (it.hasNext()) {
            GenericStack gs = it.next();
            long requested = gs.amount();
            GenericStack remainder = FurnaceOutputPort.outputKeyWithRemainder(
                    gs,
                    this.owner.createAeOutputPort(),
                    this.owner.getItemHandler(),
                    this.owner.getOutputSlotsStart(),
                    this.owner.getOutputSlotsCount(),
                    this.owner.getOutputFluidTanks(),
                    this.owner.getFluidTankCount(),
                    this.owner.getOutputChemicalStorage(),
                    this.owner.getChemicalKeyProvider());
            if (remainder != null && remainder.amount() == requested) {
                continue;
            }
            changed = true;
            if (remainder == null) {
                it.remove();
            } else {
                it.set(remainder);
            }
        }
        if (changed) {
            this.owner.markChanged();
        }
    }

    private long insertChemicalFallback(AEKey key, long amount) {
        if (amount <= 0L) return 0L;
        ChemicalStackView view = this.owner.getChemicalKeyProvider()
                .fromGenericStack(new GenericStack(key, amount));
        if (view == null || view.isEmpty()) return 0L;

        long inserted = insertChemical(this.owner.getInputChemicalStorage(), view);
        if (inserted < amount) {
            inserted += insertChemical(this.owner.getOutputChemicalStorage(),
                    view.copyWithAmount(amount - inserted));
        }
        return Math.min(amount, Math.max(0L, inserted));
    }

    private static long insertChemical(FurnaceChemicalStorage storage, ChemicalStackView view) {
        if (storage == null || !storage.isAvailable() || view == null || view.isEmpty()) return 0L;
        ChemicalStackView remainder = storage.insertChemical(view, false);
        return Math.max(0L, view.amount() - remainder.amount());
    }

    private static long clampInserted(long inserted, long requested) {
        return Math.max(0L, Math.min(requested, inserted));
    }

    // ==================== 持久化 ====================

    /**
     * 将活跃任务与待启动批次序列化到 NBT。
     */
    public void saveTasks(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag tasksTag = new ListTag();
        for (CraftingTask task : this.activeTasks.values()) {
            tasksTag.add(task.save(registries));
        }
        tag.put("ActiveTasks", tasksTag);

        ListTag pendingTag = new ListTag();
        synchronized (this.aePendingBatches) {
            for (PendingAEBatch batch : this.aePendingBatches.values()) {
                CompoundTag batchTag = batch.save(registries);
                if (batchTag != null) {
                    pendingTag.add(batchTag);
                }
            }
        }
        tag.put("PendingBatches", pendingTag);

        ListTag unreturnedTag = new ListTag();
        for (GenericStack gs : this.unreturnedInputs) {
            unreturnedTag.add(GenericStack.writeTag(registries, gs));
        }
        tag.put("UnreturnedInputs", unreturnedTag);

        ListTag unreturnedOutputsTag = new ListTag();
        for (GenericStack gs : this.unreturnedOutputs) {
            unreturnedOutputsTag.add(GenericStack.writeTag(registries, gs));
        }
        tag.put("UnreturnedOutputs", unreturnedOutputsTag);
        tag.putInt("NextTaskId", this.nextTaskId);
    }

    public boolean hasPersistedData() {
        return !this.activeTasks.isEmpty()
                || !this.aePendingBatches.isEmpty()
                || !this.unreturnedInputs.isEmpty()
                || !this.unreturnedOutputs.isEmpty()
                || this.deferredTasksTag != null;
    }

    /**
     * 记录任务 NBT，推迟到 level 可用时（首 tick）再解码。
     */
    public void readTasksTag(CompoundTag tag) {
        if (tag.contains("AeTasks")) {
            this.deferredTasksTag = tag.getCompound("AeTasks");
        }
    }

    /**
     * 在 level 可用后加载延迟的任务数据（样板解码需要 level）。
     */
    public void loadDeferredTasks() {
        CompoundTag tag = this.deferredTasksTag;
        if (tag == null) {
            return;
        }
        Level level = this.owner.getLevel();
        if (level == null) {
            return;
        }
        this.deferredTasksTag = null;
        HolderLookup.Provider registries = level.registryAccess();

        this.nextTaskId = tag.getInt("NextTaskId");

        ListTag tasksTag = tag.getList("ActiveTasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); i++) {
            CompoundTag taskTag = tasksTag.getCompound(i);
            try {
                CraftingTask task = CraftingTask.load(taskTag, level, this.owner, registries);
                if (task != null) {
                    this.activeTasks.put(task.getTaskId(), task);
                    this.activeTasksByPattern.computeIfAbsent(
                            PatternExecutionKey.of(task.getPattern(), task.getComponentInputKeys()),
                            k -> new ArrayList<>()).add(task);
                    this.activeAETaskCount.incrementAndGet();
                } else {
                    CraftingTask.returnSavedMaterials(taskTag, this.owner, registries);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Failed to restore an Advanced Alloy Furnace AE task; returning its saved materials", exception);
                CraftingTask.returnSavedMaterials(taskTag, this.owner, registries);
            }
        }

        ListTag pendingTag = tag.getList("PendingBatches", Tag.TAG_COMPOUND);
        synchronized (this.aePendingBatches) {
            for (int i = 0; i < pendingTag.size(); i++) {
                CompoundTag batchTag = pendingTag.getCompound(i);
                try {
                    PendingAEBatch batch = PendingAEBatch.load(batchTag, level, registries);
                    if (batch != null && batch.pattern != null) {
                        this.aePendingBatches.put(PendingPatternExecutionKey.of(
                                batch.pattern, batch.operationsPerPush, batch.getComponentInputKeys()), batch);
                    } else {
                        returnSavedBatchMaterials(batchTag, registries);
                    }
                } catch (RuntimeException exception) {
                    LOGGER.error("Failed to restore an Advanced Alloy Furnace AE batch; returning its saved materials", exception);
                    returnSavedBatchMaterials(batchTag, registries);
                }
            }
        }

        ListTag unreturnedTag = tag.getList("UnreturnedInputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < unreturnedTag.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, unreturnedTag.getCompound(i));
            if (gs != null) {
                this.unreturnedInputs.add(gs);
            }
        }

        ListTag unreturnedOutputsTag = tag.getList("UnreturnedOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < unreturnedOutputsTag.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, unreturnedOutputsTag.getCompound(i));
            if (gs != null) {
                this.unreturnedOutputs.add(gs);
            }
        }
    }

    private void returnSavedBatchMaterials(CompoundTag tag, HolderLookup.Provider registries) {
        List<KeyCounter[]> inputs = new ArrayList<>();
        ListTag craftsTag = tag.getList("Crafts", Tag.TAG_COMPOUND);
        for (int i = 0; i < craftsTag.size(); i++) {
            inputs.add(readKeyCounters(registries, craftsTag.getCompound(i)));
        }
        CraftingTask.returnInputsToAE(inputs, this.owner);
    }

    public void updateClientTaskProgress(List<AETaskProgressPacket.TaskProgressData> tasks) {
        synchronized (this.clientTaskProgressList) {
            this.clientTaskProgressList.clear();
            for (var taskData : tasks) {
                this.clientTaskProgressList.add(new AETaskProgress(
                        taskData.productName,
                        taskData.progress,
                        taskData.maxProgress,
                        taskData.craftCount,
                        taskData.totalOutputCount,
                        taskData.statusKey,
                        taskData.statusDetail
                ));
            }
        }
    }

    public void sendAETaskProgressToClients() {
        Level level = this.owner.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) return;

        var packet = createTaskProgressPacket();
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel,
                new net.minecraft.world.level.ChunkPos(this.owner.getBlockPos()), packet);
    }

    public void sendAETaskProgressToPlayer(ServerPlayer player) {
        if (player == null || !(this.owner.getLevel() instanceof ServerLevel)) return;
        PacketDistributor.sendToPlayer(player, createTaskProgressPacket());
    }

    private AETaskProgressPacket createTaskProgressPacket() {
        List<AETaskProgressPacket.TaskProgressData> taskDataList = new ArrayList<>();
        for (var entry : this.aeTaskProgressMap.entrySet()) {
            AETaskProgress progress = entry.getValue();
            taskDataList.add(new AETaskProgressPacket.TaskProgressData(
                    progress.getProductName(),
                    progress.getProgress(),
                    progress.getMaxProgress(),
                    progress.getCraftCount(),
                    progress.getTotalOutputCount(),
                    progress.getStatusKey(),
                    progress.getStatusDetail()
            ));
        }

        synchronized (this.aePendingBatches) {
            for (PendingAEBatch batch : this.aePendingBatches.values()) {
                AETaskProgress progress = this.createPendingProgress(batch);
                taskDataList.add(new AETaskProgressPacket.TaskProgressData(
                        progress.getProductName(),
                        progress.getProgress(),
                        progress.getMaxProgress(),
                        progress.getCraftCount(),
                        progress.getTotalOutputCount(),
                        progress.getStatusKey(),
                        progress.getStatusDetail()
                ));
            }
        }

        return new AETaskProgressPacket(this.owner.getBlockPos(), taskDataList);
    }

    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(this.patterns);
    }

    public int getPatternPriority() {
        return this.patternPriority;
    }

    public void setPatternPriority(int value) {
        this.patternPriority = value;
    }

    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        SmartDoublingPatterns.Resolved execution = SmartDoublingPatterns.resolve(patternDetails);
        IPatternDetails original = execution.pattern();

        if (this.owner.getMainNode() == null || !this.owner.getMainNode().isActive()
                || !this.patterns.contains(original)
                || execution.operationsPerPush() > SmartDoublingPatterns.maximumSafeMultiplier(original)) {
            return false;
        }

        synchronized (this.aePendingBatches) {
            PendingAEBatch batch = this.findOrCreateBatch(
                    original, execution.operationsPerPush(), inputHolder);
            batch.add(inputHolder);
        }
        this.sendAETaskProgressToClients();
        return true;
    }

    public boolean isBusy() {
        return this.activeTasks.size() >= this.owner.getMaxAETaskCount();
    }

    public boolean tickAETasks() {
        boolean progressed = false;
        CraftingTask mergeTarget;
        synchronized (this.aePendingBatches) {
            var it = this.aePendingBatches.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                PendingAEBatch pending = entry.getValue();
                mergeTarget = this.findExistingTask(PatternExecutionKey.of(
                        pending.pattern, pending.getComponentInputKeys()));
                if (mergeTarget != null) {
                    List<KeyCounter[]> inputs = pending.drain();
                    if (inputs.isEmpty()) {
                        it.remove();
                        continue;
                    }
                    if (mergeTarget.addMergedBatch(inputs, pending.operationsPerPush)) {
                        it.remove();
                    } else {
                        pending.statusKey = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.queued";
                        pending.statusDetail = "";
                        pending.allInputs.addAll(inputs);
                    }
                }
            }
        }

        Iterator<Map.Entry<Integer, CraftingTask>> iterator = this.activeTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CraftingTask> entry = iterator.next();
            CraftingTask task = entry.getValue();
            task.tick();
            progressed |= task.progressedLastTick();
            if (task.isProcessingComplete()) {
                iterator.remove();
                this.removeFromPatternIndex(task);
                this.activeAETaskCount.decrementAndGet();
                this.owner.markChanged();
            }
        }

        this.rebalanceTasks();
        return progressed;
    }

    public boolean hasWork() {
        if (!this.activeTasks.isEmpty() || this.deferredTasksTag != null
                || !this.unreturnedInputs.isEmpty() || !this.unreturnedOutputs.isEmpty()) {
            return true;
        }
        synchronized (this.aePendingBatches) {
            return !this.aePendingBatches.isEmpty();
        }
    }

    /**
     * 空闲线程再分配：当活跃任务数未达上限时，把已有任务队列尾部的子任务拆出，
     * 放到新的空闲线程并行运行。
     */
    private void rebalanceTasks() {
        int maxTasks = this.owner.getMaxAETaskCount();
        while (this.activeAETaskCount.get() < maxTasks) {
            CraftingTask donor = this.findSplittableTask();
            if (donor == null) {
                break;
            }
            CraftingTask split = donor.splitLastSubTask(this.nextTaskId++);
            if (split == null) {
                break;
            }
            this.registerActiveTask(split);
        }
    }

    private CraftingTask findSplittableTask() {
        for (CraftingTask task : this.activeTasks.values()) {
            if (task.hasQueuedSubTasks()) {
                return task;
            }
        }
        return null;
    }

    private void registerActiveTask(CraftingTask task) {
        this.activeTasks.put(task.getTaskId(), task);
        this.activeTasksByPattern.computeIfAbsent(
                PatternExecutionKey.of(task.getPattern(), task.getComponentInputKeys()),
                k -> new ArrayList<>()).add(task);
        this.activeAETaskCount.incrementAndGet();
        this.owner.markChanged();
    }

    private void removeFromPatternIndex(CraftingTask task) {
        PatternExecutionKey key = PatternExecutionKey.of(
                task.getPattern(), task.getComponentInputKeys());
        List<CraftingTask> list = this.activeTasksByPattern.get(key);
        if (list != null) {
            list.remove(task);
            if (list.isEmpty()) {
                this.activeTasksByPattern.remove(key);
            }
        }
    }

    public void flushAEBatches() {
        flushAEBatches(this::sendAETaskProgressToClients);
    }

    void flushAEBatches(Runnable syncProgress) {
        List<PendingAEBatch> ripe;
        synchronized (this.aePendingBatches) {
            var it = this.aePendingBatches.entrySet().iterator();
            ripe = new ArrayList<>();
            while (it.hasNext()) {
                var entry = it.next();
                PendingAEBatch batch = entry.getValue();
                batch.ripeTimer--;
                if (batch.ripeTimer <= 0) {
                    ripe.add(batch);
                    it.remove();
                }
            }
        }

        boolean requeued = false;
        for (PendingAEBatch batch : ripe) {
            if (!this.flushBatch(batch)) {
                this.requeueBatch(batch);
                requeued = true;
            }
        }
        // A retry is removed from aePendingBatches while it is evaluated. Sync only
        // after it has been reinserted, otherwise clients briefly receive an empty list.
        if (requeued) {
            syncProgress.run();
        }
    }

    public void updatePatterns() {
        this.patternRefreshPending = true;
    }

    /** Rebuilds and publishes the provider at most once during a server tick. */
    public void tickPatternRefresh() {
        if (!this.patternRefreshPending) {
            return;
        }
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        this.patternRefreshPending = false;
        rebuildPatterns();
        this.owner.onPatternsRebuilt();
    }

    /** Rebuilds the provider snapshot without touching AE's live grid index. */
    public void rebuildPatterns() {
        Level level = this.owner.getLevel();
        // AE providers and the dynamic recipe catalog are server-authoritative. Client block
        // entity synchronization may invoke inventory callbacks repeatedly while a GUI is open.
        if (level == null || level.isClientSide) {
            return;
        }
        this.patterns.clear();
        if (!this.owner.canPublishPatterns()) {
            return;
        }

        int seen = 0;
        int decoded = 0;
        for (ItemStack stack : this.owner.getPatternStacks()) {
            if (!stack.isEmpty()) {
                seen++;
                try {
                    IPatternDetails pattern = AdvancedAlloyFurnacePatternResolver.decode(stack, level);
                    if (pattern != null && this.owner.acceptsPattern(pattern)) {
                        this.patterns.add(pattern);
                        decoded++;
                    } else {
                        LOGGER.debug("Ignoring non-publishable alloy furnace pattern at {} (item={}, decoded={})",
                                this.owner.getBlockPos(), stack.getItem(), pattern != null);
                    }
                } catch (RuntimeException exception) {
                    // A malformed pattern must not prevent the remaining
                    // slots from being published to AE2.
                    LOGGER.warn("Failed to decode alloy furnace pattern at {} (item={})",
                            this.owner.getBlockPos(), stack.getItem(), exception);
                }
            }
        }

        LOGGER.debug("Updated alloy furnace patterns at {}: seen={}, published={}",
                this.owner.getBlockPos(), seen, decoded);
    }

    public List<GenericStack> getUnreturnedInputsSnapshot() {
        return List.copyOf(this.unreturnedInputs);
    }

    public List<GenericStack> getUnreturnedOutputsSnapshot() {
        return List.copyOf(this.unreturnedOutputs);
    }

    public void addUnreturnedInputs(List<GenericStack> stacks) {
        if (stacks == null) return;
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                this.unreturnedInputs.add(stack);
            }
        }
        if (!stacks.isEmpty()) this.owner.markChanged();
    }

    public void addUnreturnedOutputs(List<GenericStack> stacks) {
        if (stacks == null) return;
        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                this.unreturnedOutputs.add(stack);
            }
        }
        if (!stacks.isEmpty()) this.owner.markChanged();
    }

    public int getActiveAETaskCount() {
        return this.activeAETaskCount.get();
    }

    public void setActiveAETaskCount(int value) {
        this.activeAETaskCount.set(value);
    }

    public int getTotalAEProgress() {
        return this.totalAEProgress.get();
    }

    public void setTotalAEProgress(int value) {
        this.totalAEProgress.set(value);
    }

    public int getTotalAEMaxProgress() {
        return this.totalAEMaxProgress.get();
    }

    public void setTotalAEMaxProgress(int value) {
        this.totalAEMaxProgress.set(value);
    }

    public Collection<AETaskProgress> getAETaskProgressList() {
        Level level = this.owner.getLevel();
        if (level != null && level.isClientSide) {
            synchronized (this.clientTaskProgressList) {
                return new ArrayList<>(this.clientTaskProgressList);
            }
        }
        List<AETaskProgress> result = new ArrayList<>(this.aeTaskProgressMap.values());
        synchronized (this.aePendingBatches) {
            for (PendingAEBatch batch : this.aePendingBatches.values()) {
                result.add(this.createPendingProgress(batch));
            }
        }
        return result;
    }

    public ConcurrentHashMap<Integer, AETaskProgress> getAETaskProgressMap() {
        return this.aeTaskProgressMap;
    }

    public AtomicInteger getTotalAEMaxProgressAtomic() {
        return this.totalAEMaxProgress;
    }

    public AtomicInteger getTotalAEProgressAtomic() {
        return this.totalAEProgress;
    }

    public ReentrantLock getCraftingLock() {
        return this.craftingLock;
    }

    private PendingAEBatch findOrCreateBatch(
            IPatternDetails patternDetails, long operationsPerPush,
            @org.jetbrains.annotations.Nullable KeyCounter[] inputHolder) {
        PendingPatternExecutionKey key = PendingPatternExecutionKey.of(
                patternDetails, operationsPerPush,
                AdvancedAlloyFurnacePatternPolicy.componentInputKeys(patternDetails, inputHolder));
        PendingAEBatch existing = this.aePendingBatches.get(key);
        if (existing != null) {
            return existing;
        }
        PendingAEBatch batch = new PendingAEBatch(patternDetails, operationsPerPush);
        this.aePendingBatches.put(key, batch);
        return batch;
    }

    private void requeueBatch(PendingAEBatch batch) {
        synchronized (this.aePendingBatches) {
            KeyCounter[] firstInput = batch.allInputs.isEmpty() ? null : batch.allInputs.getFirst();
            PendingAEBatch target = this.findOrCreateBatch(
                    batch.pattern, batch.operationsPerPush, firstInput);
            target.allInputs.addAll(batch.allInputs);
            target.statusKey = batch.statusKey;
            target.statusDetail = batch.statusDetail;
            target.ripeTimer = BATCH_RIPE_TICKS;
        }
    }

    private boolean flushBatch(PendingAEBatch batch) {
        List<KeyCounter[]> allInputs = batch.drain();
        if (allInputs.isEmpty() || batch.pattern == null) return true;

        if (this.activeAETaskCount.get() >= this.owner.getMaxAETaskCount()) {
            batch.allInputs.addAll(allInputs);
            batch.statusKey = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.queued";
            batch.statusDetail = "";
            return false;
        }

        long maximumTaskCrafts = SmartDoublingPatterns.maximumSafeMultiplier(batch.pattern);
        List<List<KeyCounter[]>> taskBatches = splitInputBatches(
                allInputs, batch.operationsPerPush, maximumTaskCrafts);
        List<KeyCounter[]> firstBatch = taskBatches.getFirst();
        KeyCounter[] merged = this.mergeKeyCounters(firstBatch);

        int taskId = this.nextTaskId++;
        IPatternDetails taskPattern = batch.pattern;
        long totalCrafts = multiplyExactPositive(firstBatch.size(), batch.operationsPerPush);

        CraftingTask task = new CraftingTask(taskId, taskPattern, merged, totalCrafts, this.owner);
        if (!task.canStartNow()) {
            batch.allInputs.addAll(allInputs);
            batch.statusKey = task.getWaitingStatusKey();
            batch.statusDetail = task.getWaitingDetail();
            return false;
        }

        List<KeyCounter[]> queuedInputs = new ArrayList<>();
        for (int index = 1; index < taskBatches.size(); index++) {
            queuedInputs.addAll(taskBatches.get(index));
        }
        if (!queuedInputs.isEmpty()
                && !task.addMergedBatch(queuedInputs, batch.operationsPerPush)) {
            batch.allInputs.addAll(allInputs);
            batch.statusKey = task.getWaitingStatusKey();
            batch.statusDetail = task.getWaitingDetail();
            return false;
        }

        this.registerActiveTask(task);
        return true;
    }

    /**
     * Splits pushed AE inputs before either total operations or any individual key amount would
     * exceed the long range. Individual pushes are left intact when they already use a full long.
     */
    static List<List<KeyCounter[]>> splitInputBatches(
            List<KeyCounter[]> allInputs, long operationsPerPush, long maximumTaskCrafts) {
        if (allInputs == null || allInputs.isEmpty()) {
            return List.of();
        }

        long safeOperationsPerPush = Math.max(1L, operationsPerPush);
        long safeMaximumTaskCrafts = Math.max(1L, maximumTaskCrafts);
        List<List<KeyCounter[]>> result = new ArrayList<>();
        List<KeyCounter[]> current = new ArrayList<>();
        Map<AEKey, Long> currentAmounts = new HashMap<>();
        long currentCrafts = 0L;
        boolean currentRequiresIsolation = false;

        for (KeyCounter[] input : allInputs) {
            Map<AEKey, Long> inputAmounts = collectExactKeyAmounts(input);
            boolean fitsCurrentBatch = !current.isEmpty()
                    && !currentRequiresIsolation
                    && safeOperationsPerPush <= safeMaximumTaskCrafts - currentCrafts
                    && inputAmounts != null
                    && canAddExact(currentAmounts, inputAmounts);
            if (!current.isEmpty() && !fitsCurrentBatch) {
                result.add(current);
                current = new ArrayList<>();
                currentAmounts = new HashMap<>();
                currentCrafts = 0L;
                currentRequiresIsolation = false;
            }

            current.add(input);
            if (inputAmounts == null) {
                // A single push can contain repeated full-range keys in distinct holders. Keep
                // it as-is, but never combine it with another push and lose the extra amount.
                currentRequiresIsolation = true;
            } else {
                addExact(currentAmounts, inputAmounts);
            }
            currentCrafts += safeOperationsPerPush;
        }

        if (!current.isEmpty()) {
            result.add(current);
        }
        return result;
    }

    private KeyCounter[] mergeKeyCounters(List<KeyCounter[]> allInputs) {
        if (allInputs.isEmpty()) return new KeyCounter[0];
        if (allInputs.size() == 1) return allInputs.getFirst();

        Map<AEKey, Long> merged = new HashMap<>();
        for (KeyCounter[] counters : allInputs) {
            if (counters == null) continue;
            for (KeyCounter counter : counters) {
                if (counter == null) continue;
                for (var entry : counter) {
                    long amount = entry.getLongValue();
                    if (amount <= 0L) continue;
                    long current = merged.getOrDefault(entry.getKey(), 0L);
                    if (current > Long.MAX_VALUE - amount) {
                        throw new IllegalStateException("Attempted to merge AE inputs beyond long range");
                    }
                    merged.put(entry.getKey(), current + amount);
                }
            }
        }

        KeyCounter result = new KeyCounter();
        for (var entry : merged.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        return new KeyCounter[]{result};
    }

    @org.jetbrains.annotations.Nullable
    private static Map<AEKey, Long> collectExactKeyAmounts(KeyCounter[] counters) {
        Map<AEKey, Long> result = new HashMap<>();
        if (counters == null) return result;
        for (KeyCounter counter : counters) {
            if (counter == null) continue;
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount <= 0L) continue;
                long current = result.getOrDefault(entry.getKey(), 0L);
                if (current > Long.MAX_VALUE - amount) {
                    return null;
                }
                result.put(entry.getKey(), current + amount);
            }
        }
        return result;
    }

    private static boolean canAddExact(Map<AEKey, Long> target, Map<AEKey, Long> additions) {
        for (var entry : additions.entrySet()) {
            if (target.getOrDefault(entry.getKey(), 0L) > Long.MAX_VALUE - entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void addExact(Map<AEKey, Long> target, Map<AEKey, Long> additions) {
        for (var entry : additions.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Long::sum);
        }
    }

    private CraftingTask findExistingTask(PatternExecutionKey patternKey) {
        List<CraftingTask> list = this.activeTasksByPattern.get(patternKey);
        if (list == null) {
            return null;
        }
        for (CraftingTask task : list) {
            if (!task.isProcessingComplete()) {
                return task;
            }
        }
        return null;
    }

    private AETaskProgress createPendingProgress(PendingAEBatch batch) {
        long craftCount = calculateTotalCrafts(batch.allInputs.size(), batch.operationsPerPush);
        long outputAmount = 1L;
        if (batch.pattern != null && !batch.pattern.getOutputs().isEmpty()) {
            outputAmount = batch.pattern.getOutputs().getFirst().amount();
        }
        long totalOutputCount = saturatingMultiply(outputAmount, craftCount);
        return new AETaskProgress(this.getPatternProductName(batch.pattern), 0, 1, craftCount, totalOutputCount,
                batch.statusKey, batch.statusDetail);
    }

    private String getPatternProductName(IPatternDetails pattern) {
        if (pattern == null || pattern.getOutputs().isEmpty()) {
            return "Unknown";
        }
        return pattern.getOutputs().getFirst().what().getDisplayName().getString();
    }

    // AE任务进度信息类
    public static class AETaskProgress {
        private final String productName;
        private final long outputCount; // 单次产出数量
        private volatile int progress;
        private volatile int maxProgress;
        private volatile long craftCount;
        private volatile long totalOutputCount; // 最终产物总数 = 合成次数 × 单次产出数量
        private volatile String statusKey;
        private volatile String statusDetail;

        public AETaskProgress(String productName, int maxProgress, long craftCount, long totalOutputCount) {
            this(productName, 0, maxProgress, craftCount, totalOutputCount,
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        }

        public AETaskProgress(String productName, int progress, int maxProgress, long craftCount, long totalOutputCount) {
            this(productName, progress, maxProgress, craftCount, totalOutputCount,
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        }

        public AETaskProgress(String productName, int progress, int maxProgress, long craftCount, long totalOutputCount, String statusKey, String statusDetail) {
            this.productName = productName;
            this.progress = progress;
            this.maxProgress = maxProgress;
            this.craftCount = craftCount;
            this.totalOutputCount = totalOutputCount;
            this.outputCount = craftCount > 0 ? totalOutputCount / craftCount : 1; // 计算单次产出数量
            this.statusKey = statusKey;
            this.statusDetail = statusDetail;
        }

        public String getProductName() {return productName;}

        public int getProgress() {return progress;}

        public void setProgress(int progress) {this.progress = progress;}

        public int getMaxProgress() {return maxProgress;}

        public void setMaxProgress(int maxProgress) {this.maxProgress = maxProgress;}

        public long getCraftCount() {return craftCount;}

        public long getTotalOutputCount() {return totalOutputCount;}

        public String getStatusKey() {return statusKey;}

        public String getStatusDetail() {return statusDetail;}

        public void setStatus(String statusKey, String statusDetail) {
            this.statusKey = statusKey;
            this.statusDetail = statusDetail;
        }

        // 更新合成次数和最终产物总数（用于任务合并）。
        public void updateCraftCount(long newCraftCount) {
            this.craftCount = newCraftCount;
            this.totalOutputCount = saturatingMultiply(newCraftCount, outputCount);
        }
    }

    static final class PendingAEBatch {
        final IPatternDetails pattern;
        final long operationsPerPush;
        final List<KeyCounter[]> allInputs = new ArrayList<>();
        int ripeTimer = BATCH_RIPE_TICKS;
        String statusKey = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.queued";
        String statusDetail = "";

        PendingAEBatch(IPatternDetails pattern, long operationsPerPush) {
            this.pattern = pattern;
            this.operationsPerPush = Math.max(1L, operationsPerPush);
        }

        void add(KeyCounter[] input) {
            // 不重置 ripeTimer：成熟计时从批次创建（或重排）起算。
            // 若每次推送都重置，AE CPU 对超大请求持续推送时批次永远不会成熟，
            // 任务迟迟不启动，表现为“一直不合成”。
            this.allInputs.add(input);
        }

        List<KeyCounter[]> drain() {
            List<KeyCounter[]> result = new ArrayList<>(this.allInputs);
            this.allInputs.clear();
            return result;
        }

        CompoundTag save(HolderLookup.Provider registries) {
            if (this.pattern == null) {
                return null;
            }
            CompoundTag tag = new CompoundTag();
            tag.put("Pattern", this.pattern.getDefinition().toTag(registries));
            tag.putLong("OperationsPerPush", this.operationsPerPush);
            ListTag craftsTag = new ListTag();
            for (KeyCounter[] counters : this.allInputs) {
                craftsTag.add(writeKeyCounters(registries, counters));
            }
            tag.put("Crafts", craftsTag);
            return tag;
        }

        @org.jetbrains.annotations.Nullable
        static PendingAEBatch load(CompoundTag tag, Level level, HolderLookup.Provider registries) {
            appeng.api.stacks.AEItemKey definition = appeng.api.stacks.AEItemKey.fromTag(registries, tag.getCompound("Pattern"));
            if (definition == null) {
                return null;
            }
            IPatternDetails pattern = AdvancedAlloyFurnacePatternResolver.decode(definition.toStack(), level);
            if (pattern == null) {
                return null;
            }
            long operationsPerPush = tag.contains("OperationsPerPush", Tag.TAG_ANY_NUMERIC)
                    ? Math.max(1L, tag.getLong("OperationsPerPush"))
                    : 1L;
            PendingAEBatch batch = new PendingAEBatch(pattern, operationsPerPush);
            ListTag craftsTag = tag.getList("Crafts", Tag.TAG_COMPOUND);
            for (int i = 0; i < craftsTag.size(); i++) {
                batch.allInputs.add(readKeyCounters(registries, craftsTag.getCompound(i)));
            }
            return batch;
        }

        Set<AEKey> getComponentInputKeys() {
            if (allInputs.isEmpty()) {
                return Set.of();
            }
            return AdvancedAlloyFurnacePatternPolicy.componentInputKeys(pattern, allInputs.getFirst());
        }
    }

    private static CompoundTag writeKeyCounters(HolderLookup.Provider registries, KeyCounter[] counters) {
        ListTag list = new ListTag();
        if (counters != null) {
            for (KeyCounter counter : counters) {
                if (counter == null) continue;
                for (var entry : counter) {
                    list.add(GenericStack.writeTag(registries, new GenericStack(entry.getKey(), entry.getLongValue())));
                }
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Stacks", list);
        return tag;
    }

    private static KeyCounter[] readKeyCounters(HolderLookup.Provider registries, CompoundTag tag) {
        ListTag list = tag.getList("Stacks", Tag.TAG_COMPOUND);
        KeyCounter counter = new KeyCounter();
        for (int i = 0; i < list.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, list.getCompound(i));
            if (gs != null) {
                counter.add(gs.what(), gs.amount());
            }
        }
        return new KeyCounter[]{counter};
    }

    private record PatternKey(@org.jetbrains.annotations.Nullable AEItemKey definition) {
        static PatternKey of(IPatternDetails pattern) {
            if (pattern == null) return new PatternKey(null);
            return new PatternKey(SmartDoublingPatterns.unwrap(pattern).getDefinition());
        }
    }

    static long calculateTotalCrafts(long batchSize, long multiplier) {
        return saturatingMultiply(Math.max(0L, batchSize), Math.max(1L, multiplier));
    }

    private static long multiplyExactPositive(long amount, long multiplier) {
        long safeAmount = Math.max(1L, amount);
        long safeMultiplier = Math.max(1L, multiplier);
        if (safeAmount > Long.MAX_VALUE / safeMultiplier) {
            throw new IllegalStateException("Batch partition failed to keep operations within long range");
        }
        return safeAmount * safeMultiplier;
    }

    private static long saturatingMultiply(long amount, long multiplier) {
        if (amount <= 0L || multiplier <= 0L) {
            return 0L;
        }
        return amount > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : amount * multiplier;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record PatternExecutionKey(PatternKey pattern, Set<AEKey> componentInputKeys) {
        private PatternExecutionKey {
            componentInputKeys = componentInputKeys == null ? Set.of() : Set.copyOf(componentInputKeys);
        }

        static PatternExecutionKey of(IPatternDetails pattern, Set<AEKey> componentInputKeys) {
            return new PatternExecutionKey(PatternKey.of(pattern), componentInputKeys);
        }
    }

    /** Pending batches keep their per-push multiplier separate from active-task identity. */
    private record PendingPatternExecutionKey(
            PatternKey pattern, long operationsPerPush, Set<AEKey> componentInputKeys) {
        private PendingPatternExecutionKey {
            operationsPerPush = Math.max(1L, operationsPerPush);
            componentInputKeys = componentInputKeys == null ? Set.of() : Set.copyOf(componentInputKeys);
        }

        static PendingPatternExecutionKey of(
                IPatternDetails pattern, long operationsPerPush, Set<AEKey> componentInputKeys) {
            return new PendingPatternExecutionKey(
                    PatternKey.of(pattern), operationsPerPush, componentInputKeys);
        }
    }
}
