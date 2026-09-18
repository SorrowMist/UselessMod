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
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchContext;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchResult;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapter;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapters;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import com.sorrowmist.useless.energy.IEnergyManager;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.integration.dataenergistics.TrinityDispatchDiagnostics;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
     * 回网队列里单条 pending 的持久化格式版本。
     *
     * <p>1（隐式，无 {@code Format} 标签）= 逐段写的 {@code GenericStack} 列表；
     * 2 = 按键聚合的 BigInteger（见 {@link CraftingAeAmountAccumulator#writeCompactTag}）。</p>
     *
     * <p>为什么必须换格式：产物总量可以远超 {@code long}，按 {@code Long.MAX} 切段后
     * 1e22 个物品就是上千条 NBT，而队列深度等于线程数 —— 整条队列能到 GB 级，
     * 每次区块存盘都要重写。聚合后每个键只占一条，重载时再展开，语义完全等价。</p>
     */
    private static final byte QUEUED_OUTPUT_FORMAT_COMPACT = 2;

    /** AE 存储 API 的单次上限，也是切段粒度（与数据能源的 {@code PHYSICAL_CHUNK} 一致）。 */
    private static final BigInteger MAX_OUTPUT_CHUNK = BigInteger.valueOf(Long.MAX_VALUE);
    /**
     * 连续多少个键被拒收（插入返回 0）就认为网络整体饱和，提前结束本轮。
     *
     * <p>没有它就只能在「网络不可达 + 键很多」时逐键空转。正常键数很小，影响可忽略。</p>
     */
    private static final int MAX_EMPTY_KEY_PROBES = 8;

    // ==================== 回网单批规模：AIMD 控制器 ====================
    //
    // 为什么用控制器而不是公式：单批规模该多大取决于「交付能力」与「调度侧派发频率」，
    // 前者随存储/网络变化，后者是调度侧内部行为 —— 两者都无法在编译期算准。
    // 所以改成**用实测反馈探测**（TCP 拥塞控制那一套）：
    //   慢启动：无积压时每 tick ×10，快速探到可用上限；
    //   乘法减小：一旦积压超过「一批在飞」的规模就减半，并退出慢启动；
    //   线性回升：之后无积压时逐步 +step 缓升。
    // 收敛点是「一批大约一两个 tick 就交付完」，也就是最平滑的形态。

    /**
     * 还没测到插入成本前的初始估计（纳秒/段）。
     *
     * <p>偏大 ⇒ 起步批次偏小、爬坡慢；偏小 ⇒ 首批偏大、可能积压。取 1000ns（略保守于实测的
     * 650ns）：起步批次约为目标的 2/3，几批之内就被实测值取代，既不浪费爬坡时间也不冒险。</p>
     */
    private static final double DEFAULT_INSERT_NANOS_PER_CHUNK = 1_000.0D;
    /** 实测插入成本的平滑系数（新样本占 1/8）。 */
    private static final double INSERT_COST_SMOOTHING = 0.125D;
    /**
     * 有效样本要求的最少交付段数。
     *
     * <p><b>为什么必须有这个门槛</b>：样本是 {@code 插入耗时 ÷ 交付段数}，而<b>每 tick 有固定开销</b>
     * （首次插入的缓存冷、跨存储遍历等）。交付段数很少时固定开销会主导样本 ——
     * 实测日志里出现过 {@code deliveredChunks=1, insertUs=41}，即 41µs/段，
     * 是真实值（0.65µs）的 <b>63 倍</b>。一个这样的样本会把单批预算直接砸下去，
     * 再慢慢爬回来 —— 正是用户反馈的「降档一次降得太多、再爬坡也慢」。
     * 要求至少 256 段后，固定开销被摊薄到可忽略（41µs/256 ≈ 160ns）。</p>
     */
    private static final long MIN_INSERT_SAMPLE_CHUNKS = 1_024L;
    /**
     * 单次采样允许「成本估计」上升的最大比例 —— 也就是<b>限制降档幅度</b>。
     *
     * <p><b>为什么必须非对称</b>：成本估计升高通常来自<b>噪声</b>（首次插入缓存冷、GC、
     * 跨存储遍历的固定开销），而不是真的变慢；而它一旦升高，单批预算就立刻按比例下降。
     * 无限制时会看到「一次降档降太多，再爬坡又慢」。
     * 所以：<b>升档不限速</b>（成本下降立刻放大批次，不浪费爬坡时间），
     * <b>降档每次最多 1%</b>（噪声最多造成 1% 损失，且下一批就能恢复）。</p>
     *
     * <p>代价：存储真的永久变慢时，预算要 ~70 批才收敛到位 —— 这期间积压会略涨，
     * 由 {@link #hardOutputChunkBudget()} 兜底。之所以敢取这么慢：噪声是<b>单侧</b>的
     * （只会让耗时偏高），而真的变慢会持续出现，慢慢收即可。</p>
     */
    private static final double MAX_INSERT_COST_RISE_PER_SAMPLE = 1.01D;
    /** 成本估计下限：防止除零与荒谬的大批次。 */
    private static final double MIN_INSERT_NANOS_PER_CHUNK = 50.0D;
    /**
     * 安全系数：单批按「半 tick 可交付量」定尺。
     *
     * <p>调度侧可能一 tick 派发不止一批；取一半可保证到达 ≤ 交付，积压自然收敛在一两批。</p>
     */
    private static final long OUTPUT_BUDGET_SAFETY_DIVISOR = 2L;
    /**
     * 回网积压硬上限相对「单批预算上限」的倍数：<b>只作内存 / NBT 兜底</b>。
     *
     * <p>控制器正常工作时积压收敛在「一两批在飞」（≈2×当前预算），离这里极远。</p>
     */
    private static final long OUTPUT_BACKLOG_HARD_MULTIPLIER = 8L;
    /**
     * 回网积压的<b>绝对</b>硬上限（分段数）：约 256K 段 ≈ 10MB NBT。
     *
     * <p>刻意用绝对值而不是「单批上限 × 倍数」：单批上限会随交付能力调整，
     * 若硬上限跟着放大，内存 / NBT 兜底就形同虚设。</p>
     */
    private static final long HARD_OUTPUT_CHUNK_BUDGET = 262_144L;
    /**
     * 每台机器每 tick 的产物回网时间预算（纳秒），由配置项
     * {@code advanced_alloy_furnace.ae_output_return_budget_millis} 给出。
     *
     * <p><b>它直接决定可持续合成速度</b>：AE2 存储接口单次只能写一个 {@code long} 分段，
     * 产物必须逐段插入，所以每 tick 能插多少次就决定了能跑多快。</p>
     *
     * <p>实际生效值还要按 {@link AlloyFurnaceTickBudget#scaledFlushBudget(long)} 动态收窄：
     * 全局预算被打满时按比例缩小（下限 250µs）。</p>
     */
    private static long flushBudgetNanos() {
        return ConfigManager.getAdvancedAlloyFurnaceAeOutputReturnBudgetMillis() * 1_000_000L;
    }

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
    /**
     * 回网积压的<b>真实工作量</b>：队列里所有条目还欠的产物总量（精确 BigInteger）。
     *
     * <p><b>为什么不能用「条目数」当积压度量</b>：一个条目可以装着上百万段
     * （线圈并行上百万时，单批产物就是百万段），条目数离阈值差着六个数量级，
     * 按条目数降级永远不触发 —— 表现为「首批过大、降频追不上积压」。</p>
     *
     * <p>用<b>金额</b>而不是「分段计数」是为了<b>零漂移</b>：入队加总量、交付减实际交付量，
     * 两者都是精确值；而分段计数在「部分吸收」时无法精确扣减，会越用越偏。
     * 需要分段口径时按 {@code 金额 / Long.MAX} 现算即可。</p>
     *
     * <p>只在服务端主线程维护：入队加、交付减、读档重算。</p>
     */
    private BigInteger pendingOutputAmount = BigInteger.ZERO;
    /**
     * 实测「写回一个分段」的平均耗时（纳秒/段，指数平滑）。
     *
     * <p>单批分段预算由它<b>前馈</b>算出：{@code 时间预算 ÷ 本值 ÷ 安全系数}。
     * 没有闭环就没有振荡，也不会像反馈环那样卡在低位；存储快慢一变它会自动跟上。</p>
     */
    private double measuredInsertNanosPerChunk = DEFAULT_INSERT_NANOS_PER_CHUNK;
    /** 是否已经采到过有效样本（首个有效样本直接采纳，不走平滑，避免慢慢爬坡）。 */
    private boolean insertCostMeasured;
    /**
     * 回网诊断日志的上报间隔（tick）。
     *
     * <p>回网每 tick 都跑，逐 tick 打日志会刷屏；20 tick 一条足够看出积压趋势。</p>
     */
    private static final long OUTPUT_DIAGNOSTICS_INTERVAL_TICKS = 20L;
    /** 上次回网诊断日志的 tick；{@link Long#MIN_VALUE} 表示还没打过。 */
    private long lastOutputDiagnosticsTick = Long.MIN_VALUE;
    /** 当前是否处于「回网积压」状态（用于状态跃迁日志的迟滞，避免刷屏）。 */
    private boolean outputBacklogNoticed;
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
        // 大数批次先给 CPU 侧一个终局信号：产物照样会强制写回，不会丢，只是不再走逐 tick 节奏。
        for (PendingCraftingOutput pending : this.queuedCraftingOutputs) {
            notifyCpuBatchCancelled(pending);
        }
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
            CompoundTag entry = new CompoundTag();
            entry.putLong("QueuedTick", pending.queuedTick);
            entry.putByte("Format", QUEUED_OUTPUT_FORMAT_COMPACT);
            // 内存里就是按键 BigInteger 账本，与紧凑格式 1:1，直接写即可（无需再聚合）。
            entry.put("Outputs", pending.ledger.writeCompactTag(registries));
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
            // 老存档没有 Format 标签（getByte 返回 0），自动走逐段格式的读取分支。
            CraftingAeAmountAccumulator amounts = entry.getByte("Format") >= QUEUED_OUTPUT_FORMAT_COMPACT
                    ? CraftingAeAmountAccumulator.readCompactTag(registries, outputTag)
                    : readLegacyCraftingOutputs(registries, outputTag);
            if (!amounts.isEmpty()) {
                // 重载后重新计时：这批产物尚未回网，必须再等至少一个 tick 才能注入。
                // 账本直接交给条目（构造函数会拷贝一份隔离）。
                enqueueCraftingOutput(new PendingCraftingOutput(
                        level.getGameTime(), amounts));
            }
        }
    }

    /** 旧存档格式（Format &lt; 2）：逐段写的 {@code GenericStack} 列表，每个 {@code Long.MAX} 一段。 */
    private static CraftingAeAmountAccumulator readLegacyCraftingOutputs(
            HolderLookup.Provider registries, ListTag outputTag) {
        CraftingAeAmountAccumulator amounts = new CraftingAeAmountAccumulator();
        for (int index = 0; index < outputTag.size(); index++) {
            GenericStack stack = GenericStack.readTag(registries, outputTag.getCompound(index));
            if (stack != null && stack.amount() > 0L) {
                amounts.add(stack);
            }
        }
        return amounts;
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
        // 只有到「硬上限」才报忙：在此之前单批规模由 AIMD 控制器收敛，容量不会归零。
        // 报忙会让 AE2 直接跳过本供应器（又一个二元门），把控制器的意义抵消掉。
        return this.activeTasks.size() >= this.owner.getMaxAETaskCount()
                || isOutputBacklogFull();
    }

    /**
     * 合成样板「回网队列」的容量 = 本机的线圈线程数。
     *
     * <p>线程数既是炉子的并行度、也是它一 tick 能接多少个合成样板窗口：三位一体按窗口派发，
     * 每个窗口的产物要在这里排一次队再回网，所以队列深度跟线程数对齐（想更快就升级线圈线程数）。
     * 超过这个深度时同时通过 {@code isBusy()} 与容量上报挡住，避免材料滞留在队列里。</p>
     */
    private int craftingPatternQueueCap() {
        return Math.max(1, this.owner.getMaxAETaskCount());
    }

    /** 单批分段预算的<b>天花板</b>：线程数（保留「一份窗口一段」语义）与硬上限取小。 */
    private long outputSegmentBudgetCap() {
        return Math.max(1L, Math.min(this.owner.getMaxAETaskCount(),
                AlloyFurnaceBigIntegerCrafting.MAX_OUTPUT_SEGMENT_BUDGET));
    }

    /**
     * 回网积压的硬上限（分段数）：<b>只作内存 / NBT 兜底</b>。
     *
     * <p>控制器正常工作时积压收敛在「一两批在飞」的量级，离这里很远；只有网络长期不可达
     * （交付速率 0、积压只增不减）时才会碰到 —— 那种情况下拒收才是对的（产物没地方放）。</p>
     */
    private long hardOutputChunkBudget() {
        return HARD_OUTPUT_CHUNK_BUDGET;
    }

    /** 回网队列的<b>条目数</b>硬上限：与分段硬上限互为双保险。 */
    private int hardOutputQueueCap() {
        long cap = (long) craftingPatternQueueCap() * OUTPUT_BACKLOG_HARD_MULTIPLIER;
        return (int) Math.max(2L, Math.min(Integer.MAX_VALUE, cap));
    }

    /** 回网积压是否已到硬上限（真拒收）。分段与条目两个维度任一越界即算满。 */
    private boolean isOutputBacklogFull() {
        return backlogChunks(this.pendingOutputAmount) >= hardOutputChunkBudget()
                || this.queuedCraftingOutputs.size() >= hardOutputQueueCap();
    }

    /** 把产物总量换算成 {@code long} 分段数（饱和 long）。 */
    private static long backlogChunks(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0L;
        }
        BigInteger[] quotientAndRemainder = amount.divideAndRemainder(MAX_OUTPUT_CHUNK);
        long whole = quotientAndRemainder[0].compareTo(MAX_OUTPUT_CHUNK) >= 0
                ? Long.MAX_VALUE : quotientAndRemainder[0].longValueExact();
        return saturatingAdd(whole, quotientAndRemainder[1].signum() > 0 ? 1L : 0L);
    }

    /** 增减回网积压总量（不会低于 0）。 */
    private void addPendingOutputAmount(BigInteger delta) {
        if (delta == null || delta.signum() == 0) {
            return;
        }
        BigInteger updated = this.pendingOutputAmount.add(delta);
        this.pendingOutputAmount = updated.signum() <= 0 ? BigInteger.ZERO : updated;
    }

    /**
     * 把一条待回网产物入队，并同步积压总量。
     *
     * <p>所有入队都必须走这里 —— 漏一处就会让积压度量失真（进而影响 {@link #outputSegmentBudget()} 的判断）。</p>
     */
    private void enqueueCraftingOutput(PendingCraftingOutput pending) {
        this.queuedCraftingOutputs.add(pending);
        addPendingOutputAmount(pending.ledger.totalAmount());
    }

    /**
     * 本 tick 允许<b>单个批次</b>产生的分段数。
     *
     * <p>由宿主方块实体通过 {@link CraftingTaskContext#outputSegmentBudget()} 转发给上层，
     * 再传进 {@link AlloyFurnaceBigIntegerCrafting#maximumSegmentedCount}。</p>
     *
     * <p><b>前馈算出，不用反馈环</b>：{@code 时间预算 ÷ 实测单段插入耗时 ÷ 安全系数}
     * —— 也就是「半个 tick 能交付多少段」。单批恰好能在一两个 tick 内交付完，
     * 既不会因首批过大而长时间积压，也不会因单批过小而浪费吞吐。</p>
     *
     * <p><b>为什么删掉反馈环</b>：先后试过「开关式 AIMD」与「比例调节」两版反馈控制 ——
     * 前者必然锯齿（拥塞信号与被控量同量级），后者增长太慢且目标自引用导致恢复力弱、会卡在低位。
     * 而交付能力其实<b>可以直接测出来</b>（实测单段插入 0.65µs），所以不需要「试」：
     * 前馈没有闭环就没有振荡，也不会卡死。</p>
     */
    public long outputSegmentBudget() {
        long cap = outputSegmentBudgetCap();
        long perTick = (long) (flushBudgetNanos() / Math.max(1.0D, this.measuredInsertNanosPerChunk));
        return Math.max(1L, Math.min(cap, perTick / OUTPUT_BUDGET_SAFETY_DIVISOR));
    }


    /**
     * 回网诊断（DEBUG，每 {@link #OUTPUT_DIAGNOSTICS_INTERVAL_TICKS} tick 最多一条）。
     *
     * <p><b>为什么需要它</b>：超大量合成时「任务之间的停顿」很难从游戏里看出根因 ——
     * 是回网积压把新批次压小了？还是调度侧本身慢？这条日志把四个关键量一次打出来：</p>
     * <ul>
     *   <li>{@code pending} —— 待回网分段数（积压）；持续上涨说明交付跟不上派发；</li>
     *   <li>{@code delivered} —— 本 tick 实际交付的分段数（交付速率）；</li>
     *   <li>{@code budgetUs} —— 本 tick 生效的回网时间预算（µs）；被降到下限说明服务端整体过载；</li>
     *   <li>{@code segmentBudget} —— AIMD 控制器当前给出的单批分段预算；
     *       贴近交付速率说明已收敛，贴近下限说明正在为积压大幅让路。</li>
     * </ul>
     *
     * <p>打开方式：日志配置里把 {@code com.sorrowmist.useless} 设为 {@code DEBUG}。</p>
     */
    private void logOutputReturnDiagnostics(long now, long flushStartedNanos, long flushBudgetNanos,
                                            long insertWorkNanos, BigInteger deliveredTotal) {
        if (deliveredTotal.signum() <= 0 || !LOGGER.isDebugEnabled()) {
            return;
        }
        if (this.lastOutputDiagnosticsTick != Long.MIN_VALUE
                && now - this.lastOutputDiagnosticsTick < OUTPUT_DIAGNOSTICS_INTERVAL_TICKS) {
            return;
        }
        this.lastOutputDiagnosticsTick = now;
        long spentNanos = System.nanoTime() - flushStartedNanos;
        long deliveredChunks = backlogChunks(deliveredTotal);
        // insertUs/deliveredChunks 就是每次插入的真实成本 —— 它决定了吞吐的物理上限
        // （吞吐 ≈ 每 tick 可插入次数 × 每段物品数）。
        LOGGER.debug(
                "Alloy furnace output return at {}: entries={}, pendingChunks={}, deliveredChunks={}, "
                        + "spentUs={}, insertUs={}, budgetUs={}, segmentBudget={}, throttleScale={}",
                this.owner.getBlockPos(),
                this.queuedCraftingOutputs.size(),
                backlogChunks(this.pendingOutputAmount),
                deliveredChunks,
                spentNanos / 1_000L,
                insertWorkNanos / 1_000L,
                flushBudgetNanos / 1_000L,
                outputSegmentBudget(),
                AlloyFurnaceTickBudget.scale());
    }

    /**
     * Returns the number of provider slots that can accept another physical AE submission.
     * Processing patterns use active furnace tasks. Crafting patterns finish synchronously;
     * their queue only provides bounded output backpressure.
     *
     * <p>两个分支都返回<b>真实剩余</b>槽位而不是「未满就返回满」：上层会把每一条剩余槽位
     * 当成一条独立的 TARGETED route 上报给三位一体，报多了等于超卖自己的并发。</p>
     */
    public int getRemainingAETaskCount(boolean craftingPattern) {
        int maximum = Math.max(0, this.owner.getMaxAETaskCount());
        if (craftingPattern) {
            // 用「硬上限」而不是软阈值算槽位：回网积压不该把可用槽位直接打到 0
            //（那会让调度侧停一拍再重启，表现为合成不丝滑）；单批规模由 AIMD 控制器收敛。
            int cap = hardOutputQueueCap();
            return Math.max(0, cap - Math.min(cap, this.queuedCraftingOutputs.size()));
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
        // 只在「硬上限」拒收 —— 在此之前单批规模由 AIMD 控制器收敛，容量不会归零，
        // 所以正常永远走不到这里（它只是网络长期不可达时的兜底）。
        if (isOutputBacklogFull()) {
            return false;
        }

        KeyCounter[] working = copyCounters(inputHolder);
        // 产出一律先并入 BigInteger 账本再切段：倍率可以到 long 上限，逐条 long 累加一定会丢账。
        CraftingAeAmountAccumulator produced = new CraftingAeAmountAccumulator();
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
            accumulateScaled(produced, unitOutputs, repeat);
        }

        if (craftsLeft > 0L) {
            // 探针预算耗尽：按最近一份的产出整体放大（兜底语义与三位一体式批量一致）
            if (lastUnitOutputs == null) {
                return false;
            }
            accumulateScaled(produced, lastUnitOutputs, craftsLeft);
        }
        if (produced.isEmpty()) {
            return false;
        }

        // 走到这里才算接收：AE2 随后会把本批预期产物写入 CPU 的 waitingFor。
        for (KeyCounter counter : inputHolder) {
            counter.clear();
        }
        enqueueCraftingOutput(new PendingCraftingOutput(
                level.getGameTime(), produced));
        TrinityDispatchDiagnostics.reportCraftingPatternWindow(
                level.getGameTime(), Math.max(1L, operationsPerPush), probes);
        this.owner.markChanged();
        return true;
    }

    // ==================== 原生 bigint（exact）批次 ====================

    /**
     * 接收数据能源 3.3.0 的原生 bigint 批次（{@code BigIntegerCraftingProviderAdapter}）。
     *
     * <p>与 {@link #pushPattern} 的长版倍率路径有两处关键差异：</p>
     * <ul>
     *   <li>拿到的 prototype 是<b>单次合成的原型</b>：DE 只把次数通过 {@code exactCount()} 交给我们，
     *       剩下的 {@code count-1} 份材料由 DE 自己的 BigInteger 账本扣除，所以我们只消费手里这一份原型；</li>
     *   <li>批次是「完整的同质批」：DE 保证整批输入完全一致，因此装配一次再整体 ×count 就是精确结果
     *       —— 不需要 {@link #matchesBatchShape} 那套逐键折叠探测。</li>
     * </ul>
     *
     * <p>只处理合成样板；其它样板返回 {@code false}，继续由长版 counted 路径服务。</p>
     */
    /**
     * 数据能源适配器的 bigint 提交入口（{@code BigIntegerPush}，无 CPU 回执）。
     *
     * @see #pushBigIntegerBatch(IPatternDetails, BigInteger, KeyCounter[], AlloyFurnaceBigIntegerCpuBinding)
     */
    public boolean pushBigIntegerCraftingPattern(IPatternDetails patternDetails,
                                                 BigInteger count,
                                                 KeyCounter[] unitPrototype) {
        return pushBigIntegerBatch(patternDetails, count, unitPrototype, null);
    }

    /**
     * 原生 bigint 批次的<b>统一入口</b>：按样板种类路由到对应的折叠实现。
     *
     * <ul>
     *   <li><b>万象样板</b>（{@link OmniversalPatternDetails}）：解析绑定配方一次 → 产物 ×count，
     *       按 {@code count × 单份能耗} 收能量；</li>
     *   <li><b>AE2 合成样板</b>（{@link IMolecularAssemblerSupportedPattern}）：在虚拟 3×3 工作台上
     *       装配一次 → 产物 ×count，<b>不收能量</b>；</li>
     *   <li>其它样板返回 {@code false}，由长版 counted 路径继续服务。</li>
     * </ul>
     *
     * @param cpuBinding CPU 侧回执绑定；{@code null} 表示产物只切段写回 ME 网络
     */
    public boolean pushBigIntegerBatch(IPatternDetails patternDetails,
                                       BigInteger count,
                                       KeyCounter[] unitPrototype,
                                       @Nullable AlloyFurnaceBigIntegerCpuBinding cpuBinding) {
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide || !this.owner.isTaskExecutionEnabled()) {
            return false;
        }
        if (count == null || count.signum() <= 0 || unitPrototype == null) {
            return false;
        }
        IPatternDetails original = SmartDoublingPatterns.unwrap(patternDetails);
        if (original instanceof OmniversalPatternDetails omniversal) {
            return commitOmniversalBatch(omniversal, count, unitPrototype, cpuBinding);
        }
        return original instanceof IMolecularAssemblerSupportedPattern craftingPattern
                && commitCraftingBatch(craftingPattern, count, unitPrototype, cpuBinding);
    }

    /**
     * 合成样板的 bigint 折叠：在虚拟 3×3 工作台上装配<b>一次</b>，产物整体 ×count。
     *
     * <p>不按 count 收能量 —— 与长版 counted 路径的合成样板分支一致（那条路同样是装配一次后按倍率
     * 折叠，不建真实加工任务）。</p>
     */
    private boolean commitCraftingBatch(IMolecularAssemblerSupportedPattern craftingPattern,
                                        BigInteger count,
                                        KeyCounter[] unitPrototype,
                                        @Nullable AlloyFurnaceBigIntegerCpuBinding cpuBinding) {
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        // 背压：只在「硬上限」拒收。在此之前单批规模由 AIMD 控制器收敛，容量不会归零。
        if (isOutputBacklogFull()) {
            return false;
        }

        long useless$bigintStarted = System.nanoTime();
        KeyCounter[] working = copyCounters(unitPrototype);
        List<ItemStack> grid = emptyCraftingGrid();
        craftingPattern.fillCraftingGrid(working, (slot, stack) -> {
            if (slot >= 0 && slot < CRAFTING_GRID_SIZE) {
                grid.set(slot, stack);
            }
        });
        List<GenericStack> unitOutputs = assembleCraftingPattern(craftingPattern, grid, level);
        if (unitOutputs == null) {
            return false;
        }

        CraftingAeAmountAccumulator produced = new CraftingAeAmountAccumulator();
        accumulateScaled(produced, unitOutputs, count);
        if (produced.isEmpty()) {
            return false;
        }

        // 走到这里才算接收：清空收到的单位原型（count-1 份由调用方的账本扣除），产物进回网队列。
        AlloyFurnaceBigIntegerBatchContext cpuContext = cpuBinding == null
                ? null : buildCpuContext(craftingPattern, count, 0L, cpuBinding);
        PendingCraftingOutput pending = new PendingCraftingOutput(
                level.getGameTime(),
                produced,
                AlloyFurnaceBigIntegerCrafting.scaledOutputs(unitOutputs, count),
                cpuContext,
                cpuBinding == null ? null : cpuBinding.adapterId());
        for (KeyCounter counter : unitPrototype) {
            counter.clear();
        }
        enqueueCraftingOutput(pending);
        if (cpuContext != null) {
            notifyCpuBatchAdmitted(cpuContext, cpuBinding.adapterId());
        }
        TrinityDispatchDiagnostics.reportCraftingPatternWindow(level.getGameTime(), count, 1);
        // 报账给「每 tick 时间预算」：超预算时后续批次的窗口预算会被自动收窄
        AlloyFurnaceTickBudget.addWork(System.nanoTime() - useless$bigintStarted);
        this.owner.markChanged();
        return true;
    }

    // ==================== 万象样板的原生 bigint（exact）批次 ====================

    /**
     * 接收万象样板的原生 bigint 批次（<b>折叠</b>语义）。
     *
     * <p>与合成样板那条 bigint 路径的区别：万象样板绑定了一个合金炉配方，所以「单份产出」直接来自
     * 配方（主产物 + 流体 + 隐藏键产出），代价是本机要按 {@code count × 单份能耗} 收能量；
     * 合成样板则是虚拟工作台装配一次再放大，不收能量。</p>
     *
     * <p><b>检查顺序不能改</b>：配方可用性（档次/模具）、回网背压、能量这三类可能失败的事全部排在
     * 「清空原型」之前 —— 返回 {@code false} 时调用方手里的原型完好无损，它自己扣掉的那
     * {@code count - 1} 份也能安全回滚。</p>
     *
     * @param count         份数，可以超过 {@code long}
     * @param unitPrototype <b>单次推送</b>的原型（不是 ×count 的整批材料）
     * @param cpuBinding    CPU 侧回执绑定；{@code null} 表示产物只切段写回 ME 网络
     */
    private boolean commitOmniversalBatch(OmniversalPatternDetails pattern,
                                          BigInteger count,
                                          KeyCounter[] unitPrototype,
                                          @Nullable AlloyFurnaceBigIntegerCpuBinding cpuBinding) {
        Level level = this.owner.getLevel();
        if (level == null || level.isClientSide || !this.owner.isTaskExecutionEnabled()) {
            return false;
        }
        if (!this.owner.supportsBigIntegerRecipeBatches()) {
            return false;
        }
        AdvancedAlloyFurnaceRecipe recipe = pattern.recipe();
        if (recipe == null || !this.owner.isTaskRecipeAvailable(recipe)) {
            return false;
        }
        // 背压：与合成样板共用「回网队列」，同样只在硬上限拒收（折叠语义不建长任务，不占 activeTasks 名额）。
        if (isOutputBacklogFull()) {
            return false;
        }
        long manualOperations = SmartDoublingPatterns.manualOperationsPerPattern(recipe, pattern);
        if (manualOperations <= 0L) {
            return false;
        }
        List<GenericStack> unitOutputs = AlloyFurnaceBigIntegerCrafting.unitOutputs(recipe, manualOperations);
        if (unitOutputs.isEmpty()) {
            return false;
        }
        // 先把产出算出来（纯计算、不改状态），再扣能量 —— 这样任何一条失败路径都不会「扣了能量却不接收」。
        CraftingAeAmountAccumulator produced = new CraftingAeAmountAccumulator();
        accumulateScaled(produced, unitOutputs, count);
        if (produced.isEmpty()) {
            return false;
        }
        // 能量：口径与长版任务的 calculateTargetTotalEnergy 完全一致（与并行相关时按 count 放大）。
        ResolvedCatalystEffect effect = this.owner.resolveTaskEffect(recipe);
        long totalEnergy = AlloyFurnaceBigIntegerCrafting.totalEnergy(recipe, count, effect);
        if (totalEnergy > 0L) {
            IEnergyManager energy = this.owner.getEnergyManager();
            if (totalEnergy > energy.getEnergyStoredLong() || !energy.tryConsumeEnergy(totalEnergy)) {
                return false;
            }
        }

        long useless$bigintStarted = System.nanoTime();

        // 走到这里才算接收：只消费手里那一份原型（count-1 份由调用方的 BigInteger 账本扣除），
        // 产物进回网队列。回执上下文先建好，成功入队后再通知 CPU 侧「已受理」。
        AlloyFurnaceBigIntegerBatchContext cpuContext = cpuBinding == null
                ? null : buildCpuContext(pattern, count, totalEnergy, cpuBinding);
        PendingCraftingOutput pending = new PendingCraftingOutput(
                level.getGameTime(),
                produced,
                AlloyFurnaceBigIntegerCrafting.scaledOutputs(unitOutputs, count),
                cpuContext,
                cpuBinding == null ? null : cpuBinding.adapterId());
        for (KeyCounter counter : unitPrototype) {
            counter.clear();
        }
        enqueueCraftingOutput(pending);
        if (cpuContext != null) {
            notifyCpuBatchAdmitted(cpuContext, cpuBinding.adapterId());
        }
        TrinityDispatchDiagnostics.reportCraftingPatternWindow(level.getGameTime(), count, 1);
        // 报账给「每 tick 时间预算」：超预算时后续批次的窗口预算会被自动收窄
        AlloyFurnaceTickBudget.addWork(System.nanoTime() - useless$bigintStarted);
        this.owner.markChanged();
        return true;
    }

    /** 组装一次大数批次的 CPU 回执上下文（合成样板与万象样板共用）。 */
    private @NotNull AlloyFurnaceBigIntegerBatchContext buildCpuContext(
            IPatternDetails pattern,
            BigInteger count,
            long energyCharged,
            AlloyFurnaceBigIntegerCpuBinding binding) {
        return new AlloyFurnaceBigIntegerBatchContext(
                UUID.randomUUID(),
                binding.cpuToken(),
                AlloyFurnaceBigIntegerCrafting.machineIdentity(
                        "machine@" + this.owner.getBlockPos().asLong(),
                        this.owner.getLevel(),
                        this.owner.getBlockPos()),
                pattern,
                count,
                AlloyFurnaceBigIntegerCrafting.plannedOutputs(pattern, count),
                energyCharged);
    }

    // ==================== CPU 侧回执 ====================

    private static void notifyCpuBatchAdmitted(AlloyFurnaceBigIntegerBatchContext context,
                                               ResourceLocation adapterId) {
        AlloyFurnaceBigIntegerCpuAdapter adapter =
                AlloyFurnaceBigIntegerCpuAdapters.find(adapterId).orElse(null);
        if (adapter != null) {
            runCpuCallback(() -> adapter.onBatchAdmitted(context), adapterId, "onBatchAdmitted");
        }
    }

    /**
     * 产物全部回网后通知 CPU 侧。
     *
     * <p>实际产出直接取入队时算好的 BigInteger 精确值，<b>不</b>去累加 long 分段 —— 那会溢出。</p>
     */
    private static void notifyCpuBatchCompleted(PendingCraftingOutput pending) {
        AlloyFurnaceBigIntegerBatchContext context = pending.cpuContext;
        ResourceLocation adapterId = pending.cpuAdapterId;
        if (context == null || adapterId == null || pending.cpuNotified) {
            return;
        }
        // 取消流程可能已经发过 CANCELLED；一个批次只能有一个终局。
        pending.cpuNotified = true;
        AlloyFurnaceBigIntegerCpuAdapter adapter =
                AlloyFurnaceBigIntegerCpuAdapters.find(adapterId).orElse(null);
        if (adapter == null) {
            return;
        }
        List<AlloyFurnaceBigIntegerOutput> outputs = pending.bigIntegerOutputs;
        if (outputs != null && !outputs.isEmpty()) {
            runCpuCallback(() -> adapter.onBatchOutputs(context, outputs), adapterId, "onBatchOutputs");
        }
        runCpuCallback(() -> adapter.onBatchFinished(context, AlloyFurnaceBigIntegerBatchResult.SUCCESS),
                adapterId, "onBatchFinished");
    }

    /** 批次被取消时给 CPU 侧一个明确的终局信号，避免它一直等回执。 */
    private static void notifyCpuBatchCancelled(PendingCraftingOutput pending) {
        AlloyFurnaceBigIntegerBatchContext context = pending.cpuContext;
        ResourceLocation adapterId = pending.cpuAdapterId;
        if (context == null || adapterId == null || pending.cpuNotified) {
            return;
        }
        pending.cpuNotified = true;
        AlloyFurnaceBigIntegerCpuAdapter adapter =
                AlloyFurnaceBigIntegerCpuAdapters.find(adapterId).orElse(null);
        if (adapter != null) {
            runCpuCallback(() -> adapter.onBatchFinished(context, AlloyFurnaceBigIntegerBatchResult.CANCELLED),
                    adapterId, "onBatchFinished");
        }
    }

    /**
     * 回调必须隔离：第三方适配器抛异常绝不能影响机器的产物回网。
     * 记日志并吞掉，语义与 AE2 对供应器的容错一致。
     */
    private static void runCpuCallback(Runnable callback, ResourceLocation adapterId, String name) {
        try {
            callback.run();
        } catch (RuntimeException exception) {
            LOGGER.error("BigInteger crafting CPU adapter {} threw in {}", adapterId, name, exception);
        }
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
        // 逐键核对完之后只允许「before 之外的键」为空：铺料前的材料必须被完整解释掉。
        // 注意不能用 remaining.isEmpty()——remaining 是铺完一份后剩下的 (craftsLeft-1) 份材料，
        // 它天然非空，那样写会让倍率折叠永不生效（每次推送都退化成最多 64 次真实装配）。
        for (var entry : remaining.object2LongEntrySet()) {
            if (!before.containsKey(entry.getKey())) {
                return false;
            }
        }
        return true;
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

    /**
     * 把一份装配的产物流按倍率并入 BigInteger 账本。
     *
     * <p>倍率可以一路到 {@code long} 上限，单键总量会超过 {@code long}：这里只用
     * {@link BigInteger} 做乘法与累加，最后由 {@link CraftingAeAmountAccumulator#segments()}
     * 切成若干 long 条目。绝不能像旧实现那样在 {@code Math.multiplyExact} 溢出时丢弃条目——
     * 少记的产物就是 AE2 账本上对不上的缺口。（手法与 Data Energistics 内部
     * {@code TrinityItemAmount.multiply} 的分段放大一致。）</p>
     */
    private static void accumulateScaled(CraftingAeAmountAccumulator target,
                                        List<GenericStack> unitOutputs,
                                        long multiplier) {
        accumulateScaled(target, unitOutputs, BigInteger.valueOf(Math.max(1L, multiplier)));
    }

    /** BigInteger 倍率版本：数据能源的原生 bigint 批次一次可能交付超过 {@code long} 的次数。 */
    private static void accumulateScaled(CraftingAeAmountAccumulator target,
                                        List<GenericStack> unitOutputs,
                                        BigInteger multiplier) {
        if (multiplier.signum() <= 0) {
            throw new IllegalArgumentException("Crafting batch multiplier must be positive");
        }
        for (GenericStack unit : unitOutputs) {
            if (unit.amount() <= 0L) {
                continue;
            }
            target.add(unit.what(), BigInteger.valueOf(unit.amount()).multiply(multiplier));
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
     * 把已装配完成、等待回网的产物写入 AE。
     *
     * <p><b>BigInteger 优先</b>：整条可回网队列先按键聚合成一本账，每个键只投递一次，且只在真正
     * 插入的那一刻才把余额切成 {@code long} 分段（AE 存储 API 只有 long，这是硬边界）。
     * 投递成功后把实际投递量按队列 <b>FIFO</b> 归因回各条目，条目余额清零才移除并发终局回调。</p>
     *
     * <p>归因是精确的：令可回网条目为 {@code e₁..eₙ}，键 k 的余额为 {@code Eᵢ}、聚合为
     * {@code T=ΣEᵢ}、本轮投递为 {@code D∈[0,T]}；按 FIFO 依次扣 {@code min(剩余, Eᵢ)}，
     * 因 {@code D ≤ ΣEᵢ} 故累计扣减恰为 {@code D}，单条扣减又不会超过其实际余额 ⇒ 不丢不重。</p>
     *
     * @param force true 时忽略「必须晚一个 tick」的保护，且<b>不受</b>每 tick 预算与单键分段上限约束。
     *              只用于取消/拆除：此时 CPU 已放弃这批产物（落进通用存储也不会被错认），
     *              但方块实体与 NBT 随后就消失，产物必须尽量一次全部写回，否则就丢了。
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

        // 1) 收集本轮可刷条目，保持队列 FIFO 顺序（归因依赖这个顺序）。
        List<PendingCraftingOutput> flushable = new ArrayList<>();
        for (PendingCraftingOutput pending : this.queuedCraftingOutputs) {
            if (force || pending.queuedTick < now) {
                flushable.add(pending);
            }
        }
        if (flushable.isEmpty()) {
            return;
        }

        // 2) 跨条目按键聚合：整条队列里同一个键只投递一次。
        CraftingAeAmountAccumulator total = new CraftingAeAmountAccumulator();
        for (PendingCraftingOutput pending : flushable) {
            total.addAll(pending.ledger);
        }
        if (total.isEmpty()) {
            return;
        }

        // 3) 解析一次网络写入目标，整趟刷写复用。
        //    原来每写一个分段都要重解析（多方块侧 2 次方块实体查询 + 2 次分配），
        //    而可持续吞吐直接由「每 tick 能插多少次」决定（一 tick 数千次）⇒ 不缓存就直接吃吞吐。
        //    不可达时直接返回、不触碰任何状态（旧实现会逐段空转并标脏）。
        CraftingAeOutputTarget target = this.owner.resolveAeOutputTarget();
        if (target == null) {
            return;
        }

        // 4) 逐键投递。offer 取 min(余额, Long.MAX_VALUE)：存储 API 单次只有 long。
        long flushStarted = System.nanoTime();
        // 预算按全局系数动态收窄（下限 250µs）：回网预算是每台机器的，
        // 写死会让 N 台机器各自吃满基准值（10 台就是 40ms/tick）。
        long flushBudgetNanos = AlloyFurnaceTickBudget.scaledFlushBudget(flushBudgetNanos());
        boolean deliveredAny = false;
        int emptyKeyProbes = 0;
        BigInteger deliveredTotal = BigInteger.ZERO;
        // 插入耗时先本地累计，整趟结束再上报一次：addWork 是 synchronized，
        // 而一 tick 可能插数千次，逐段加锁本身就是可观开销。
        long insertWorkNanos = 0L;
        for (var entry : total.snapshotEntries()) {
            if (!force && insertWorkNanos >= flushBudgetNanos) {
                break; // 预算已用尽：本 tick 到此为止（首个键因累计为 0 必定会被尝试）
            }
            AEKey key = entry.getKey();
            BigInteger remaining = entry.getValue();
            // 不设段数上限：**只由时间预算决定何时停**。
            // 直接拿累计插入耗时当预算信号，这样每段只需一对 nanoTime（原来还要额外的预算检查）。
            // 循环条件在插入前判定，所以第一个分段总会尝试（预算为 0 时也进得去）。
            while (remaining.signum() > 0 && (force || insertWorkNanos < flushBudgetNanos)) {
                BigInteger offer = remaining.min(MAX_OUTPUT_CHUNK);
                long offerLong = offer.longValueExact(); // ≤ Long.MAX_VALUE，安全
                long insertStarted = System.nanoTime();
                long inserted = clampInserted(target.insert(key, offerLong), offerLong);
                insertWorkNanos += System.nanoTime() - insertStarted;
                if (inserted <= 0L) {
                    break; // 本键被拒收：本轮不再尝试它
                }
                deliveredAny = true;
                emptyKeyProbes = 0;
                remaining = remaining.subtract(BigInteger.valueOf(inserted));
                if (inserted < offerLong) {
                    break; // 只吸收了部分 ⇒ 存储已饱和，继续喂只会空转
                }
            }

            // 4) 把本键实际投递量按 FIFO 归因回各条目（队首条目先被清空）。
            BigInteger delivered = entry.getValue().subtract(remaining);
            if (delivered.signum() > 0) {
                attributeDeliveredOutputs(flushable, key, delivered);
                deliveredTotal = deliveredTotal.add(delivered);
            } else if (++emptyKeyProbes >= MAX_EMPTY_KEY_PROBES) {
                break; // 连续多个键都拒收 ⇒ 网络整体饱和，提前收手
            }

            if (!force && insertWorkNanos >= flushBudgetNanos) {
                break; // 预算已用尽：跳出键循环
            }
        }

        // 整趟的插入耗时一次性上报（见上面 insertWorkNanos 的说明）。
        AlloyFurnaceTickBudget.addWork(insertWorkNanos);

        // 更新实测单段插入成本：单批分段预算由它前馈算出。
        // 门槛见 MIN_INSERT_SAMPLE_CHUNKS：样本太小时固定开销会主导，会把预算砸下去。
        long deliveredChunksNow = backlogChunks(deliveredTotal);
        if (deliveredChunksNow >= MIN_INSERT_SAMPLE_CHUNKS && insertWorkNanos > 0L) {
            double sample = (double) insertWorkNanos / (double) deliveredChunksNow;
            double previous = this.measuredInsertNanosPerChunk;
            double updated = this.insertCostMeasured
                    // 首个有效样本直接采纳：否则要从初始估计慢慢爬，白等几十批。
                    ? previous * (1.0D - INSERT_COST_SMOOTHING) + sample * INSERT_COST_SMOOTHING
                    : sample;
            // 非对称：成本（= 耗时）上升受限，等价于「降档每次最多 5%」；
            // 下降不限速，所以批次放大是即时的。
            updated = Math.min(updated, previous * MAX_INSERT_COST_RISE_PER_SAMPLE);
            this.measuredInsertNanosPerChunk = Math.max(MIN_INSERT_NANOS_PER_CHUNK, updated);
            this.insertCostMeasured = true;
        }

        // 5) 清空条目：产物全部回网后发一次终局回调，并从队列移除。
        //    没有投递任何东西时这里不会命中，也不会 markChanged（旧实现会空转并标脏）。
        boolean changed = deliveredAny;
        if (deliveredTotal.signum() > 0) {
            // 同步积压总量（精确减）：它是 AIMD 控制器的度量来源。
            addPendingOutputAmount(deliveredTotal.negate());
        }
        logOutputReturnDiagnostics(now, flushStarted, flushBudgetNanos, insertWorkNanos, deliveredTotal);
        var iterator = this.queuedCraftingOutputs.iterator();
        while (iterator.hasNext()) {
            PendingCraftingOutput pending = iterator.next();
            if (pending.isEmpty()) {
                iterator.remove();
                // 产物全部回网：给 CPU 侧发实际产出与终局通知（没有绑定时是空操作）。
                notifyCpuBatchCompleted(pending);
                changed = true;
            }
        }
        if (changed) {
            this.owner.markChanged();
        }
    }

    /**
     * 把某个键本轮实际投递的量按队列 FIFO 记回各条目。
     *
     * <p>调用方保证 {@code delivered ≤ Σᵢ entryᵢ.ledger.amount(key)}（因为 delivered 由聚合余额
     * 逐次扣减得到）。逐条取 {@code min(剩余投递量, 该条余额)} 扣减，累计恰好等于 {@code delivered}
     * —— 既不会少记（丢物品），也不会多记（凭空扣账）。</p>
     */
    private static void attributeDeliveredOutputs(List<PendingCraftingOutput> entries,
                                                 AEKey key, BigInteger delivered) {
        BigInteger left = delivered;
        for (PendingCraftingOutput pending : entries) {
            if (left.signum() <= 0) {
                break;
            }
            left = left.subtract(pending.ledger.consume(key, left));
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

    /**
     * 一次自执行（合成样板装配 / 万象样板折叠）的产出，等待在安全时机注入 AE 网络。
     *
     * <p><b>BigInteger 优先</b>：产物以按键 {@link CraftingAeAmountAccumulator 余额账本} 保存，
     * 不预先物化 long 分段。旧实现把产物按 {@code Long.MAX_VALUE} 切成 {@code List<GenericStack>}
     * 再逐段插入，最坏情况（队列深度 = 线程数、每条约千段）会达到每 tick 千万次插入调用；
     * 现在只在真正插入的那一刻才切段，且同一个键在整条队列里只投递一次。</p>
     *
     * <p>大数批次额外带两样东西：{@code bigIntegerOutputs} 是本批产出的 BigInteger <b>精确</b>值
     * （回调专用 —— long 分段相加会溢出，且它与投递结果无关，commit 时就算好了）；
     * {@code cpuContext} + {@code cpuAdapterId} 用来在产物全部回网后驱动
     * {@code onBatchOutputs} / {@code onBatchFinished}。</p>
     *
     * <p>CPU 回执绑定<b>不随 NBT 持久化</b>：产物队列本身照常存盘（不会丢物品），但重载后不再补发
     * 回调。这是刻意的 —— 回调只是本会话内的便利信号，产物最终仍会经 ME 网络正常入账，
     * 调用方以网络实际入账为准即可。</p>
     */
    static final class PendingCraftingOutput {
        final long queuedTick;
        /** 剩余待回网的按键 BigInteger 余额。回网刷新时原地扣减，扣空即条目完成。 */
        final CraftingAeAmountAccumulator ledger;
        /** 本批的实际产出（BigInteger 精确值）；非大数批次为 {@code null}。 */
        @Nullable
        final List<AlloyFurnaceBigIntegerOutput> bigIntegerOutputs;
        /** CPU 回执上下文；没有绑定时为 {@code null}。 */
        @Nullable
        final AlloyFurnaceBigIntegerBatchContext cpuContext;
        /** 已注册的适配器 id，与 {@link #cpuContext} 同时存在。 */
        @Nullable
        final ResourceLocation cpuAdapterId;
        /**
         * 是否已经给 CPU 侧发过终局通知。
         *
         * <p>必须有这个位：取消流程会先发 {@code CANCELLED}，紧接着
         * {@link #flushQueuedCraftingOutputs(boolean)} 以 {@code force=true} 强制回网，
         * 若这一条刚好被刷空就会再发一次 {@code SUCCESS} —— 同一个批次出现两个互相矛盾的终局。
         * 一个批次只能有一个终局。</p>
         */
        boolean cpuNotified;

        PendingCraftingOutput(long queuedTick, CraftingAeAmountAccumulator ledger) {
            this(queuedTick, ledger, null, null, null);
        }

        PendingCraftingOutput(long queuedTick, CraftingAeAmountAccumulator ledger,
                              @Nullable List<AlloyFurnaceBigIntegerOutput> bigIntegerOutputs) {
            this(queuedTick, ledger, bigIntegerOutputs, null, null);
        }

        PendingCraftingOutput(long queuedTick, CraftingAeAmountAccumulator ledger,
                              @Nullable List<AlloyFurnaceBigIntegerOutput> bigIntegerOutputs,
                              @Nullable AlloyFurnaceBigIntegerBatchContext cpuContext,
                              @Nullable ResourceLocation cpuAdapterId) {
            this.queuedTick = queuedTick;
            // 拷贝隔离：调用方的 produced 账本随后就被丢弃，但别让它与队列条目共享可变状态。
            this.ledger = ledger.copy();
            this.bigIntegerOutputs = bigIntegerOutputs;
            this.cpuContext = cpuContext;
            this.cpuAdapterId = cpuAdapterId;
        }

        /** 本条目是否已全部回网（账本扣空）。 */
        boolean isEmpty() {
            return this.ledger.isEmpty();
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
