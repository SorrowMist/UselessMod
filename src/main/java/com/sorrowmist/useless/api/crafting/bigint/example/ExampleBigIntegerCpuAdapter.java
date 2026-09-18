package com.sorrowmist.useless.api.crafting.bigint.example;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerApi;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerCapacity;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchContext;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchResult;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapter;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapters;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>参考实现</b>：一个最小可用的大数 CPU 适配器，演示本 API 的完整用法。
 *
 * <p>这个类不参与运行，只是给第三方 mod 作者照抄的样板。真正接入时请替换掉：</p>
 * <ul>
 *   <li>{@link #ID} —— 改成你自己的命名空间；</li>
 *   <li>注册时机 —— 在你自己 mod 的 {@code FMLCommonSetupEvent#enqueueWork} 里调用
 *       {@code AlloyFurnaceBigIntegerCpuAdapters.register(new YourAdapter())}；</li>
 *   <li>{@link #onBatchAdmitted} / {@link #onBatchOutputs} / {@link #onBatchFinished}
 *       —— 接进你自己的合成任务记账；</li>
 *   <li>{@link #dispatch} —— 接进你自己的 CPU tick 调度。</li>
 * </ul>
 *
 * @see AlloyFurnaceBigIntegerApi 发现入口
 * @see AlloyFurnaceBigIntegerBatch 提交契约（尤其是 {@code count - 1} 份材料的归属）
 */
@ApiStatus.Experimental
public final class ExampleBigIntegerCpuAdapter implements AlloyFurnaceBigIntegerCpuAdapter {
    /** 改成 {@code ResourceLocation.fromNamespaceAndPath("<你的modid>", "bigint_cpu")}。 */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("example_mod", "bigint_cpu");

    /** 示例状态：batchId → 你自己的任务标识。真实实现请换成你的合成任务表。 */
    private final Map<UUID, String> pendingBatches = new ConcurrentHashMap<>();

    /** 示例账本：把机器回报的实际产出按 BigInteger 累起来（不要累加 long 分段，会溢出）。 */
    private final Map<String, BigInteger> deliveredPerJob = new ConcurrentHashMap<>();

    /** 示例统计：单条产出里出现过的最大数量，用来证明 BigInteger 确实用上了。 */
    private volatile BigInteger largestSingleOutput = BigInteger.ZERO;

    @Override
    public @NotNull ResourceLocation id() {
        return ID;
    }

    @Override
    public void onBatchAdmitted(@NotNull AlloyFurnaceBigIntegerBatchContext context) {
        // 材料所有权已转移给机器。这里可以把「本批计划产出多少」记进自己的账本，
        // 并让任务进入「等待机器交付」状态。
        String jobKey = jobKey(context);
        this.pendingBatches.put(context.batchId(), jobKey);
        for (AlloyFurnaceBigIntegerOutput planned : context.plannedOutputs()) {
            this.deliveredPerJob.merge(jobKey, planned.amount(), BigInteger::add);
        }
    }

    @Override
    public void onBatchOutputs(@NotNull AlloyFurnaceBigIntegerBatchContext context,
                               @NotNull List<AlloyFurnaceBigIntegerOutput> actualOutputs) {
        // 产物已经全部写回 ME 网络。actualOutputs 是 BigInteger 精确值 ——
        // 对动态产物（id-only 产物槽）来说，这里的键才是真正的键，可能与 plannedOutputs 的模板不同。
        // 累加一律走 BigInteger：long 分段相加会溢出。
        String jobKey = jobKey(context);
        BigInteger total = BigInteger.ZERO;
        for (AlloyFurnaceBigIntegerOutput actual : actualOutputs) {
            total = total.add(actual.amount());
            if (actual.exceedsLong()) {
                // 这一条已经超过 long，只有 BigInteger 账本能正确表达它。
                this.largestSingleOutput = this.largestSingleOutput.max(actual.amount());
            }
        }
        this.deliveredPerJob.merge(jobKey, total, BigInteger::add);
    }

    @Override
    public void onBatchFinished(@NotNull AlloyFurnaceBigIntegerBatchContext context,
                                @NotNull AlloyFurnaceBigIntegerBatchResult result) {
        String jobKey = this.pendingBatches.remove(context.batchId());
        if (jobKey == null) {
            // 存档重载后 cpuToken 会丢，这里可能找不到对应批次。产物仍已入网，按「机器已交付」处理即可。
            return;
        }
        if (result == AlloyFurnaceBigIntegerBatchResult.CANCELLED) {
            // 机器被拆/任务被取消：产物已被强制写回网络（不会丢），只是没走逐条回执。
            // 不要在这里补记产物，否则会重复记账。
        }
    }

    // ==================== 发配流程 ====================

    /**
     * 参考的发配流程：找到机器 → 查容量 → 申请 → 提交。
     *
     * <p>请在<b>服务器线程</b>、你自己的 CPU tick 里调用。</p>
     *
     * @param grid          你的 CPU 所在网格
     * @param pattern       已解开的样板（万象样板或 AE2 合成样板）
     * @param unitPrototype <b>单次推送</b>的原型材料，不是整批的 count 倍
     * @param requested     你期望发配的份数，可以超过 {@code long}
     * @param jobHandle     你自己的任务句柄，会原样回传给你
     * @param urgent        {@code true} 表示紧急批次，即使机器正在降频也照发
     * @return 实际被接受的份数；{@code null} 表示没有任何机器接单
     */
    public static @Nullable BigInteger dispatch(@Nullable IGrid grid,
                                                @NotNull IPatternDetails pattern,
                                                @NotNull KeyCounter @NotNull [] unitPrototype,
                                                @NotNull BigInteger requested,
                                                @Nullable Object jobHandle,
                                                boolean urgent) {
        if (grid == null || requested.signum() <= 0) {
            return null;
        }
        AlloyFurnaceBigIntegerCpuBinding binding = new AlloyFurnaceBigIntegerCpuBinding(ID, jobHandle);
        for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
            // 机器正忙（全局每 tick 时间预算被打满）时，容量会变小 —— 这里按需延后非紧急批次。
            // 降频永远不会把容量压到 0，所以「要不要停手」由你决定。
            if (target.isThrottled() && !urgent) {
                continue;
            }
            AlloyFurnaceBigIntegerCapacity capacity = target.capacity(pattern, unitPrototype, requested);
            if (!capacity.isAvailable()) {
                // capacity.statusKey() 是可显示给玩家的原因（档次不够 / 缺模具 / 没能量）。
                continue;
            }
            AlloyFurnaceBigIntegerBatch batch =
                    target.admit(pattern, unitPrototype, capacity.accepted(), binding);
            if (batch == null) {
                continue;
            }
            if (batch.commit(unitPrototype)) {
                // 成功：材料所有权已转移，产物会经回调交回来。
                return batch.count();
            }
            // 失败：机器没消费任何材料。请回滚你自己扣掉的那 (count - 1) 份，再试下一台。
            // 注意：只有你自己知道该怎么回滚 —— 机器从不碰你那部分账本。
        }
        return null;
    }

    // ==================== 示例内部实现 ====================

    private static String jobKey(AlloyFurnaceBigIntegerBatchContext context) {
        Object token = context.cpuToken();
        return token == null ? context.batchId().toString() : token.toString();
    }

    /** @return 示例用：某任务累计收到的产出总量 */
    public @Nullable BigInteger deliveredFor(String jobKey) {
        return this.deliveredPerJob.get(jobKey);
    }

    /** @return 示例用：单条产出里出现过的最大数量 */
    public @NotNull BigInteger largestSingleOutput() {
        return this.largestSingleOutput;
    }

    /** @return 示例用：当前仍在等待机器交付的批次数 */
    public int pendingBatchCount() {
        return this.pendingBatches.size();
    }

    /**
     * 注册示例适配器（仅供演示；真实 mod 请在自己初始化阶段调用）。
     */
    @ApiStatus.Experimental
    public static void registerSelf() {
        AlloyFurnaceBigIntegerCpuAdapters.register(new ExampleBigIntegerCpuAdapter());
    }
}
