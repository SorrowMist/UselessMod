package com.sorrowmist.useless.api.crafting.bigint.cpu;

import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * CPU 侧的产物回执通道：实现它并在 mod 初始化阶段注册，就能收到大数批次的产物与终局通知。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>机器产出的总量可以超过 {@code long}，而 AE2 的存储接口单次最多接受 {@code long}，
 * 所以产物是按 {@code Long.MAX_VALUE} 切段、逐 tick 写回 ME 网络的。若你的 CPU 自己维护一份
 * BigInteger 账本（例如给「超长合成任务」记账），只靠网络里的分段是拼不回精确总数的 ——
 * 这个通道会把<b>完整</b>的 BigInteger 产物直接告诉你。</p>
 *
 * <h2>回调约定</h2>
 *
 * <ul>
 *   <li>全部回调都在<b>服务器线程</b>执行，且都在提交成功之后。</li>
 *   <li>{@link #onBatchAdmitted} 在 {@code commit} 成功后<b>立即</b>调用（同一 tick）。</li>
 *   <li>{@link #onBatchOutputs} 在产物<b>全部</b>写回网络之后调用一次；
 *       {@link #onBatchFinished} 紧接着以 {@link AlloyFurnaceBigIntegerBatchResult#SUCCESS} 调用。</li>
 *   <li>批次被取消时只调用 {@link #onBatchFinished}（状态 {@code CANCELLED}），不调用 {@code onBatchOutputs}。</li>
 *   <li>实现必须<b>不抛异常</b>：抛出的异常会被记录并吞掉，但你的状态可能因此不一致。
 *       请不要在回调里再调用 {@code admit}/{@code commit}（会造成重入）。</li>
 *   <li><b>不要持有机器引用</b>：上下文里没有任何机器对象，这是刻意的。</li>
 * </ul>
 *
 * <h2>没有适配器时会怎样</h2>
 *
 * <p>完全没有降级风险：不注册适配器（或 {@code admit} 时传 {@code cpu = null}）时，
 * 产物依旧照常切段写回 ME 网络，只是不发这些回调。</p>
 */
public interface AlloyFurnaceBigIntegerCpuAdapter {

    /** @return 全局唯一的适配器 id，用于注册去重与持久化 */
    @NotNull ResourceLocation id();

    /**
     * 批次已被机器接受（材料所有权已转移）。
     *
     * <p>适合在这里把「本批要产出什么」记进自己的账本，或标记任务进入等待状态。</p>
     */
    void onBatchAdmitted(@NotNull AlloyFurnaceBigIntegerBatchContext context);

    /**
     * 产物已全部写回 ME 网络，下面是本批的<b>实际</b>产出（BigInteger 精确值）。
     *
     * <p>对动态产物（id-only 产物槽）而言，这里的键可能与
     * {@link AlloyFurnaceBigIntegerBatchContext#plannedOutputs()} 里的模板键不同 ——
     * 请以这里为准。</p>
     */
    void onBatchOutputs(@NotNull AlloyFurnaceBigIntegerBatchContext context,
                        @NotNull List<AlloyFurnaceBigIntegerOutput> actualOutputs);

    /**
     * 批次终局。
     *
     * @param result 见 {@link AlloyFurnaceBigIntegerBatchResult}
     */
    void onBatchFinished(@NotNull AlloyFurnaceBigIntegerBatchContext context,
                         @NotNull AlloyFurnaceBigIntegerBatchResult result);

    /**
     * 产物<b>落网之前</b>的「整批接收」机会 —— 这是唯一能<b>跳过逐段插入</b>的通道。
     *
     * <h2>为什么需要它</h2>
     * <p>AE2 的存储接口单次只收 {@code long}（{@code IMEInventory#insert(AEKey, long, Actionable)}），
     * 所以本机默认要把产物按 {@code Long.MAX_VALUE} 切段、<b>逐段</b>写回网络
     * （实测约 2.6µs/段，一 tick 两万多段就是几十毫秒）。若你愿意直接收下整批，
     * 这段插入就完全不需要发生。</p>
     *
     * <h2>调用时机与语义</h2>
     * <ul>
     *   <li>在产物写回网络<b>之前</b>、按<b>键</b>调用；返回你收下的量。</li>
     *   <li>机器会把收下的量从待回网账本中<b>扣除</b>，只把余额写回网络。</li>
     *   <li>默认返回空 Map ⇒ 行为与不实现本方法<b>完全一致</b>（全部写回网络）。</li>
     * </ul>
     *
     * <h2>⚠️ 接收即接管：责任随之转移</h2>
     * <p><b>收下的物品不会进入 ME 网络</b>，只存在于你自己的账本里。因此：</p>
     * <ul>
     *   <li>你必须负责它们后续的去向（自行落网、或作为中间产物消耗）；</li>
     *   <li><b>机器无法退还</b> —— 账本在调用返回时已被扣减。若批次随后被取消
     *       （{@link #onBatchFinished} 收到 {@code CANCELLED}），已收下的部分需由你自行处理；</li>
     *   <li>实现必须<b>不抛异常</b>；抛出的异常会被记录并吞掉，但可能造成产物归属不一致。</li>
     * </ul>
     *
     * <p>出于安全考虑，本机只会在「整趟刷写绑定的都是同一个适配器」时调用本方法；
     * 多 CPU 混绑同一台机器时不会调用（退回逐段写回）。</p>
     *
     * @param context 本批上下文（与其它回调同一个）
     * @param outputs 本次<b>准备写回网络</b>的产物；可只收其中一部分（返回量 ≤ 这里的量）
     * @return 你实际收下的量，按 key 计；未列出的 key 视为不收
     */
    default @NotNull java.util.Map<appeng.api.stacks.AEKey, java.math.BigInteger> claimOutputs(
            @NotNull AlloyFurnaceBigIntegerBatchContext context,
            @NotNull java.util.List<AlloyFurnaceBigIntegerOutput> outputs) {
        return java.util.Map.of();
    }
}
