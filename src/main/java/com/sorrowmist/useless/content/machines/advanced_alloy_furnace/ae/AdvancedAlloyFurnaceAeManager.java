package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.layout.AdvancedAlloyFurnaceLayout;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 高级合金炉的 AE 任务调度器。
 * 负责管理样板、任务队列、批量合并、活跃任务和客户端进度同步。
 */
public final class AdvancedAlloyFurnaceAeManager {
    private static final int MAX_CONCURRENT_TASKS = 4;
    private static final int BATCH_RIPE_TICKS = 10;

    private final AdvancedAlloyFurnaceBlockEntity owner;
    private final ConcurrentHashMap<Integer, CraftingTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<PatternKey, List<CraftingTask>> activeTasksByPattern = new HashMap<>();
    private final ReentrantLock craftingLock = new ReentrantLock();
    private final ConcurrentHashMap<Integer, AETaskProgress> aeTaskProgressMap = new ConcurrentHashMap<>();
    private final List<AETaskProgress> clientTaskProgressList = new ArrayList<>();
    private final Map<PatternKey, PendingAEBatch> aePendingBatches = new HashMap<>();
    private final List<IPatternDetails> patterns = new ArrayList<>();
    private final AtomicInteger activeAETaskCount = new AtomicInteger(0);
    private final AtomicInteger totalAEProgress = new AtomicInteger(0);
    private final AtomicInteger totalAEMaxProgress = new AtomicInteger(0);
    private int patternPriority = 0;
    private int nextTaskId = 0;
    // 延迟加载的任务数据（loadTag 时 level 尚不可用，需推迟到首 tick 解码样板）
    @org.jetbrains.annotations.Nullable
    private CompoundTag deferredTasksTag = null;

    public AdvancedAlloyFurnaceAeManager(AdvancedAlloyFurnaceBlockEntity owner) {
        this.owner = owner;
    }

    public void shutdown() {
        this.cancelAllTasks();
    }

    public void cancelAllTasks() {
        this.activeTasks.values().forEach(CraftingTask::cancel);
        this.activeTasks.clear();
        this.activeTasksByPattern.clear();
        this.activeAETaskCount.set(0);
        this.totalAEProgress.set(0);
        this.totalAEMaxProgress.set(0);
        this.aeTaskProgressMap.clear();
        synchronized (this.aePendingBatches) {
            this.aePendingBatches.clear();
        }
        this.owner.markChanged();
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
        tag.putInt("NextTaskId", this.nextTaskId);
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
        this.deferredTasksTag = null;
        if (tag == null) {
            return;
        }
        Level level = this.owner.getLevel();
        if (level == null) {
            return;
        }
        HolderLookup.Provider registries = level.registryAccess();

        this.nextTaskId = tag.getInt("NextTaskId");

        ListTag tasksTag = tag.getList("ActiveTasks", Tag.TAG_COMPOUND);
        for (int i = 0; i < tasksTag.size(); i++) {
            CraftingTask task = CraftingTask.load(tasksTag.getCompound(i), level, this.owner, registries);
            if (task != null) {
                this.activeTasks.put(task.getTaskId(), task);
                this.activeTasksByPattern.computeIfAbsent(PatternKey.of(task.getPattern()), k -> new ArrayList<>()).add(task);
                this.activeAETaskCount.incrementAndGet();
            }
        }

        ListTag pendingTag = tag.getList("PendingBatches", Tag.TAG_COMPOUND);
        synchronized (this.aePendingBatches) {
            for (int i = 0; i < pendingTag.size(); i++) {
                PendingAEBatch batch = PendingAEBatch.load(pendingTag.getCompound(i), level, registries);
                if (batch != null && batch.pattern != null) {
                    this.aePendingBatches.put(PatternKey.of(batch.pattern), batch);
                }
            }
        }
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

        var packet = new AETaskProgressPacket(this.owner.getBlockPos(), taskDataList);
        PacketDistributor.sendToPlayersTrackingChunk(serverLevel, new net.minecraft.world.level.ChunkPos(this.owner.getBlockPos()), packet);
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
        if (!this.owner.getMainNode().isActive() || !this.patterns.contains(patternDetails)) {
            return false;
        }

        synchronized (this.aePendingBatches) {
            PendingAEBatch batch = this.findOrCreateBatch(patternDetails);
            batch.add(inputHolder);
        }
        this.sendAETaskProgressToClients();
        return true;
    }

    public boolean isBusy() {
        return this.activeTasks.size() >= MAX_CONCURRENT_TASKS;
    }

    public void tickAETasks() {
        CraftingTask mergeTarget;
        synchronized (this.aePendingBatches) {
            var it = this.aePendingBatches.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                mergeTarget = this.findExistingTask(entry.getKey());
                if (mergeTarget != null) {
                    List<KeyCounter[]> inputs = entry.getValue().drain();
                    if (inputs.isEmpty()) {
                        it.remove();
                        continue;
                    }
                    if (mergeTarget.addMergedBatch(inputs)) {
                        it.remove();
                    } else {
                        entry.getValue().statusKey = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.queued";
                        entry.getValue().statusDetail = "";
                        entry.getValue().allInputs.addAll(inputs);
                    }
                }
            }
        }

        Iterator<Map.Entry<Integer, CraftingTask>> iterator = this.activeTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, CraftingTask> entry = iterator.next();
            CraftingTask task = entry.getValue();
            task.tick();
            if (task.isProcessingComplete()) {
                iterator.remove();
                this.removeFromPatternIndex(task);
                this.activeAETaskCount.decrementAndGet();
                this.owner.markChanged();
            }
        }

        this.rebalanceTasks();
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
        this.activeTasksByPattern.computeIfAbsent(PatternKey.of(task.getPattern()), k -> new ArrayList<>()).add(task);
        this.activeAETaskCount.incrementAndGet();
        this.owner.markChanged();
    }

    private void removeFromPatternIndex(CraftingTask task) {
        PatternKey key = PatternKey.of(task.getPattern());
        List<CraftingTask> list = this.activeTasksByPattern.get(key);
        if (list != null) {
            list.remove(task);
            if (list.isEmpty()) {
                this.activeTasksByPattern.remove(key);
            }
        }
    }

    public void flushAEBatches() {
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

        for (PendingAEBatch batch : ripe) {
            if (!this.flushBatch(batch)) {
                this.requeueBatch(batch);
            }
        }
    }

    public void updatePatterns() {
        this.patterns.clear();
        Level level = this.owner.getLevel();
        if (level == null) {
            return;
        }

        for (int i = AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_START; i <= AdvancedAlloyFurnaceLayout.PATTERN_SLOTS_END; i++) {
            ItemStack stack = this.owner.getItemHandler().getStackInSlot(i);
            if (!stack.isEmpty()) {
                IPatternDetails pattern = PatternDetailsHelper.decodePattern(stack, level);
                if (pattern != null) {
                    this.patterns.add(pattern);
                }
            }
        }

        if (this.owner.getMainNode().getNode() != null) {
            ICraftingProvider.requestUpdate(this.owner.getMainNode());
        }
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

    private PendingAEBatch findOrCreateBatch(IPatternDetails patternDetails) {
        PatternKey key = PatternKey.of(patternDetails);
        PendingAEBatch existing = this.aePendingBatches.get(key);
        if (existing != null) {
            return existing;
        }
        PendingAEBatch batch = new PendingAEBatch(patternDetails);
        this.aePendingBatches.put(key, batch);
        return batch;
    }

    private void requeueBatch(PendingAEBatch batch) {
        synchronized (this.aePendingBatches) {
            PendingAEBatch target = this.findOrCreateBatch(batch.pattern);
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

        KeyCounter[] merged = this.mergeKeyCounters(allInputs);

        int taskId = this.nextTaskId++;
        int totalCrafts = allInputs.size();

        CraftingTask task = new CraftingTask(taskId, batch.pattern, merged, totalCrafts, this.owner);
        if (!task.canStartNow()) {
            batch.allInputs.addAll(allInputs);
            batch.statusKey = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_mold";
            batch.statusDetail = task.getWaitingDetail();
            this.sendAETaskProgressToClients();
            return false;
        }

        this.registerActiveTask(task);
        return true;
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
                    merged.merge(entry.getKey(), entry.getLongValue(), Long::sum);
                }
            }
        }

        KeyCounter result = new KeyCounter();
        for (var entry : merged.entrySet()) {
            result.add(entry.getKey(), entry.getValue());
        }
        return new KeyCounter[]{result};
    }

    private CraftingTask findExistingTask(PatternKey patternKey) {
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
        int craftCount = Math.max(1, batch.allInputs.size());
        long outputAmount = 1L;
        if (batch.pattern != null && !batch.pattern.getOutputs().isEmpty()) {
            outputAmount = batch.pattern.getOutputs().getFirst().amount();
        }
        int totalOutputCount = outputAmount * craftCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) (outputAmount * craftCount);
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
        private final int outputCount; // 单次产出数量
        private volatile int progress;
        private volatile int maxProgress;
        private volatile int craftCount;
        private volatile int totalOutputCount; // 最终产物总数 = 合成次数 × 单次产出数量
        private volatile String statusKey;
        private volatile String statusDetail;

        public AETaskProgress(String productName, int maxProgress, int craftCount, int totalOutputCount) {
            this(productName, 0, maxProgress, craftCount, totalOutputCount,
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        }

        public AETaskProgress(String productName, int progress, int maxProgress, int craftCount, int totalOutputCount) {
            this(productName, progress, maxProgress, craftCount, totalOutputCount,
                    "gui.useless_mod.advanced_alloy_furnace.ae_task_status.processing", "");
        }

        public AETaskProgress(String productName, int progress, int maxProgress, int craftCount, int totalOutputCount, String statusKey, String statusDetail) {
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

        public int getCraftCount() {return craftCount;}

        public int getTotalOutputCount() {return totalOutputCount;}

        public String getStatusKey() {return statusKey;}

        public String getStatusDetail() {return statusDetail;}

        public void setStatus(String statusKey, String statusDetail) {
            this.statusKey = statusKey;
            this.statusDetail = statusDetail;
        }

        // 更新合成次数和最终产物总数（用于任务合并）
        public void updateCraftCount(int newCraftCount) {
            this.craftCount = newCraftCount;
            this.totalOutputCount = newCraftCount * outputCount;
        }
    }

    private static final class PendingAEBatch {
        final IPatternDetails pattern;
        final List<KeyCounter[]> allInputs = new ArrayList<>();
        int ripeTimer = BATCH_RIPE_TICKS;
        String statusKey = "gui.useless_mod.advanced_alloy_furnace.ae_task_status.queued";
        String statusDetail = "";

        PendingAEBatch(IPatternDetails pattern) {
            this.pattern = pattern;
        }

        void add(KeyCounter[] input) {
            this.allInputs.add(input);
            this.ripeTimer = BATCH_RIPE_TICKS;
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
            IPatternDetails pattern = PatternDetailsHelper.decodePattern(definition.toStack(), level);
            if (pattern == null) {
                return null;
            }
            PendingAEBatch batch = new PendingAEBatch(pattern);
            ListTag craftsTag = tag.getList("Crafts", Tag.TAG_COMPOUND);
            for (int i = 0; i < craftsTag.size(); i++) {
                batch.allInputs.add(readKeyCounters(registries, craftsTag.getCompound(i)));
            }
            return batch;
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

    private record PatternKey(List<GenericStack> outputs) {
        static PatternKey of(IPatternDetails pattern) {
            if (pattern == null || pattern.getOutputs().isEmpty()) {
                return new PatternKey(List.of());
            }
            return new PatternKey(List.copyOf(pattern.getOutputs()));
        }
    }
}
