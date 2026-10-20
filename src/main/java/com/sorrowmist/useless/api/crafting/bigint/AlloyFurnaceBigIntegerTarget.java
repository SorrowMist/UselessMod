package com.sorrowmist.useless.api.crafting.bigint;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.KeyCounter;
import com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Set;

/**
 * 一台支持 BigInteger 批次的多方块万象合金炉，站在「样板供应器」一侧的对外视图。
 *
 * <p>拿到实例的唯一正规途径是 {@link AlloyFurnaceBigIntegerApi#findTargets(IGrid)} /
 * {@link AlloyFurnaceBigIntegerApi#findTarget(IGrid, String)}。实例由机器的方块实体持有，
 * <b>不要缓存到跨 tick 的字段里</b>，每 tick 重新查一次即可（查询本身是网格节点遍历，很轻）。</p>
 *
 * <p><b>仅服务器线程</b>：本接口的所有方法都必须在服务器线程调用。</p>
 */
public interface AlloyFurnaceBigIntegerTarget {

    /** @return provider 局部稳定的路由身份；同一台机器的生命周期内不变 */
    @NotNull String routeIdentity();

    /**
     * @return provider 无关的<b>物理机器身份</b>，格式 {@code 维度@x,y,z}。
     *
     * <p>同一台物理机器无论从哪个 provider 暴露，都必须给出同一个字符串，调用方据此避免超卖
     * 同一台机器。本模组的所有入口都用同一个公式，所以它们认的是同一台机器。</p>
     */
    @NotNull String machineIdentity();

    /** @return 本机所在的 AE 网格；机器离线时为 {@code null} */
    @Nullable IGrid grid();

    /** @return 本机接受的样板种类（AE2 样板物品 id），用于调用方预先过滤 */
    @NotNull Set<ResourceLocation> acceptedPatternKinds();

    /**
     * 查询容量：本机此刻最多能接受多少份「单次推送的原型」。
     *
     * <p>本方法<b>不产生任何副作用</b>，可以随时调用（例如每次 tick 做一次规划）。</p>
     *
     * @param pattern   已<b>解开</b>的样板（不要把倍率包装类传进来；本 API 只认原始样板）
     * @param prototype <b>单次推送</b>的原型材料，不是整批的 count 倍
     * @param requested 期望的份数，必须为正
     * @return 容量结果；为 0 时 {@link AlloyFurnaceBigIntegerCapacity#statusKey()} 给出原因
     */
    @NotNull AlloyFurnaceBigIntegerCapacity capacity(IPatternDetails pattern,
                                                     KeyCounter @NotNull [] prototype,
                                                     @NotNull BigInteger requested);

    /**
     * 本机是否正在<b>动态降频</b>。
     *
     * <p>本模组对每条 bigint 路径都做了「每 tick 时间预算」的动态降频：
     * 实测耗时超预算时，后续批次的容量会被按比例收窄。降频状态下 {@link #capacity} 返回的数字
     * 会明显小于机器的真实能力。</p>
     *
     * <p>这个方法的用途就是让调用方<b>区分</b>这两种情况：</p>
     * <ul>
     *   <li>容量小且 {@code isThrottled() == false} —— 机器能力就这样（材料窗口 / 能量 / 产物分段
     *       或配置的线程数限制）；</li>
     *   <li>容量小且 {@code isThrottled() == true} —— 机器正忙（其它机器或本机的耗时占满了全局
     *       预算）。此时可以主动延后非紧急批次，待预算恢复后再提交。</li>
     * </ul>
     *
     * <p>注意降频<b>不会</b>将容量降至 0：完全上报「无容量」会引发调度侧「无容量 → 重新提交」的
     * 空转，因此降频只收窄批次规模。是否停止提交由调用方决定。</p>
     *
     * @return 是否正在降频
     */
    boolean isThrottled();

    /**
     * 申请一批大数合成，成功时返回一次性凭据（见 {@link AlloyFurnaceBigIntegerBatch} 的调用契约）。
     *
     * <p>本方法<b>不消费材料、不扣能量</b>，只是做一次快照式检查；真正的检查会在
     * {@code commit} 时重新执行（期间机器状态可能变化，最终以 commit 的返回值为准）。</p>
     *
     * @param pattern   已解开的样板
     * @param prototype 单次推送的原型（提交时必须原样传回同一个数组对象）
     * @param requested 期望的份数，必须为正
     * @param cpu       CPU 侧绑定，用于接收产物回执；{@code null} 表示不需要回调
     *                  （产物仍会按段写回 ME 网络）
     * @return 准入凭据；{@code null} 表示此刻不可接受
     */
    @Nullable AlloyFurnaceBigIntegerBatch admit(IPatternDetails pattern,
                                                KeyCounter @NotNull [] prototype,
                                                @NotNull BigInteger requested,
                                                @Nullable AlloyFurnaceBigIntegerCpuBinding cpu);
}
