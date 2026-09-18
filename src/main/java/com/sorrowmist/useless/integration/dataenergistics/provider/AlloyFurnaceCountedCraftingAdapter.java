package com.sorrowmist.useless.integration.dataenergistics.provider;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import com.fish_dan_.data_energistics.api.crafting.dispatch.BigIntegerCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.BigIntegerCraftingProviderAdapter;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingCapacity;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingRoutingMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingTarget;
import com.fish_dan_.data_energistics.api.registry.provider.runtime.PatternProviderIdentity;
import com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MePatternAssemblyBlockEntity;
import com.sorrowmist.useless.content.blockentities.multiblock.MultiblockAlloyFurnaceCoreBlockEntity;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AlloyFurnaceTickBudget;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.CraftingTaskContext;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.DynamicComponentPattern;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.OmniversalPatternDetails;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 *   <li><b>原生 bigint</b>（{@link #prepareBigIntegerBatch}，仅合成样板）：DE 3.3.0 的 exact 派发。
 *       它只在「本 target 已被异步提案排他预留」时触发，一次能交付<b>超过 long</b> 的批次；
 *       关键是这里拿到的 prototype 是<b>单次合成的原型</b>，count 只通过 {@code exactCount()} 传递，
 *       剩余 count-1 份材料由 DE 自己的 BigInteger 账本扣除，我们只消费手里那一份原型。</li>
 * </ul>
 *
 * <p><b>为什么只有合成样板发布 machine identity</b>：DE 的 exact 分支要求容量快照带
 * provider 无关的 machine identity（排他预留的前提），而它一旦生效就会<b>完全接管</b>该 provider
 * 的派发（不会再回落长版路径）。所以只有真正实现了 bigint 语义的合成样板才发 machine target；
 * 万象样板/处理样板继续用 {@code route(...)} 目标走长版路径。</p>
 */
final class AlloyFurnaceCountedCraftingAdapter implements BigIntegerCraftingProviderAdapter {
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

    /** Creates the live adapter used by one standalone advanced alloy furnace. */
    static AlloyFurnaceCountedCraftingAdapter forAdvancedAlloyFurnace(
            @NotNull AdvancedAlloyFurnaceBlockEntity provider,
            @NotNull PatternProviderIdentity identity) {
        String digest = identity.digest();
        return new AlloyFurnaceCountedCraftingAdapter(
                provider,
                () -> isAdvancedAlloyFurnaceOnline(provider),
                digest,
                machineIdentity(digest, provider.getLevel(), provider.getBlockPos()),
                () -> provider,
                provider::getRemainingAETaskCount,
                provider::getMaxAETaskCount,
                provider::pushBigIntegerCraftingPattern);
    }

    /** Creates the live adapter used by one ME pattern assembly and its linked multiblock controller. */
    static AlloyFurnaceCountedCraftingAdapter forMePatternAssembly(
            @NotNull MePatternAssemblyBlockEntity provider,
            @NotNull PatternProviderIdentity identity) {
        String digest = identity.digest();
        return new AlloyFurnaceCountedCraftingAdapter(
                provider,
                () -> isMePatternAssemblyOnline(provider),
                digest,
                machineIdentity(digest, provider.getLevel(), provider.getBlockPos()),
                provider::getController,
                craftingPattern -> {
                    MultiblockAlloyFurnaceCoreBlockEntity controller = provider.getController();
                    return controller == null ? 0 : controller.getRemainingAETaskCount(craftingPattern);
                },
                () -> {
                    MultiblockAlloyFurnaceCoreBlockEntity controller = provider.getController();
                    return controller == null ? 0 : controller.getMaxAETaskCount();
                },
                provider::pushBigIntegerCraftingPattern);
    }

    // ==================== 长版 counted 路径 ====================

    @Override
    public @Nullable CountedCraftingAdmission prepareBatch(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        return prepareAdmission(patternDetails, prototype, requestedCount);
    }

    @Override
    public @NotNull ObjectList<@NotNull CountedCraftingCapacity> captureCapacityFast(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] prototype, long requestedCount) {
        AvailableCapacity capacity = availableCapacity(patternDetails, prototype, requestedCount);
        if (capacity.logicalCrafts() == 0L) {
            return ObjectLists.emptyList();
        }
        return ObjectLists.singleton(new CountedCraftingCapacity(
                targetFor(patternDetails),
                CountedCraftingRoutingMode.TARGETED,
                OptionalLong.of(capacity.logicalCrafts()),
                OptionalLong.of(capacity.maximumSingleBatch())));
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

    // ==================== 原生 bigint（exact）路径 ====================

    @Override
    public @Nullable BigIntegerCraftingAdmission prepareBigIntegerBatch(
            @NotNull IPatternDetails patternDetails,
            KeyCounter @NotNull [] prototype,
            @NotNull BigInteger requestedCount,
            @NotNull CountedCraftingTarget requestedTarget) {
        if (requestedCount.signum() <= 0 || !targetFor(patternDetails).equals(requestedTarget)) {
            return null;
        }
        BigInteger accepted = availableBigIntegerCount(patternDetails, prototype, requestedCount);
        return accepted.signum() <= 0
                ? null
                : new AlloyFurnaceBigIntegerAdmission(this, patternDetails, prototype, accepted);
    }

    /**
     * bigint 批次的可接受量：只对合成样板开放（本机能一次装配折叠任意份数），
     * 上限由产物分段预算反推；其它样板返回 0，让 DE 继续走长版 counted 路径。
     */
    private @NotNull BigInteger availableBigIntegerCount(
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
        if (!(original instanceof IMolecularAssemblerSupportedPattern craftingPattern)) {
            return BigInteger.ZERO;
        }
        if (prototype == null || prototype.length == 0) {
            return BigInteger.ZERO;
        }
        // 回网队列有空位才收新批次（跟长版路径同一套背压）。
        if (this.remainingThreads.applyAsInt(true) <= 0) {
            return BigInteger.ZERO;
        }
        return requestedCount.min(maximumWindowedCount(craftingPattern, prototype, machineThreads()));
    }

    /**
     * bigint 批次的次数上限：**一个线程一份 long 总量的材料窗口**。
     *
     * <p>语义（用户定的）：线程数 N ⇒ 这批最多吃下 N 份「每种材料各 {@code Long.MAX}」的量。
     * 九合一配方每 craft 消耗 9 个同种材料，于是单批 = {@code N × Long.MAX / 9} 次合成
     * （N=10000 时 ≈ 1.02e22），正好对应「每线程一个老物理窗口、整批一次交付」。
     * 逐键取最小：每个材料键各自都不能越过它那份 long 窗口。</p>
     *
     * <p>另外再叠一层产物分段上限（{@link #maximumSegmentedCount}，同样跟随线程数而非固定值），
     * 防「一次合成产出很多个物品」的配方把回网分段列表撑得过大 —— 正常配方下它不生效。</p>
     *
     * <p>传入的 {@code prototype} 是<b>单次合成的原型</b>，所以逐键的数量就是「每次合成的消耗量」。</p>
     */
    private static @NotNull BigInteger maximumWindowedCount(@NotNull IPatternDetails pattern,
                                                            KeyCounter @NotNull [] prototype,
                                                            int threads) {
        BigInteger window = BigInteger.valueOf(Long.MAX_VALUE)
                .multiply(BigInteger.valueOf(Math.max(1, threads)));
        BigInteger limit = null;
        for (KeyCounter counter : prototype) {
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount <= 0L) {
                    continue;
                }
                BigInteger allowed = window.divide(BigInteger.valueOf(amount));
                limit = limit == null ? allowed : limit.min(allowed);
            }
        }
        BigInteger segmented = maximumSegmentedCount(pattern, threads);
        // 动态降频：本机最近实测耗时超预算时，按比例收窄本批次的窗口预算
        // （仿数据能源的 30ms 提交预算，见 AlloyFurnaceTickBudget）。
        BigInteger limitWithBudget = limit == null ? segmented : limit.min(segmented);
        return AlloyFurnaceTickBudget.applyScale(limitWithBudget);
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

    /** 由「每键最多切多少段」反推单批次数上限，避免产物分段列表被打爆。 */
    /**
     * bigint 批次的次数上限：由「本机当前线程数」反推能安全承载的产物分段数。
     *
     * <p>产物按 {@code CraftingAeAmountAccumulator#segments()} 切成 ≤ long 的段入队，
     * 段数随 count 线性增长，所以上限 = {@code Long.MAX × 分段预算 / 单位产出量}；
     * 分段预算就是本机线程数（见 {@link #machineThreads()}）—— 一个窗口的产物通常只占一段，
     * 于是「线程数份窗口」量级刚好对应一段/窗口，不需要也不应该写死常数。</p>
     *
     * <p>真正该卡住多少由数据能源自己算：{@code maximumExactLogicalFirings} 已按精确库存、
     * {@code MAX_EXACT_DISPATCH_AMOUNT} 与可用能量逐项取过最小值。</p>
     */
    private static @NotNull BigInteger maximumSegmentedCount(@NotNull IPatternDetails pattern, int threads) {
        BigInteger maximumLong = BigInteger.valueOf(Long.MAX_VALUE);
        BigInteger segments = BigInteger.valueOf(Math.max(1, threads));
        BigInteger limit = null;
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null || output.amount() <= 0L) {
                continue;
            }
            BigInteger allowed = maximumLong.multiply(segments).divide(BigInteger.valueOf(output.amount()));
            limit = limit == null ? allowed : limit.min(allowed);
        }
        return limit == null ? maximumLong.multiply(segments) : limit;
    }

    /** exact 批次的实际提交：把单位原型与 BigInteger 次数交给机器。 */
    private boolean dispatchBigInteger(
            @NotNull IPatternDetails patternDetails, KeyCounter @NotNull [] unitPrototype, @NotNull BigInteger count) {
        if (availableBigIntegerCount(patternDetails, unitPrototype, count).compareTo(count) < 0) {
            return false;
        }
        return this.bigIntegerPush.push(patternDetails, count, unitPrototype);
    }

    // ==================== 共用：目标与容量 ====================

    /**
     * 合成样板发布带 machine identity 的 TARGETED 目标（exact 派发的前提），
     * 其余样板只发 route 目标 —— 这样 DE 不会对它们启用 exact 分支（那会跳过长版路径）。
     */
    private @NotNull CountedCraftingTarget targetFor(@NotNull IPatternDetails patternDetails) {
        return supportsExactBatch(patternDetails)
                ? CountedCraftingTarget.machine(this.routeIdentity, this.machineIdentity)
                : CountedCraftingTarget.route(this.routeIdentity);
    }

    private static boolean supportsExactBatch(@NotNull IPatternDetails patternDetails) {
        return SmartDoublingPatterns.unwrap(patternDetails) instanceof IMolecularAssemblerSupportedPattern;
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

    private AvailableCapacity availableCapacity(
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
        int availableThreads = Math.max(0, this.remainingThreads.applyAsInt(craftingPattern));
        int totalThreads = Math.max(0, this.totalThreads.getAsInt());
        availableThreads = Math.min(availableThreads, totalThreads);
        if (craftingPattern) {
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

    private static @NotNull String machineIdentity(
            @NotNull String fallback, @Nullable Level level, @NotNull BlockPos pos) {
        if (level == null) {
            return fallback;
        }
        ResourceLocation dimension = level.dimension().location();
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
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

    /**
     * bigint 批次的准入：{@code exactCount()} 可以超过 {@code long}，因此 {@code count()} 保持接口默认
     * （{@code longValueExact()} 会在超限时抛异常而不是截断）—— exact 路径的记账一律读 {@code exactCount()}。
     */
    private static final class AlloyFurnaceBigIntegerAdmission implements BigIntegerCraftingAdmission {
        private AdmissionState state;
        private final BigInteger count;
        private boolean transferredInputOwnership;

        private AlloyFurnaceBigIntegerAdmission(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] preparedPrototype,
                @NotNull BigInteger count) {
            this.state = new PreparedState(adapter, patternDetails, preparedPrototype);
            this.count = count;
        }

        @Override
        public @NotNull BigInteger exactCount() {
            return count;
        }

        @Override
        public boolean hasTransferredInputOwnership() {
            return transferredInputOwnership;
        }

        @Override
        public boolean commit(KeyCounter @NotNull [] prototype) {
            if (!(state instanceof PreparedState(
                    AlloyFurnaceCountedCraftingAdapter adapter,
                    IPatternDetails patternDetails,
                    KeyCounter[] preparedPrototype))) {
                throw new IllegalStateException("Exact admission has already been committed");
            }
            if (prototype != preparedPrototype) {
                throw new IllegalArgumentException("Exact admission must be committed with its prepared prototype");
            }
            state = ReleasedState.RELEASED;
            boolean accepted = adapter.dispatchBigInteger(patternDetails, prototype, count);
            transferredInputOwnership = accepted;
            return accepted;
        }

        private sealed interface AdmissionState permits PreparedState, ReleasedState {}

        private record PreparedState(
                @NotNull AlloyFurnaceCountedCraftingAdapter adapter,
                @NotNull IPatternDetails patternDetails,
                KeyCounter @NotNull [] prototype) implements AdmissionState {}

        private enum ReleasedState implements AdmissionState {
            RELEASED
        }
    }
}
