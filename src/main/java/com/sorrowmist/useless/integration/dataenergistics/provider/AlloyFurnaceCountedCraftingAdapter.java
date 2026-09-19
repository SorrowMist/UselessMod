package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceBigIntegerCrafting;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceTickBudget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Bridges one alloy-furnace AE provider to Data Energistics counted Trinity dispatch.
 *
 * <p><b>两条路径并存</b>：</p>
 * <ul>
 *   <li><b>长版 counted</b>（{@link #prepareBatch} / {@link #prepareBatchForTarget}）：一次物理提交最多
 *       {@code Long.MAX / 每次合成消耗量} 个合成（= 一个 AE2 long 物理窗口），由本机在虚拟 3×3 上折叠。</li>
 *   <li><b>原生 bigint</b>（子类 {@link AlloyFurnaceBigIntegerCraftingAdapter} 的
 *       {@code prepareBigIntegerBatch}）：DE 3.3.0 的 exact 派发。
 *       它只在「本 target 已被异步提案排他预留」时触发，一次能交付<b>超过 long</b> 的批次；
 *       关键是这里拿到的 prototype 是<b>单次合成的原型</b>，count 只通过 {@code exactCount()} 传递，
 *       剩余 count-1 份材料由 DE 自己的 BigInteger 账本扣除，我们只消费手里那一份原型。</li>
 * </ul>
 *
 * <p><b>哪些样板发布 machine identity</b>：DE 的 exact 分支要求容量快照带
 * provider 无关的 machine identity（排他预留的前提），而它一旦生效就会<b>完全接管</b>该 provider
 * 的派发（不会再回落长版路径）。所以只给真正实现了 bigint 语义的样板发 machine target：</p>
 * <ul>
 *   <li><b>AE2 合成样板</b>：本机一次装配即可折叠任意份数，始终发布 machine target；</li>
 *   <li><b>万象样板</b>：只有宿主声明了 {@link CraftingTaskContext#supportsBigIntegerRecipeBatches()}
 *       （目前只有多方块核心）才发布 —— 它把配方折叠成 BigInteger 批次并按 count 收能量；</li>
 *   <li>其余样板（含单方块的万象样板）继续用 {@code route(...)} 目标走长版路径。</li>
 * </ul>
 *
 * <p><b>安全阀</b>：因为接管后不回落，「本机此刻吃不下」必须体现为<b>容量为零</b>，
 * 而不是先发布带 machine identity 的容量再在提交时拒绝 —— 后者会把该 provider 卡死在 exact 分支。
 * 见 {@link #maximumRecipeBatchCount} 里的可用性/能量闸。</p>
 *
 * <h2>为什么 bigint 部分在子类里</h2>
 *
 * <p>{@code BigIntegerCraftingProviderAdapter} 是数据能源 <b>3.3.0 才引入</b>的接口，3.2.2 及更早没有。
 * 所以本类刻意<b>只依赖两个版本都存在的 API</b>（注意它实现的 {@link CountedCraftingProviderAdapter}
 * 两版都有）—— 这样旧版数据能源下这个类仍能正常加载并走长版 counted 路径。bigint 专属的
 * {@code prepareBigIntegerBatch} / {@code captureCapacityFast} 放在子类
 * {@link AlloyFurnaceBigIntegerCraftingAdapter}，且子类<b>只在能力探针通过时才会被加载</b>：
 * 引用它的唯一地方是 {@link AlloyFurnaceBigIntegerAdapterFactory}，而那个类只在探针为真的分支里被触达。
 * 结果是：旧版能启动（功能降级），数据能源升级到 3.3.0 后重启即自动恢复 bigint，
 * 无需改代码或配置。</p>
 */
class AlloyFurnaceCountedCraftingAdapter implements CountedCraftingProviderAdapter {
    private final ICraftingProvider provider;
    private final BooleanSupplier online;
    /** provider 局部稳定 route 身份。 */
    private final String routeIdentity;
    /** provider 无关的物理机器身份（维度@坐标）。 */
    private final String machineIdentity;
    private final Supplier<@Nullable CraftingTaskContext> taskContext;
    private final ToIntFunction<Boolean> remainingThreads;
    private final IntSupplier totalThreads;
    /** 机器侧的大数推送入口：单位原型 + BigInteger 次数。 */
    private final BigIntegerPush bigIntegerPush;

    /** Pushes one homogeneous bigint batch into the machine. */
    @FunctionalInterface
    interface BigIntegerPush {
        boolean push(@NotNull IPatternDetails pattern, @NotNull BigInteger count, KeyCounter @NotNull [] unitPrototype);
    }

    AlloyFurnaceCountedCraftingAdapter(
            @NotNull ICraftingProvider provider,
            @NotNull BooleanSupplier online,
            @NotNull String routeIdentity,
            @NotNull String machineIdentity,
            @NotNull Supplier<@Nullable CraftingTaskContext> taskContext,
            @NotNull ToIntFunction<Boolean> remainingThreads,
            @NotNull IntSupplier totalThreads,
            @NotNull BigIntegerPush bigIntegerPush) {
        this.provider = provider;
        this.online = online;
        this.routeIdentity = routeIdentity;
        this.machineIdentity = machineIdentity;
        this.taskContext = taskContext;
        this.remainingThreads = remainingThreads;
        this.totalThreads = totalThreads;
        this.bigIntegerPush = bigIntegerPush;
    }

    /**
     * Creates the live adapter used by one standalone advanced alloy furnace.
     *
     * <p>按能力探针二选一：3.3.0 的 bigint API 存在 ⇒ 子类（含 exact 派发）；
     * 不存在 ⇒ 本类（只走长版 counted）。子类引用只出现在探针通过的那条分支里，
     * 旧版数据能源下那一行永远不会执行，于是子类也永远不会被加载。</p>
     */
    static AlloyFurnaceCountedCraftingAdapter forAdvancedAlloyFurnace(
            @NotNull AdvancedAlloyFurnaceBlockEntity provider,
            @NotNull PatternProviderIdentity identity) {
        String digest = identity.digest();
        BooleanSupplier online = () -> isAdvancedAlloyFurnaceOnline(provider);
        String machineIdentity =
                AlloyFurnaceBigIntegerCrafting.machineIdentity(digest, provider.getLevel(), provider.getBlockPos());
        Supplier<@Nullable CraftingTaskContext> taskContext = () -> provider;
        if (DataEnergisticsBigIntSupport.AVAILABLE) {
            // 子类引用只出现在 holder 里：探针为假时这一行不执行 ⇒ holder 与子类都不会被加载。
            return (AlloyFurnaceCountedCraftingAdapter) AlloyFurnaceBigIntegerAdapterFactory.create(
                    provider,
                    online,
                    digest,
                    machineIdentity,
                    taskContext,
                    provider::getRemainingAETaskCount,
                    provider::getMaxAETaskCount,
                    provider::pushBigIntegerCraftingPattern);
        }
        return new AlloyFurnaceCountedCraftingAdapter(
                provider,
                online,
                digest,
                machineIdentity,
                taskContext,
                provider::getRemainingAETaskCount,
                provider::getMaxAETaskCount,
                provider::pushBigIntegerCraftingPattern);
    }

    /** Creates the live adapter used by one ME pattern assembly and its linked multiblock controller. */
    static AlloyFurnaceCountedCraftingAdapter forMePatternAssembly(
            @NotNull MePatternAssemblyBlockEntity provider,
            @NotNull PatternProviderIdentity identity) {
        String digest = identity.digest();
        BooleanSupplier online = () -> isMePatternAssemblyOnline(provider);
        String machineIdentity =
                AlloyFurnaceBigIntegerCrafting.machineIdentity(digest, provider.getLevel(), provider.getBlockPos());
        Supplier<@Nullable CraftingTaskContext> taskContext = provider::getController;
        ToIntFunction<Boolean> remainingThreads = craftingPattern -> {
            MultiblockAlloyFurnaceCoreBlockEntity controller = provider.getController();
            return controller == null ? 0 : controller.getRemainingAETaskCount(craftingPattern);
        };
        IntSupplier totalThreads = () -> {
            MultiblockAlloyFurnaceCoreBlockEntity controller = provider.getController();
            return controller == null ? 0 : controller.getMaxAETaskCount();
        };
        if (DataEnergisticsBigIntSupport.AVAILABLE) {
            // 同上：子类引用只出现在 holder 里。
            return (AlloyFurnaceCountedCraftingAdapter) AlloyFurnaceBigIntegerAdapterFactory.create(
                    provider,
                    online,
                    digest,
                    machineIdentity,
                    taskContext,
                    remainingThreads,
                    totalThreads,
                    provider::pushBigIntegerCraftingPattern);
        }
        return new AlloyFurnaceCountedCraftingAdapter(
                provider,
                online,
                digest,
                machineIdentity,
                taskContext,
                remainingThreads,
                totalThreads,
                provider::pushBigIntegerCraftingPattern);
    }

    // ==================== 长版 counted 路径 ====================

    @Override
    public @Nullable CountedCraftingAdmission prepareBatch(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        return prepareAdmission(patternDetails, prototype, requestedCount);
    }

    /**
     * 长版 counted 的容量上报（3.2.2 与 3.3.0 都存在的那个入口）。
     *
     * <p><b>必须实现它</b>，否则旧版数据能源会落到接口的 default 实现 —— 那个 default 返回
     * {@code CountedCraftingCapacity.aggregateUnknown()}，即「未知容量 + 保守单次派发」，
     * 功能能用但吞吐极差。3.3.0 的调度器改走子类的 {@code captureCapacityFast}，
     * 两者共用 {@link #capacityEntry}，口径不会漂移。</p>
     *
     * <p>3.3.0 里这个方法已标记 {@code @Deprecated(forRemoval)}，这里是<b>刻意保留</b>的：
     * 它就是 3.2.2 唯一可用的容量入口，删掉就等于放弃旧版兼容。等数据能源真的移除它时，
     * 只需要把最短支持版本抬到 3.3.0 并删掉本方法。</p>
     */
    @Override
    @SuppressWarnings("removal")
    public @NotNull List<@NotNull CountedCraftingCapacity> captureCapacity(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        CountedCraftingCapacity single = capacityEntry(patternDetails, prototype, requestedCount);
        return single == null ? List.of() : List.of(single);
    }

    /** 单条容量上报；容量为 0 时返回 {@code null}（两个版本的容量入口共用）。 */
    final @Nullable CountedCraftingCapacity capacityEntry(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        AvailableCapacity capacity = availableCapacity(patternDetails, prototype, requestedCount);
        if (capacity.logicalCrafts() == 0L) {
            return null;
        }
        return new CountedCraftingCapacity(
                targetFor(patternDetails),
                CountedCraftingRoutingMode.TARGETED,
                OptionalLong.of(capacity.logicalCrafts()),
                OptionalLong.of(capacity.maximumSingleBatch()));
    }

    @Override
    public @Nullable CountedCraftingAdmission prepareBatchForTarget(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount,
            @NotNull CountedCraftingTarget requestedTarget) {
        return targetFor(patternDetails).equals(requestedTarget)
                ? prepareAdmission(patternDetails, prototype, requestedCount)
                : null;
    }

    /**
     * bigint 批次的可接受量。
     *
     * <p>合成样板：本机能一次装配折叠任意份数，上限由产物分段预算反推。</p>
     * <p>万象样板：只有宿主声明了 bigint 能力（多方块）才接，上限交给
     * {@link AlloyFurnaceBigIntegerCrafting#maximumCount} —— 它把配方可用性、材料窗口、
     * 产物分段与能量四道闸一起算完。其它样板返回 0，让 DE 继续走长版 counted 路径。</p>
     *
     * <p>「产物分段预算」由宿主的 AIMD 控制器给出（见 {@code CraftingTaskContext#outputSegmentBudget}）：
     * 它把单批规模收敛到「一批大约一两个 tick 交付完」，所以容量永不因积压归零，
     * 调度侧不会停一拍再重启。</p>
     */
    @NotNull BigInteger availableBigIntegerCount(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            @NotNull BigInteger requestedCount) {
        if (!online.getAsBoolean()) {
            return BigInteger.ZERO;
        }
        IPatternDetails original = SmartDoublingPatterns.unwrap(patternDetails);
        if (!provider.getAvailablePatterns().contains(original)) {
            return BigInteger.ZERO;
        }
        if (prototype == null || prototype.length == 0) {
            return BigInteger.ZERO;
        }
        int threads = machineThreads();
        long segmentBudget = segmentBudget();
        if (original instanceof IMolecularAssemblerSupportedPattern) {
            return requestedCount.min(AlloyFurnaceBigIntegerCrafting.maximumCraftingPatternCount(
                    original, prototype, threads, segmentBudget));
        }
        if (original instanceof OmniversalPatternDetails omniversal) {
            CraftingTaskContext context = this.taskContext.get();
            if (!AlloyFurnaceBigIntegerCrafting.supports(context)) {
                return BigInteger.ZERO;
            }
            return requestedCount.min(AlloyFurnaceBigIntegerCrafting.maximumCount(
                    context, omniversal, prototype, threads, segmentBudget));
        }
        return BigInteger.ZERO;
    }

    /** 宿主的单批分段预算；没有宿主时退回 1（最保守，只影响容量不会崩）。 */
    private long segmentBudget() {
        CraftingTaskContext context = this.taskContext.get();
        return context == null ? 1L : Math.max(1L, context.outputSegmentBudget());
    }

    /**
     * 本机当前线程数：多方块跟随线圈线程（{@code coil_tier_N_threads}），单方块跟随炉子等级线程
     * （{@code furnace_tier_N_threads}）。
     *
     * <p>它就是「一台机器一 tick 能接几份窗口」的并行度，bigint 批次的窗口预算直接用它 ——
     * 不设上限：线程数配到多少就吃多少，代价（产物分段规模、内存、NBT 体积）由配置者承担。</p>
     */
    private int machineThreads() {
        return Math.max(1, this.totalThreads.getAsInt());
    }

    /** exact 批次的实际提交：把单位原型与 BigInteger 次数交给机器。 */
    boolean dispatchBigInteger(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] unitPrototype, @NotNull BigInteger count) {
        if (availableBigIntegerCount(patternDetails, unitPrototype, count).compareTo(count) < 0) {
            return false;
        }
        return this.bigIntegerPush.push(patternDetails, count, unitPrototype);
    }

    // ==================== 共用：目标与容量 ====================

    /**
     * 合成样板、以及声明了 bigint 能力的万象样板，发布带 machine identity 的 TARGETED 目标
     * （exact 派发的前提）；其余样板只发 route 目标 —— 这样 DE 不会对它们启用 exact 分支
     * （那会跳过长版路径，而它们没有 bigint 语义）。
     */
    @NotNull CountedCraftingTarget targetFor(@NotNull IPatternDetails patternDetails) {
        return supportsExactBatch(patternDetails)
                ? CountedCraftingTarget.machine(this.routeIdentity, this.machineIdentity)
                : CountedCraftingTarget.route(this.routeIdentity);
    }

    /**
     * 该样板是否具备 bigint（exact）语义。
     *
     * <p>万象样板只有在宿主声明了 {@link CraftingTaskContext#supportsBigIntegerRecipeBatches()}
     * 时才算 —— 单方块高级合金炉没有实现折叠语义，给它发布 machine identity 会让 DE 切进
     * exact 分支且永不回落，而那边根本没有 bigint 接收入口。</p>
     */
    private boolean supportsExactBatch(@NotNull IPatternDetails patternDetails) {
        IPatternDetails original = SmartDoublingPatterns.unwrap(patternDetails);
        if (original instanceof IMolecularAssemblerSupportedPattern) {
            return true;
        }
        return original instanceof OmniversalPatternDetails
                && AlloyFurnaceBigIntegerCrafting.supports(this.taskContext.get());
    }

    /** Returns the largest safe count for one physical scaled-pattern submission. */
    static long maximumBatchCount(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        validateRequestedCount(requestedCount);
        SmartDoublingPatterns.Resolved execution = SmartDoublingPatterns.resolve(patternDetails);
        long maximumCount = SmartDoublingPatterns.maximumSafeMultiplier(execution.pattern())
                / execution.operationsPerPush();
        for (KeyCounter counter : prototype) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("Crafting input amounts must not be negative");
                }
                if (amount > 0L) {
                    maximumCount = Math.min(maximumCount, Long.MAX_VALUE / amount);
                }
            }
        }
        return Math.min(requestedCount, maximumCount);
    }

    /** Creates a deep, exact scaled input snapshot without changing the caller-owned prototype. */
    static KeyCounter[] scalePrototype(KeyCounter @NotNull [] prototype, long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("Counted crafting batch size must be positive");
        }
        KeyCounter[] scaled = new KeyCounter[prototype.length];
        for (int index = 0; index < prototype.length; index++) {
            KeyCounter source = prototype[index];
            KeyCounter targetCounter = new KeyCounter();
            for (var entry : source) {
                long amount = entry.getLongValue();
                if (amount < 0L) {
                    throw new IllegalArgumentException("Crafting input amounts must not be negative");
                }
                targetCounter.add(entry.getKey(), Math.multiplyExact(amount, count));
            }
            scaled[index] = targetCounter;
        }
        return scaled;
    }

    private @Nullable CountedCraftingAdmission prepareAdmission(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        long acceptedCount = availableCapacity(patternDetails, prototype, requestedCount).logicalCrafts();
        return acceptedCount == 0L
                ? null
                : new AlloyFurnaceCountedCraftingAdmission(this, patternDetails, prototype, acceptedCount);
    }

    AvailableCapacity availableCapacity(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long requestedCount) {
        validateRequestedCount(requestedCount);
        if (!online.getAsBoolean()) {
            return AvailableCapacity.EMPTY;
        }
        IPatternDetails original = SmartDoublingPatterns.unwrap(patternDetails);
        if (!provider.getAvailablePatterns().contains(original)) {
            return AvailableCapacity.EMPTY;
        }
        long arithmeticMaximum = maximumBatchCount(
                patternDetails,
                prototype,
                requestedCount);
        CapacityLimits limits = maximumRecipeBatchCount(patternDetails, prototype, arithmeticMaximum);
        if (limits.logicalMaximum() == 0L) {
            return AvailableCapacity.EMPTY;
        }

        IPatternDetails executionPattern = SmartDoublingPatterns.unwrap(patternDetails);
        boolean craftingPattern = executionPattern instanceof IMolecularAssemblerSupportedPattern;
        int remaining = Math.max(0, this.remainingThreads.applyAsInt(craftingPattern));
        int totalThreads = Math.max(0, this.totalThreads.getAsInt());
        int availableThreads = Math.min(remaining, totalThreads);
        if (craftingPattern) {
            // 合成样板/大数批次的槽位来自「回网队列」。积压时不再把槽位打到 0
            //（那等于二元拒绝，调度侧会停一拍再重启），而是保留 1 条 ——
            // 单批规模由宿主的 AIMD 分段预算控制，它会把批次收敛到「一两个 tick 交付完」，
            // 所以保留 1 条不会真的压垮队列。
            availableThreads = Math.max(1, availableThreads);
            CraftingTaskContext context = this.taskContext.get();
            long perThreadCapacity = context == null
                    ? 1L : Math.max(1L, context.getCraftingPatternCapacity());
            long aggregateCapacity = saturatingMultiply(perThreadCapacity, availableThreads);
            long logicalCrafts = Math.min(limits.logicalMaximum(), aggregateCapacity);
            long maximumSingleBatch = logicalCrafts;
            return logicalCrafts == 0L || maximumSingleBatch == 0L
                    ? AvailableCapacity.EMPTY
                    : new AvailableCapacity(logicalCrafts, maximumSingleBatch);
        }
        if (limits.singleThreadMaximum() == 0L) {
            return AvailableCapacity.EMPTY;
        }
        long aggregateMaximum = saturatingMultiply(limits.singleThreadMaximum(), availableThreads);
        long logicalCrafts = Math.min(limits.logicalMaximum(), aggregateMaximum);
        long maximumSingleBatch = Math.min(limits.logicalMaximum(), limits.singleThreadMaximum());
        return logicalCrafts == 0L || maximumSingleBatch == 0L
                ? AvailableCapacity.EMPTY
                : new AvailableCapacity(logicalCrafts, maximumSingleBatch);
    }

    private CapacityLimits maximumRecipeBatchCount(
            IPatternDetails patternDetails,
            KeyCounter[] prototype,
            long arithmeticMaximum) {
        CraftingTaskContext context = this.taskContext.get();
        if (arithmeticMaximum == 0L) {
            return CapacityLimits.EMPTY;
        }
        SmartDoublingPatterns.Resolved execution = SmartDoublingPatterns.resolve(patternDetails);
        if (execution.pattern() instanceof IMolecularAssemblerSupportedPattern) {
            // 合成样板由本机在虚拟 3×3 上自执行，一次推送就能折叠整批，没有配方侧并行概念。
            return new CapacityLimits(arithmeticMaximum, arithmeticMaximum);
        }
        if (context == null) {
            return new CapacityLimits(arithmeticMaximum, arithmeticMaximum);
        }
        AdvancedAlloyFurnaceRecipe recipe = context.resolveTaskRecipe(
                patternDetails,
                List.of(),
                List.of(),
                compactInputs(prototype),
                execution.operationsPerPush());
        if (recipe == null) {
            return CapacityLimits.EMPTY;
        }

        if (execution.pattern() instanceof OmniversalPatternDetails
                && AlloyFurnaceBigIntegerCrafting.supports(context)) {
            // 安全阀：本机一旦吃不下（档次/模具/能量任一不满足），必须发布「零容量」而不是带
            // machine identity 的容量 —— DE 的 exact 分支一旦接管就不会再回落长版路径，
            // 先发布容量再在提交时拒绝会把该 provider 卡死。
            if (!context.isTaskRecipeAvailable(recipe)) {
                return CapacityLimits.EMPTY;
            }
            BigInteger energyCap = AlloyFurnaceBigIntegerCrafting.maximumCountForEnergy(context, recipe);
            if (energyCap != null && energyCap.signum() <= 0) {
                return CapacityLimits.EMPTY;
            }
        }

        long manualOperations = SmartDoublingPatterns.manualOperationsPerPattern(
                recipe, execution.pattern());
        if (manualOperations == 0L) {
            return CapacityLimits.EMPTY;
        }
        BigInteger wrapperOperations = BigInteger.valueOf(execution.operationsPerPush());
        BigInteger operations = wrapperOperations
                .multiply(BigInteger.valueOf(manualOperations));
        long maximum;
        if (usesRecipeOutputs(execution.pattern())) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
            recipe.outputs().forEach(output -> mergeOutput(outputs, GenericStack.fromItemStack(output)));
            recipe.outputFluids().forEach(output -> mergeOutput(outputs, GenericStack.fromFluidStack(output)));
            recipe.keyOutputs().forEach(output -> mergeOutput(outputs, output));
            maximum = limitByOutputs(arithmeticMaximum, outputs, operations);
        } else {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> declaredOutputs = new Object2ObjectLinkedOpenHashMap<>();
            execution.pattern().getOutputs().forEach(output -> mergeOutput(declaredOutputs, output));
            maximum = limitByOutputs(arithmeticMaximum, declaredOutputs, wrapperOperations);
            if (maximum == 0L) {
                return CapacityLimits.EMPTY;
            }

            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> hiddenOutputs = new Object2ObjectLinkedOpenHashMap<>();
            for (GenericStack output : recipe.keyOutputs()) {
                boolean declared = execution.pattern().getOutputs().stream()
                        .anyMatch(patternOutput -> output.what().equals(patternOutput.what()));
                if (!declared) {
                    mergeOutput(hiddenOutputs, output);
                }
            }
            maximum = limitByOutputs(maximum, hiddenOutputs, operations);
        }

        if (maximum == 0L) {
            return CapacityLimits.EMPTY;
        }
        long parallel = Math.max(1L, context.getTaskParallel(recipe, context.resolveTaskEffect(recipe)));
        long singleThreadMaximum = divideByBigInteger(parallel, operations);
        return singleThreadMaximum == 0L
                ? CapacityLimits.EMPTY
                : new CapacityLimits(maximum, singleThreadMaximum);
    }

    private static long limitByOutputs(
            long currentMaximum,
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs,
            BigInteger operationMultiplier) {
        BigInteger maximumLong = BigInteger.valueOf(Long.MAX_VALUE);
        long maximum = currentMaximum;
        for (BigInteger amount : outputs.values()) {
            BigInteger perLogicalCraft = amount.multiply(operationMultiplier);
            if (perLogicalCraft.compareTo(maximumLong) > 0) {
                return 0L;
            }
            maximum = Math.min(maximum, maximumLong.divide(perLogicalCraft).longValueExact());
        }
        return maximum;
    }

    private static boolean usesRecipeOutputs(IPatternDetails pattern) {
        return pattern instanceof OmniversalPatternDetails
                || pattern instanceof DynamicComponentPattern dynamic && dynamic.usesDynamicOutputs();
    }

    private static List<GenericStack> compactInputs(KeyCounter[] prototype) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> inputs = new Object2ObjectLinkedOpenHashMap<>();
        for (KeyCounter counter : prototype) {
            for (var entry : counter) {
                inputs.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
            }
        }
        BigInteger maximumLong = BigInteger.valueOf(Long.MAX_VALUE);
        ArrayList<GenericStack> result = new ArrayList<>(inputs.size());
        for (var entry : inputs.object2ObjectEntrySet()) {
            BigInteger remaining = entry.getValue();
            while (remaining.compareTo(maximumLong) > 0) {
                result.add(new GenericStack(entry.getKey(), Long.MAX_VALUE));
                remaining = remaining.subtract(maximumLong);
            }
            if (remaining.signum() > 0) {
                result.add(new GenericStack(entry.getKey(), remaining.longValueExact()));
            }
        }
        return result;
    }

    private static void mergeOutput(
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs,
            @Nullable GenericStack output) {
        if (output == null || output.amount() <= 0L) {
            return;
        }
        outputs.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
    }

    private boolean dispatch(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            long count) {
        if (availableCapacity(patternDetails, prototype, count).logicalCrafts() < count) {
            return false;
        }
        // 统一走 SmartDoublingPatterns.scale：合成样板必须保留 IMolecularAssemblerSupportedPattern 身份，
        // 否则接收方会把它当成处理样板去查合金炉配方。
        IPatternDetails scaledPattern = SmartDoublingPatterns.scale(patternDetails, count);
        KeyCounter[] scaledPrototype = scalePrototype(prototype, count);
        return provider.pushPattern(scaledPattern, scaledPrototype);
    }

    private static boolean isAdvancedAlloyFurnaceOnline(@NotNull AdvancedAlloyFurnaceBlockEntity provider) {
        Level level = provider.getLevel();
        return level != null
                && !level.isClientSide
                && !provider.isRemoved()
                && provider.getMainNode().isActive();
    }

    private static boolean isMePatternAssemblyOnline(@NotNull MePatternAssemblyBlockEntity provider) {
        Level level = provider.getLevel();
        return level != null
                && !level.isClientSide
                && !provider.isRemoved()
                && provider.getMainNode().isActive()
                && provider.getController() != null;
    }

    private static void validateRequestedCount(long requestedCount) {
        if (requestedCount <= 0L) {
            throw new IllegalArgumentException("Requested counted crafting amount must be positive");
        }
    }

    private static long saturatingMultiply(long left, int right) {
        if (left <= 0L || right <= 0) {
            return 0L;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private static long divideByBigInteger(long dividend, BigInteger divisor) {
        if (dividend <= 0L || divisor.signum() <= 0) {
            return 0L;
        }
        BigInteger result = BigInteger.valueOf(dividend).divide(divisor);
        return result.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                ? Long.MAX_VALUE : result.longValueExact();
    }

    private record CapacityLimits(long logicalMaximum, long singleThreadMaximum) {
        private static final CapacityLimits EMPTY = new CapacityLimits(0L, 0L);
    }

    private record AvailableCapacity(long logicalCrafts, long maximumSingleBatch) {
        private static final AvailableCapacity EMPTY = new AvailableCapacity(0L, 0L);
    }

    /** One-shot admission that owns only temporary dispatch references until it is committed. */
    private static final class AlloyFurnaceCountedCraftingAdmission implements CountedCraftingAdmission {
        private AdmissionState state;
        private final long count;
        private boolean transferredInputOwnership;

        private AlloyFurnaceCountedCraftingAdmission(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] preparedPrototype,
                long count) {
            this.state = new PreparedAdmissionState(adapter, patternDetails, preparedPrototype);
            this.count = count;
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return transferredInputOwnership;
        }

        @Override
        public boolean commit(KeyCounter @NotNull [] prototype) {
            if (!(state instanceof PreparedAdmissionState(
                    AlloyFurnaceCountedCraftingAdapter adapter,
                    IPatternDetails patternDetails,
                    KeyCounter[] preparedPrototype))) {
                throw new IllegalStateException("Admission has already been committed");
            }
            if (prototype != preparedPrototype) {
                throw new IllegalArgumentException("Admission must be committed with its prepared prototype");
            }
            state = ReleasedAdmissionState.RELEASED;
            boolean accepted = adapter.dispatch(patternDetails, prototype, count);
            transferredInputOwnership = accepted;
            return accepted;
        }

        /** The admission's retained state is either dispatchable once or already released. */
        private sealed interface AdmissionState permits PreparedAdmissionState, ReleasedAdmissionState {}

        /** Retains the server-thread data required to execute exactly one provider dispatch. */
        private record PreparedAdmissionState(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype) implements AdmissionState {}

        /** Drops all temporary references as soon as the only commit attempt begins. */
        private enum ReleasedAdmissionState implements AdmissionState {
            RELEASED
        }
    }

}
