package com.sorrowmist.useless.api.crafting.bigint;

import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

/**
 * 一次性的大数批次准入凭据，由 {@link AlloyFurnaceBigIntegerTarget#admit} 返回。
 *
 * <h2>调用契约（务必读完）</h2>
 *
 * <ol>
 *   <li><b>只能提交一次</b>。{@link #commit} 第二次调用会抛 {@link IllegalStateException}。</li>
 *   <li><b>提交必须用「准备时那一份原型」</b>：调用 {@code admit} 时传入的
 *       {@code KeyCounter[]} 数组对象本身，不能复制、不能重建。用别的数组提交会抛
 *       {@link IllegalArgumentException}。</li>
 *   <li><b>我们只消费手里那一份原型</b>。{@code admit} 传入的原型是<b>单次推送</b>的材料，
 *       不是整批的 {@code count} 倍。整批里剩下的 {@code count - 1} 份材料由
 *       <b>调用方自己的 BigInteger 账本</b>负责扣除（数据能源的 Trinity 数据核心就是这么做的：
 *       它用 {@code TrinityExactInputTransaction} 扣账，并在我们提交失败时回滚）。
 *       <b>绝不要把 count 乘进原型</b>。</li>
 *   <li><b>提交失败不代表材料丢失</b>：{@link #commit} 返回 {@code false} 时我们不会清空原型，
 *       调用方应据此回滚它自己的那 {@code count - 1} 份扣账，并稍后重试或改走别的供应器。</li>
 *   <li><b>提交返回 true 即转移所有权</b>：此后原型里的材料归本机所有（我们会清空它），
 *       产物由本机按 {@link AlloyFurnaceBigIntegerOutput} 交付回网络 / 回报给已注册的 CPU 适配器。
 *       {@link #hasTransferredInputOwnership()} 会变成 {@code true}。</li>
 *   <li><b>仅服务器线程</b>。</li>
 * </ol>
 */
public interface AlloyFurnaceBigIntegerBatch {

    /** @return 已获准的固定份数，恒为正，且在准备与提交之间不会变化 */
    @NotNull BigInteger count();

    /**
     * 提交这批大数合成。
     *
     * @param prototype 必须与 {@link AlloyFurnaceBigIntegerTarget#admit} 传入的是<b>同一个数组对象</b>
     * @return 本机是否接收；{@code false} 表示未消费任何材料，调用方需自行回滚其账本
     * @throws IllegalStateException    该凭据已被提交过
     * @throws IllegalArgumentException 提交时用的原型不是准备时那一份
     */
    boolean commit(KeyCounter @NotNull [] prototype);

    /** @return 是否已经发生过一次成功的提交（即输入所有权已转移给本机） */
    boolean hasTransferredInputOwnership();
}
