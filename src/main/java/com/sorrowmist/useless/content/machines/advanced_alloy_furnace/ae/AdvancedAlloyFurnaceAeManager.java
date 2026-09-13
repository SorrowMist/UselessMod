package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.menu.AutoCraftingMenu;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.network.AETaskProgressPacket;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.FurnaceChemicalStorage;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.io.FurnaceOutputPort;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

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
    private static final int UNRETURNED_RETRY_TICKS = 20;
    /**
     * 合成样板的虚拟工作台格数。注意 AE2 传给供应器的输入计数器是<b>压缩后</b>的
     * （相同原料会并成一个输入位），长度不固定，网格才是固定 9 格。
     */
    private static final int CRAFTING_GRID_SIZE = 9;
    /**
     * 一个倍率批次里最多真实装配多少次。
     *
     * <p>正常配方第一次铺料后材料就是整齐的重复，一次即可折叠整批；只有一批里混着不同变体
     * （例如耐久各异的工具）才会多试几次，试到剩下的材料重新变整齐为止。这个上限只用于兜底
     * 病态输入，防止主线程被拖住。</p>
     */
    private static final int MAX_CRAFTING_PROBES = 64;

    /**
     * 批次成熟窗口（tick）：把 AE 的连续推送合并成一个任务，避免每次推送都单独开工。
     * 由配置项 {@code advanced_alloy_furnace.ae_batch_ripe_ticks} 控制，0 表示推送即刻投入执行。
     */
    private static int batchRipeTicks() {
        return ConfigManager.getAdvancedAlloyFurnaceAeBatchRipeTicks();
    }

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
    /**
     * 合成样板自执行的产物队列。
     *
     * <p>合成样板由本机在虚拟 3×3 工作台上装配（万象合金炉没有合成台）。AE2 只在
     * {@code pushPattern} 返回 true 之后才把本批的预期产物写入 CPU 的 {@code waitingFor}，
     * 所以产物必须至少延后一个 tick 再注入网络，否则会被写进通用存储、绕过当前合成任务，
     * 表现为任务永久等待 + 产物翻倍。</p>
     */
    private final List<PendingCraftingOutput> queuedCraftingOutputs = new ArrayList<>();
    private int unreturnedInputRetryTimer = 0;
    private int unreturnedOutputRetryTimer = 0;
    private int patternPriority = 0;
    private int nextTaskId = 0;
    // 延迟加载的任务数据（loadTag 时 level 尚不可用，需推迟到首 tick 解码样板）
    @Nullable
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

        // 合成样板的产物是已经装配完成的实物，AE 任务取消后必须写回网络，否则材料凭空消失。
        // 写不进去的部分留在队列里（随 NBT 持久化），等后续 tick 或重载后继续重试。
        this.flushQueuedCraftingOutputs(true);

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
        addUnreturnedAmount(this.unreturnedInputs, key, amount);
        this.owner.markChanged();
    }

    /** Stores normal recipe output that fits neither AE nor an output chemical slot yet. */
    public void stashUnreturnedOutput(AEKey key, long amount) {
        if (key == null || amount <= 0) {
            return;
        }
        addUnreturnedAmount(this.unreturnedOutputs, key, amount);
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

        ListTag craftingOutputsTag = new ListTag();
        for (PendingCraftingOutput pending : this.queuedCraftingOutputs) {
            ListTag outputTag = new ListTag();
            for (GenericStack gs : pending.outputs) {
                outputTag.add(GenericStack.writeTag(registries, gs));
            }
            CompoundTag entry = new CompoundTag();
            entry.putLong("QueuedTick", pending.queuedTick);
            entry.put("Outputs", outputTag);
            craftingOutputsTag.add(entry);
        }
        tag.put("QueuedCraftingOutputs", craftingOutputsTag);
        tag.putInt("NextTaskId", this.nextTaskId);
    }

    public boolean hasPersistedData() {
        return !this.activeTasks.isEmpty()
                || !this.aePendingBatches.isEmpty()
                || !this.unreturnedInputs.isEmpty()
                || !this.unreturnedOutputs.isEmpty()
                || !this.queuedCraftingOutputs.isEmpty()
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
        CraftingAeAmountAccumulator unreturnedInputAmounts = new CraftingAeAmountAccumulator();
        for (int i = 0; i < unreturnedTag.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, unreturnedTag.getCompound(i));
            if (gs != null && gs.amount() > 0L) {
                unreturnedInputAmounts.add(gs);
            }
        }
        this.unreturnedInputs.addAll(unreturnedInputAmounts.segments());

        ListTag unreturnedOutputsTag = tag.getList("UnreturnedOutputs", Tag.TAG_COMPOUND);
        CraftingAeAmountAccumulator unreturnedOutputAmounts = new CraftingAeAmountAccumulator();
        for (int i = 0; i < unreturnedOutputsTag.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, unreturnedOutputsTag.getCompound(i));
            if (gs != null && gs.amount() > 0L) {
                unreturnedOutputAmounts.add(gs);
            }
        }
        this.unreturnedOutputs.addAll(unreturnedOutputAmounts.segments());

        ListTag craftingOutputsTag = tag.getList("QueuedCraftingOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < craftingOutputsTag.size(); i++) {
            CompoundTag entry = craftingOutputsTag.getCompound(i);
            ListTag outputTag = entry.getList("Outputs", Tag.TAG_COMPOUND);
            CraftingAeAmountAccumulator amounts = new CraftingAeAmountAccumulator();
            for (int j = 0; j < outputTag.size(); j++) {
                GenericStack gs = GenericStack.readTag(registries, outputTag.getCompound(j));
                if (gs != null && gs.amount() > 0L) {
                    amounts.add(gs);
                }
            }
            List<GenericStack> outputs = amounts.segments();
            if (!outputs.isEmpty()) {
                // 重载后重新计时：这批产物尚未回网，必须再等至少一个 tick 才能注入
                this.queuedCraftingOutputs.add(new PendingCraftingOutput(
                        level.getGameTime(), outputs));
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

        // AE2 合成样板：万象合金炉没有合成台，由本机在虚拟 3×3 工作台上自执行。
        // 倍率来自推送方：AE2 会把样板包装成“一次推送代表 N 次合成”，此时输入与预期产物都已按 N 放大。
        if (original instanceof IMolecularAssemblerSupportedPattern craftingPattern) {
            return this.pushCraftingPattern(craftingPattern, execution.operationsPerPush(), inputHolder);
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
        return this.activeTasks.size() >= this.owner.getMaxAETaskCount()
                || this.queuedCraftingOutputs.size() >= this.owner.getMaxAETaskCount();
    }

    /**
     * Returns the number of provider slots that can accept another physical AE submission.
     * Processing patterns use active furnace tasks. Crafting patterns finish synchronously;
     * their queue only provides bounded output backpressure.
     */
    public int getRemainingAETaskCount(boolean craftingPattern) {
        int maximum = Math.max(0, this.owner.getMaxAETaskCount());
        if (craftingPattern) {
            return this.queuedCraftingOutputs.size() >= maximum ? 0 : maximum;
        }
        int occupied = Math.max(0, this.activeAETaskCount.get());
        return Math.max(0, maximum - Math.min(maximum, occupied));
    }

    // ==================== 合成样板自执行 ====================

    /**
     * 在虚拟 3×3 工作台上装配合成样板，并把产物交给延迟回网队列。
     *
     * <p>检验与铺料都在输入副本上进行：任何一步失败都直接返回 {@code false}，
     * AE2 抽出的原材料保持原样，由上层继续寻找其它供应器或重试。</p>
     *
     * <p>倍率 N &gt; 1 时按「可重复的一份」折叠：铺一份料后，如果剩下的材料逐键都恰好是这一份消耗量的
     * (N-1) 倍（模具、可复用催化剂这类同键返还同样适用），说明整批的每一份输入完全一样，
     * 于是只装配一次、把产物与返还款整体放大 N 倍。一批里混着不同变体（例如耐久各异的工具）时这条
     * 不变量先不成立，此时就真实地一份一份装配，直到剩下的材料再次变成整齐的重复 ——
     * 这样连混合变体也能得到与逐份装配一致的结果。探针次数有上限，真撞上病态输入时
     * 按最近一份的产出整体放大，语义与批量合并保持一致。</p>
     *
     * @param operationsPerPush 本次推送代表的合成次数，恒为正
     */
    private boolean pushCraftingPattern(IMolecularAssemblerSupportedPattern pattern, long operationsPerPush,
                                        KeyCounter[] inputHolder) {
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide || !this.owner.isTaskExecutionEnabled()) {
            return false;
        }
        if (inputHolder == null) {
            return false;
        }
        // 背压：上一批产物还没能写回网络时不再接收新批次，避免材料滞留在队列里。
        int taskLimit = this.owner.getMaxAETaskCount();
        if (this.queuedCraftingOutputs.size() >= taskLimit) {
            return false;
        }

        KeyCounter[] working = copyCounters(inputHolder);
        List<GenericStack> produced = new ArrayList<>();
        List<GenericStack> lastUnitOutputs = null;
        long craftsLeft = Math.max(1L, operationsPerPush);
        int probes = 0;

        while (craftsLeft > 0L && probes < MAX_CRAFTING_PROBES) {
            probes++;
            Object2LongMap<AEKey> before = snapshotAmounts(working);
            List<ItemStack> grid = emptyCraftingGrid();
            pattern.fillCraftingGrid(working, (slot, stack) -> {
                if (slot >= 0 && slot < CRAFTING_GRID_SIZE) {
                    grid.set(slot, stack);
                }
            });

            long repeat = 1L;
            if (matchesBatchShape(before, working, craftsLeft)) {
                // 整批材料都是这一份的整齐重复，可以一次算完
                repeat = craftsLeft;
                craftsLeft = 0L;
                clearCounters(working);
            } else {
                craftsLeft--;
            }

            List<GenericStack> unitOutputs = assembleCraftingPattern(pattern, grid, level);
            if (unitOutputs == null) {
                return false;
            }
            lastUnitOutputs = unitOutputs;
            addScaled(produced, unitOutputs, repeat);
        }

        if (craftsLeft > 0L) {
            // 探针预算耗尽：按最近一份的产出整体放大（兜底语义与三位一体式批量一致）
            if (lastUnitOutputs == null) {
                return false;
            }
            addScaled(produced, lastUnitOutputs, craftsLeft);
        }
        if (produced.isEmpty()) {
            return false;
        }

        // 走到这里才算接收：AE2 随后会把本批预期产物写入 CPU 的 waitingFor。
        for (KeyCounter counter : inputHolder) {
            counter.clear();
        }
        this.queuedCraftingOutputs.add(new PendingCraftingOutput(
                level.getGameTime(), produced));
        this.owner.markChanged();
        return true;
    }

    /**
     * 判断铺完一份之后，剩下的材料是否正好是这一份消耗量的 (craftsLeft - 1) 倍 —— 逐键比对。
     *
     * <p>成立时整批的每一份输入完全相同（含同键返还的模具与催化剂），所以「装配一次再整体 ×N」
     * 与真的装配 N 次结果一致。只要有一个键的剩余量对不上（例如一批里混着不同耐久的工具），
     * 就返回 {@code false}，交由调用方继续逐份装配。</p>
     */
    private static boolean matchesBatchShape(Object2LongMap<AEKey> before, KeyCounter[] after, long craftsLeft) {
        Object2LongMap<AEKey> remaining = snapshotAmounts(after);
        long previousCrafts = craftsLeft - 1L;
        for (var entry : before.object2LongEntrySet()) {
            long left = remaining.getLong(entry.getKey());
            long consumed = entry.getLongValue() - left;
            if (consumed <= 0L) {
                return false;
            }
            long expected;
            try {
                expected = Math.multiplyExact(consumed, previousCrafts);
            } catch (ArithmeticException exception) {
                return false;
            }
            if (left != expected) {
                return false;
            }
        }
        return remaining.isEmpty();
    }

    /**
     * 装配一份，返回「主产物 + 容器余料」。
     *
     * <p>与 AE2 分子装配室的装配流程保持一致：用工作台自身的定位输入裁掉空白边距，
     * 先触发合成事件再取余料。异常或空产物都返回 {@code null}，调用方据此放弃本次接管。</p>
     */
    @Nullable
    private List<GenericStack> assembleCraftingPattern(IMolecularAssemblerSupportedPattern pattern,
                                                       List<ItemStack> grid, Level level) {
        TransientCraftingContainer container = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
            container.setItem(slot, grid.get(slot).copy());
        }
        CraftingInput craftingInput = container.asPositionedCraftInput().input();

        ItemStack output;
        NonNullList<ItemStack> remainders;
        try {
            output = pattern.assemble(craftingInput, level);
            if (output.isEmpty()) {
                return null;
            }
            output.onCraftedBySystem(level);
            CraftingEvent.fireAutoCraftingEvent(level, pattern, output, container);
            remainders = pattern.getRemainingItems(craftingInput);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to self-assemble a crafting pattern at {}", this.owner.getBlockPos(), exception);
            return null;
        }

        List<GenericStack> unitOutputs = new ArrayList<>(remainders.size() + 1);
        collectProduced(unitOutputs, output, 1L);
        for (ItemStack remainder : remainders) {
            collectProduced(unitOutputs, remainder, 1L);
        }
        return unitOutputs.isEmpty() ? null : unitOutputs;
    }

    private static List<ItemStack> emptyCraftingGrid() {
        List<ItemStack> grid = new ArrayList<>(CRAFTING_GRID_SIZE);
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    private static void clearCounters(KeyCounter[] counters) {
        for (KeyCounter counter : counters) {
            if (counter != null) {
                counter.clear();
            }
        }
    }

    /** 把一组输入计数器按 AE 键汇总；键仍保留各自的变体身份。 */
    private static Object2LongMap<AEKey> snapshotAmounts(KeyCounter[] counters) {
        Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();
        for (KeyCounter counter : counters) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                if (entry.getKey() == null || entry.getLongValue() <= 0L) {
                    continue;
                }
                long existing = amounts.getLong(entry.getKey());
                amounts.put(entry.getKey(), existing > Long.MAX_VALUE - entry.getLongValue()
                        ? Long.MAX_VALUE : existing + entry.getLongValue());
            }
        }
        return amounts;
    }

    /** 把一份装配的产物流按倍率并入队列。 */
    private static void addScaled(List<GenericStack> target, List<GenericStack> unitOutputs, long multiplier) {
        long scale = Math.max(1L, multiplier);
        for (GenericStack unit : unitOutputs) {
            long amount;
            try {
                amount = Math.multiplyExact(unit.amount(), scale);
            } catch (ArithmeticException exception) {
                // 倍率在包装阶段已过 maximumSafeMultiplier 校验，这里只做兜底：宁可少记也不写坏账
                continue;
            }
            mergeProduced(target, unit.what(), amount);
        }
    }

    /**
     * 把已经装配完成、等待回网的合成样板产物写入 AE。
     *
     * <p>该方法只由方块实体 tick 调用；provider 的 push/commit 已在此之前返回，
     * 所以不需要额外等待一个 tick。写不进去的部分留在队列里逐 tick 重试，
     * 队列满时通过批次数背压新的合成提交，直到产物真正回网。</p>
     */
    public void tickQueuedCraftingOutputs() {
        flushQueuedCraftingOutputs(false);
    }

    /**
     * @param force true 时忽略“必须晚一个 tick”的保护（只用于取消/拆除任务：此时 CPU 已放弃这批产物，
     *              产物落进通用存储也不会被错认，但绝不能丢）
     */
    private void flushQueuedCraftingOutputs(boolean force) {
        if (this.queuedCraftingOutputs.isEmpty()) {
            return;
        }
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        long now = level.getGameTime();
        boolean changed = false;
        var iterator = this.queuedCraftingOutputs.iterator();
        while (iterator.hasNext()) {
            PendingCraftingOutput pending = iterator.next();
            if (!force && pending.queuedTick >= now) {
                continue;
            }
            List<GenericStack> remaining = new ArrayList<>(pending.outputs.size());
            for (GenericStack stack : pending.outputs) {
                long requested = stack.amount();
                long inserted = clampInserted(this.owner.tryOutputKeyToAE(stack.what(), requested), requested);
                long left = requested - inserted;
                if (left > 0L) {
                    remaining.add(new GenericStack(stack.what(), left));
                }
            }
            changed = true;
            if (remaining.isEmpty()) {
                iterator.remove();
            } else {
                pending.replaceOutputs(remaining);
            }
        }
        if (changed) {
            this.owner.markChanged();
        }
    }

    /**
     * 复制 AE2 传入的输入计数器。
     *
     * <p>必须用 {@link KeyCounter#addAll}：它保留同一主键下的变体子表与迭代顺序，
     * 直接遍历再 add 会丢掉这个结构，从而在可替代输入上挑到与 AE2 不同的那一种。</p>
     */
    private static KeyCounter[] copyCounters(KeyCounter[] source) {
        KeyCounter[] copy = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            KeyCounter counter = new KeyCounter();
            KeyCounter original = source[index];
            if (original != null) {
                counter.addAll(original);
            }
            copy[index] = counter;
        }
        return copy;
    }

    /**
     * 把一次装配的单份产出按倍率累加进队列。单份产出乘倍率后可能远超单栈上限，
     * 所以一律以 {@link GenericStack} 的 long 数量表达，不做物化。
     */
    private static void collectProduced(List<GenericStack> target, ItemStack stack, long multiplier) {
        if (stack == null || stack.isEmpty() || stack.getCount() <= 0) {
            return;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return;
        }
        long amount;
        try {
            amount = Math.multiplyExact((long) stack.getCount(), Math.max(1L, multiplier));
        } catch (ArithmeticException exception) {
            // 倍率在包装阶段已经过 maximumSafeMultiplier 校验，这里只做兜底：宁可少记也不写坏账
            return;
        }
        mergeProduced(target, key, amount);
    }

    private static void mergeProduced(List<GenericStack> target, AEKey key, long amount) {
        for (int index = 0; index < target.size(); index++) {
            GenericStack existing = target.get(index);
            if (existing.what().equals(key)) {
                long merged = existing.amount() > Long.MAX_VALUE - amount
                        ? Long.MAX_VALUE : existing.amount() + amount;
                target.set(index, new GenericStack(key, merged));
                return;
            }
        }
        target.add(new GenericStack(key, amount));
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
                || !this.unreturnedInputs.isEmpty() || !this.unreturnedOutputs.isEmpty()
                || !this.queuedCraftingOutputs.isEmpty()) {
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
        // 一个活跃任务都没有时，成熟窗口没有可合并的对象，只是纯粹的等待开销，直接放行。
        // 后续推送仍会由 tickAETasks 合并进刚启动的任务，批量语义不变。
        boolean idle = this.activeTasks.isEmpty();
        synchronized (this.aePendingBatches) {
            var it = this.aePendingBatches.entrySet().iterator();
            ripe = new ArrayList<>();
            while (it.hasNext()) {
                var entry = it.next();
                PendingAEBatch batch = entry.getValue();
                if (!idle && --batch.ripeTimer > 0) {
                    continue;
                }
                ripe.add(batch);
                it.remove();
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
        if (!isPublishReady()) {
            // AE2 snapshots what a provider can craft, so publishing before the node joined a grid
            // would index this machine as unable to craft anything. Keep the request pending.
            return;
        }
        rebuildPatterns();
        try {
            this.owner.onPatternsRebuilt();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to publish alloy furnace patterns at {}", this.owner.getBlockPos(), exception);
            return;
        }
        this.patternRefreshPending = false;
    }

    /** True when the owner's node is on a grid, i.e. when AE2 can actually index its patterns. */
    private boolean isPublishReady() {
        IManagedGridNode node = this.owner.getMainNode();
        return node != null && node.isActive() && node.getNode() != null
                && node.getNode().getGrid() != null;
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
            OmniversalPatternDiagnostics.notPublished("alloy furnace " + this.owner.getBlockPos(), "<all>",
                    "this machine cannot publish patterns yet");
            return;
        }

        int seen = 0;
        int decoded = 0;
        int slot = -1;
        for (ItemStack stack : this.owner.getPatternStacks()) {
            slot++;
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
                        OmniversalPatternDiagnostics.notPublished("alloy furnace " + this.owner.getBlockPos(),
                                slot, pattern == null
                                        ? "the pattern could not be decoded"
                                        : "the pattern is not accepted by this machine");
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
        if (stacks.isEmpty()) return;
        mergeUnreturnedAmounts(this.unreturnedInputs, stacks);
        this.owner.markChanged();
    }

    public void addUnreturnedOutputs(List<GenericStack> stacks) {
        if (stacks == null) return;
        if (stacks.isEmpty()) return;
        mergeUnreturnedAmounts(this.unreturnedOutputs, stacks);
        this.owner.markChanged();
    }

    private static void addUnreturnedAmount(List<GenericStack> target, AEKey key, long amount) {
        CraftingAeAmountAccumulator accumulator = CraftingAeAmountAccumulator.fromGenericStacks(target);
        accumulator.add(key, amount);
        target.clear();
        target.addAll(accumulator.segments());
    }

    private static void mergeUnreturnedAmounts(
            List<GenericStack> target,
            List<GenericStack> additions) {
        CraftingAeAmountAccumulator accumulator = CraftingAeAmountAccumulator.fromGenericStacks(target);
        for (GenericStack addition : additions) {
            if (addition != null && addition.what() != null && addition.amount() > 0L) {
                accumulator.add(addition);
            }
        }
        target.clear();
        target.addAll(accumulator.segments());
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
            @Nullable KeyCounter[] inputHolder) {
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
            target.ripeTimer = batchRipeTicks();
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
        Object2LongOpenHashMap<AEKey> currentAmounts = new Object2LongOpenHashMap<>();
        long currentCrafts = 0L;
        boolean currentRequiresIsolation = false;

        for (KeyCounter[] input : allInputs) {
            Object2LongOpenHashMap<AEKey> inputAmounts = collectExactKeyAmounts(input);
            boolean fitsCurrentBatch = !current.isEmpty()
                    && !currentRequiresIsolation
                    && safeOperationsPerPush <= safeMaximumTaskCrafts - currentCrafts
                    && inputAmounts != null
                    && canAddExact(currentAmounts, inputAmounts);
            if (!current.isEmpty() && !fitsCurrentBatch) {
                result.add(current);
                current = new ArrayList<>();
                currentAmounts = new Object2LongOpenHashMap<>();
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

        Object2LongOpenHashMap<AEKey> merged = new Object2LongOpenHashMap<>();
        for (KeyCounter[] counters : allInputs) {
            if (counters == null) continue;
            for (KeyCounter counter : counters) {
                if (counter == null) continue;
                for (var entry : counter) {
                    long amount = entry.getLongValue();
                    if (amount <= 0L) continue;
                    long current = merged.getLong(entry.getKey());
                    if (current > Long.MAX_VALUE - amount) {
                        throw new IllegalStateException("Attempted to merge AE inputs beyond long range");
                    }
                    merged.put(entry.getKey(), current + amount);
                }
            }
        }

        KeyCounter result = new KeyCounter();
        for (var entry : merged.object2LongEntrySet()) {
            result.add(entry.getKey(), entry.getLongValue());
        }
        return new KeyCounter[]{result};
    }

    @Nullable
    private static Object2LongOpenHashMap<AEKey> collectExactKeyAmounts(KeyCounter[] counters) {
        Object2LongOpenHashMap<AEKey> result = new Object2LongOpenHashMap<>();
        if (counters == null) return result;
        for (KeyCounter counter : counters) {
            if (counter == null) continue;
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount <= 0L) continue;
                long current = result.getLong(entry.getKey());
                if (current > Long.MAX_VALUE - amount) {
                    return null;
                }
                result.put(entry.getKey(), current + amount);
            }
        }
        return result;
    }

    private static boolean canAddExact(Object2LongMap<AEKey> target, Object2LongMap<AEKey> additions) {
        for (var entry : additions.object2LongEntrySet()) {
            if (target.getLong(entry.getKey()) > Long.MAX_VALUE - entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private static void addExact(Object2LongMap<AEKey> target, Object2LongMap<AEKey> additions) {
        for (var entry : additions.object2LongEntrySet()) {
            target.put(entry.getKey(), target.getLong(entry.getKey()) + entry.getLongValue());
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

    /** 一次合成样板自执行的产出（主产物 + 容器余料），等待在安全时机注入 AE 网络。 */
    static final class PendingCraftingOutput {
        final long queuedTick;
        final List<GenericStack> outputs;

        PendingCraftingOutput(long queuedTick, List<GenericStack> outputs) {
            this.queuedTick = queuedTick;
            this.outputs = new ArrayList<>(outputs);
        }

        void replaceOutputs(List<GenericStack> remaining) {
            this.outputs.clear();
            this.outputs.addAll(remaining);
        }
    }

    static final class PendingAEBatch {
        final IPatternDetails pattern;
        final long operationsPerPush;
        final List<KeyCounter[]> allInputs = new ArrayList<>();
        int ripeTimer = batchRipeTicks();
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

        @Nullable
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
        for (GenericStack amount : CraftingAeAmountAccumulator.fromCounters(counters).segments()) {
            list.add(GenericStack.writeTag(registries, amount));
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Stacks", list);
        return tag;
    }

    private static KeyCounter[] readKeyCounters(HolderLookup.Provider registries, CompoundTag tag) {
        ListTag list = tag.getList("Stacks", Tag.TAG_COMPOUND);
        CraftingAeAmountAccumulator accumulator = new CraftingAeAmountAccumulator();
        for (int i = 0; i < list.size(); i++) {
            GenericStack gs = GenericStack.readTag(registries, list.getCompound(i));
            if (gs != null && gs.amount() > 0L) {
                accumulator.add(gs);
            }
        }
        List<GenericStack> amounts = accumulator.segments();
        if (amounts.isEmpty()) {
            return new KeyCounter[]{new KeyCounter()};
        }
        KeyCounter[] counters = new KeyCounter[amounts.size()];
        for (int index = 0; index < amounts.size(); index++) {
            GenericStack amount = amounts.get(index);
            KeyCounter counter = new KeyCounter();
            counter.add(amount.what(), amount.amount());
            counters[index] = counter;
        }
        return counters;
    }

    private record PatternKey(@Nullable AEItemKey definition) {
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
