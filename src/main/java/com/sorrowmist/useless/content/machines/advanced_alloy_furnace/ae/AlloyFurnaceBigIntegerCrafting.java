package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.catalyst.ResolvedCatalystEffect;
import com.sorrowmist.useless.content.recipe.AdvancedAlloyFurnaceRecipe;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 万象样板 BigInteger 批次的共享数学引擎。
 *
 * <p>原先这套「窗口 / 分段 / 上限」的算术只存在于 Data Energistics 的适配器里，现在抽出来给
 * 两处共用：DE 适配器（保持既有行为）与对外公开的 bigint API（{@code api.crafting.bigint}）。
 * 抽出来的好处是两条入口对同一台机器算出的容量、上限与能量口径完全一致，不会各自漂移。</p>
 *
 * <p><b>语义前提</b>：bigint 批次是「折叠」语义 —— 一次解析/装配得到单份产出，再整体 ×count，
 * 因此这里的上限都是「在不让产物分段列表爆炸的前提下，本批最多能接多少份」。</p>
 *
 * <p><b>只对万象样板生效</b>：本类的方法都要求宿主
 * {@link CraftingTaskContext#supportsBigIntegerRecipeBatches()} 为 true（目前只有多方块核心）。
 * 合成样板的 bigint 路径不经过这里，它直接由「一次装配 + BigInteger 放大」完成。</p>
 */
public final class AlloyFurnaceBigIntegerCrafting {
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);

    private AlloyFurnaceBigIntegerCrafting() {
    }

    /** 宿主是否具备「把万象样板折叠成 BigInteger 批次」的能力。 */
    public static boolean supports(@Nullable CraftingTaskContext context) {
        return context != null && context.supportsBigIntegerRecipeBatches();
    }

    // ==================== 单份产出 ====================

    /**
     * 单次「样板推送」对应的实际产出（主产物 + 流体 + 隐藏键产出，按键合并）。
     *
     * <p>与 {@code CraftingTask#generatePendingOutputs} 的口径一致：一律取 <b>配方</b> 的产出，
     * 而不是样板声明的模板键 —— 样板声明的 id-only 产物槽只是模板，真正落进网络的是配方产出。</p>
     *
     * <p>同键合并时按 long 饱和：单份产出里同一个键同时出现在 {@code outputs()} 与
     * {@code keyOutputs()} 才会叠加，实际配方不可能越过 long，这里只是兜底不写坏账。</p>
     *
     * @param manualOperations 一次样板推送代表多少次基础配方操作（手动放大样板 &gt; 1，普通为 1）
     */
    public static @NotNull List<GenericStack> unitOutputs(@NotNull AdvancedAlloyFurnaceRecipe recipe,
                                                          long manualOperations) {
        long multiplier = Math.max(1L, manualOperations);
        Object2ObjectLinkedOpenHashMap<AEKey, Long> merged = new Object2ObjectLinkedOpenHashMap<>();
        for (ItemStack output : recipe.outputs()) {
            mergeUnitOutput(merged, GenericStack.fromItemStack(output), multiplier);
        }
        for (FluidStack output : recipe.outputFluids()) {
            mergeUnitOutput(merged, GenericStack.fromFluidStack(output), multiplier);
        }
        for (GenericStack output : recipe.keyOutputs()) {
            mergeUnitOutput(merged, output, multiplier);
        }
        List<GenericStack> result = new ArrayList<>(merged.size());
        for (var entry : merged.object2ObjectEntrySet()) {
            long amount = entry.getValue();
            if (amount > 0L) {
                result.add(new GenericStack(entry.getKey(), amount));
            }
        }
        return List.copyOf(result);
    }

    /** 单次样板推送的单份产出（不折手动倍率）。 */
    public static @NotNull List<GenericStack> unitOutputs(@NotNull AdvancedAlloyFurnaceRecipe recipe) {
        return unitOutputs(recipe, 1L);
    }

    private static void mergeUnitOutput(Object2ObjectLinkedOpenHashMap<AEKey, Long> merged,
                                        @Nullable GenericStack output,
                                        long multiplier) {
        if (output == null || output.what() == null || output.amount() <= 0L) {
            return;
        }
        long scaled = output.amount() > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE : output.amount() * multiplier;
        merged.merge(output.what(), scaled, AlloyFurnaceBigIntegerCrafting::saturatingAdd);
    }

    // ==================== 对外回执用的产出 ====================

    /** 样板<b>声明</b>的产物（模板键）× count，作为「计划产出」给 CPU 侧对照。 */
    public static @NotNull List<AlloyFurnaceBigIntegerOutput> plannedOutputs(@NotNull IPatternDetails pattern,
                                                                            @NotNull BigInteger count) {
        return scaleToBigIntegerOutputs(pattern.getOutputs(), count);
    }

    /** 单份实际产出 × count —— 本批真正会产出的 BigInteger 数量。 */
    public static @NotNull List<AlloyFurnaceBigIntegerOutput> scaledOutputs(@NotNull List<GenericStack> unitOutputs,
                                                                           @NotNull BigInteger count) {
        return scaleToBigIntegerOutputs(unitOutputs, count);
    }

    private static @NotNull List<AlloyFurnaceBigIntegerOutput> scaleToBigIntegerOutputs(
            @NotNull List<GenericStack> outputs, @NotNull BigInteger count) {
        List<AlloyFurnaceBigIntegerOutput> result = new ArrayList<>(outputs.size());
        for (GenericStack output : outputs) {
            if (output == null || output.what() == null || output.amount() <= 0L) {
                continue;
            }
            result.add(new AlloyFurnaceBigIntegerOutput(
                    output.what(), BigInteger.valueOf(output.amount()).multiply(count)));
        }
        return List.copyOf(result);
    }

    // ==================== 机器身份 ====================

    /**
     * 物理机器身份：{@code 维度@x,y,z}；{@code level} 为空时退回 {@code fallback}。
     *
     * <p>这是**唯一**的身份公式：数据能源适配器与对外公开 API 都走这里，保证两条入口认的是
     * 同一台物理机器（DE 靠它避免超卖，公开 API 靠它做发现与去重）。</p>
     */
    public static @NotNull String machineIdentity(@NotNull String fallback,
                                                 @Nullable Level level,
                                                 @NotNull BlockPos pos) {
        if (level == null) {
            return fallback;
        }
        ResourceLocation dimension = level.dimension().location();
        return dimension + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    // ==================== 上限 ====================
    /**
     * 「材料窗口」上限：本机线程数 N ⇒ 本批最多吃下 N 份「每种材料各 {@code Long.MAX}」的量。
     *
     * <p>传入的 {@code prototype} 是<b>单次推送的原型</b>，所以逐键的数量就是「每次推送的消耗量」；
     * 逐键取最小，每个材料键各自都不能越过它那份 long 窗口。</p>
     *
     * @return 非负上限；原型里没有正数条目时返回整个窗口（等价于不设限）
     */
    public static @NotNull BigInteger maximumWindowedCount(@NotNull KeyCounter @NotNull [] prototype,
                                                           int threads) {
        BigInteger window = MAX_LONG.multiply(BigInteger.valueOf(Math.max(1, threads)));
        BigInteger limit = null;
        for (KeyCounter counter : prototype) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount <= 0L) {
                    continue;
                }
                BigInteger allowed = window.divide(BigInteger.valueOf(amount));
                limit = limit == null ? allowed : limit.min(allowed);
            }
        }
        return limit == null ? window : limit;
    }

    /**
     * 「产物分段」上限：由「分段预算」反推能安全承载的推送次数。
     *
     * <p>产物按 {@code CraftingAeAmountAccumulator#segments()} 切成 ≤ long 的段入队，段数随 count
     * 线性增长，所以上限 = {@code Long.MAX × 分段预算 / 单位产出量}。</p>
     *
     * @param unitOutputs   单次推送的实际产出（见 {@link #unitOutputs(AdvancedAlloyFurnaceRecipe, long)}）
     * @param segmentBudget 本批允许产生的分段数（由 AIMD 控制器给出，恒 ≥ 1）
     */
    public static @NotNull BigInteger maximumSegmentedCount(@NotNull List<GenericStack> unitOutputs,
                                                            long segmentBudget) {
        BigInteger segments = BigInteger.valueOf(Math.max(1L, segmentBudget));
        BigInteger limit = null;
        for (GenericStack output : unitOutputs) {
            if (output == null || output.amount() <= 0L) {
                continue;
            }
            BigInteger allowed = MAX_LONG.multiply(segments).divide(BigInteger.valueOf(output.amount()));
            limit = limit == null ? allowed : limit.min(allowed);
        }
        return limit == null ? MAX_LONG.multiply(segments) : limit;
    }

    /**
     * 「能量」上限：按可用能量反推本批最多能收多少份。
     *
     * <p>口径与 {@code AlloyFurnaceRecipeExecutor} 完全对齐：</p>
     * <ul>
     *   <li>与并行相关（{@code energyMultipliesWithParallel}）：单份能耗 = {@code recipeEnergy / divisor}，
     *       所以份数上限 = {@code 可用能量 × divisor / recipeEnergy}；</li>
     *   <li>与并行无关：整批只收一次固定能耗，只要有能量就<b>不设限</b>。</li>
     * </ul>
     *
     * <p><b>有用线圈（tier 10）走的是第二条 ⇒ 本方法返回 {@code null}（无能量闸）</b>，因此它的
     * bigint 容量只受材料窗口与产物分段预算限制，能吃到完整规模。这个性质由两处共同保证：
     * {@code CatalystEffectResolver} 把 {@code energyMultipliesWithParallel} 设为
     * {@code !isUsefulIngot()}，而 {@code OmniversalCoilStats} 给 tier 10 的催化剂类型就是
     * {@link com.sorrowmist.useless.api.enums.CatalystType#USEFUL_INGOT}。
     * <b>改这两处任一处都会让有用线圈的能量变成按 count 翻倍，从而把 bigint 批次压回小规模。</b></p>
     *
     * @return 上限；{@code null} 表示「不设限」，{@link BigInteger#ZERO} 表示完全没能量
     */
    public static @Nullable BigInteger maximumCountForEnergy(@NotNull CraftingTaskContext context,
                                                             @NotNull AdvancedAlloyFurnaceRecipe recipe) {
        long recipeEnergy = Math.max(0L, recipe.energy());
        if (recipeEnergy <= 0L) {
            return null;
        }
        long available = Math.max(0L, context.getEnergyManager().getEnergyStoredLong());
        ResolvedCatalystEffect effect = context.resolveTaskEffect(recipe);
        int divisor = divisorOf(effect);
        if (multipliesWithParallel(effect)) {
            return BigInteger.valueOf(available)
                    .multiply(BigInteger.valueOf(divisor))
                    .divide(BigInteger.valueOf(recipeEnergy));
        }
        return available >= divideRoundUp(recipeEnergy, divisor) ? null : BigInteger.ZERO;
    }

    /**
     * 本批实际要扣的总能量（BigInteger 版 {@code calculateTargetTotalEnergy}）。
     *
     * <p>正常路径下 {@code count} 已经被 {@link #maximumCountForEnergy} 收窄过，所以结果必然落在
     * long 内；这里仍然做饱和兜底，避免调用方传入任意 count 时抛异常。</p>
     *
     * <p><b>有用线圈（tier 10）不会因 count 翻倍</b>：它的催化剂是有用锭，
     * {@code energyMultipliesWithParallel == false} ⇒ 这里走「整批一次固定能耗」分支，
     * 结果 = {@code ceil(recipeEnergy / 1024)}，与 {@code count} 无关。所以即使一批发配
     * {@code 1e22} 份，也只收这么一点能量 —— 与长版 counted 路径同一口径。</p>
     *
     * @param count 本批的推送次数，必须为正
     */
    public static long totalEnergy(@NotNull AdvancedAlloyFurnaceRecipe recipe,
                                   @NotNull BigInteger count,
                                   @Nullable ResolvedCatalystEffect effect) {
        long recipeEnergy = Math.max(0L, recipe.energy());
        if (recipeEnergy <= 0L) {
            return 0L;
        }
        int divisor = divisorOf(effect);
        if (!multipliesWithParallel(effect)) {
            return divideRoundUp(recipeEnergy, divisor);
        }
        BigInteger total = BigInteger.valueOf(recipeEnergy)
                .multiply(count)
                .add(BigInteger.valueOf(divisor - 1L))
                .divide(BigInteger.valueOf(divisor));
        return total.compareTo(MAX_LONG) >= 0 ? Long.MAX_VALUE : total.longValueExact();
    }

    /**
     * 合成样板 bigint 批次的次数上限：材料窗口 ∩ 产物分段预算，最后叠加运行时降频。
     *
     * <p>合成样板由本机在虚拟 3×3 工作台上装配<b>一次</b>再折叠，<b>不收能量</b>，
     * 所以这里没有能量闸 —— 与长版 counted 路径的合成样板分支口径一致。</p>
     *
     * @param prototype     单次装配的原型（不是 ×count 的整批材料）
     * @param segmentBudget 本批允许产生的分段数（AIMD 控制器给出）
     */
    public static @NotNull BigInteger maximumCraftingPatternCount(@NotNull IPatternDetails pattern,
                                                                  @NotNull KeyCounter @NotNull [] prototype,
                                                                  int threads,
                                                                  long segmentBudget) {
        BigInteger limit = maximumWindowedCount(prototype, threads)
                .min(maximumSegmentedCount(pattern.getOutputs(), segmentBudget));
        return limit.signum() <= 0 ? BigInteger.ZERO : AlloyFurnaceTickBudget.applyScale(limit);
    }

    /**
     * 万象样板 bigint 批次的综合上限：配方可用性 → 窗口 → 分段 → 能量，最后叠加运行时降频。
     *
     * <p>返回 {@link BigInteger#ZERO} 表示「本机此刻一份都收不了」，调用方据此<b>不要</b>发布
     * machine identity（DE 的 exact 分支一旦接管就不再回落长版路径，发布容量却拒绝会卡死该 provider）。</p>
     *
     * @param prototype     单次推送的原型（不是 ×count 的整批材料）
     * @param threads       本机当前线程数（多方块跟随线圈线程）
     * @param segmentBudget 本批允许产生的分段数（AIMD 控制器给出）
     */
    public static @NotNull BigInteger maximumCount(@NotNull CraftingTaskContext context,
                                                   @NotNull OmniversalPatternDetails pattern,
                                                   @NotNull KeyCounter @NotNull [] prototype,
                                                   int threads,
                                                   long segmentBudget) {
        AdvancedAlloyFurnaceRecipe recipe = pattern.recipe();
        if (recipe == null || !context.isTaskRecipeAvailable(recipe)) {
            return BigInteger.ZERO;
        }
        long manualOperations = SmartDoublingPatterns.manualOperationsPerPattern(recipe, pattern);
        if (manualOperations <= 0L) {
            // 与长版容量侧同一口径：无法证明「一次推送 = 整数次配方操作」时不接这批。
            return BigInteger.ZERO;
        }
        List<GenericStack> unitOutputs = unitOutputs(recipe, manualOperations);
        if (unitOutputs.isEmpty()) {
            return BigInteger.ZERO;
        }
        BigInteger limit = maximumWindowedCount(prototype, threads)
                .min(maximumSegmentedCount(unitOutputs, segmentBudget));
        BigInteger energyCap = maximumCountForEnergy(context, recipe);
        if (energyCap != null) {
            limit = limit.min(energyCap);
        }
        if (limit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        // 动态降频：本机最近实测耗时超预算时，按比例收窄本批次的窗口预算。
        return AlloyFurnaceTickBudget.applyScale(limit);
    }

    // ==================== 内部 ====================

    /**
     * {@code null} 催化剂效果按「与并行相关」处理：那是收费更多的一侧，宁可少收也不免费。
     */
    private static boolean multipliesWithParallel(@Nullable ResolvedCatalystEffect effect) {
        return effect == null || effect.energyMultipliesWithParallel();
    }

    private static int divisorOf(@Nullable ResolvedCatalystEffect effect) {
        return effect == null ? 1 : Math.max(1, effect.energyDivisor());
    }

    private static long divideRoundUp(long amount, int divisor) {
        if (amount <= 0L) {
            return 0L;
        }
        long normalizedDivisor = Math.max(1, divisor);
        return 1L + (amount - 1L) / normalizedDivisor;
    }

    private static long saturatingAdd(long left, long right) {
        return right > 0L && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
