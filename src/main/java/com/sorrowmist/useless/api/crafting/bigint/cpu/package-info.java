/**
 * 多方块万象合金炉的 <b>BigInteger 批次</b> 对外公开 API（CPU 侧 SPI）。
 *
 * <h2>这个包解决什么问题</h2>
 *
 * <p>合成 CPU 单次发配的份数可以超过 {@code long}，机器也按该数量产出，但 AE2 的
 * 存储接口单次最多接受 {@code long}，因此产物按 {@code Long.MAX_VALUE} 切段、逐 tick 写回网络。
 * 若 CPU 自行维护 BigInteger 账本，仅凭网络中的分段无法还原精确总数。</p>
 *
 * <p>实现 {@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapter}
 * 并注册，即可收到<b>完整</b>的 BigInteger 产物回执与批次终局通知。</p>
 *
 * <h2>包结构</h2>
 *
 * <table border="1">
 *   <caption>CPU 侧 SPI</caption>
 *   <tr><th>类型</th><th>用途</th></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapter}</td>
 *       <td>你实现的回调接口</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapters}</td>
 *       <td>显式注册表（在 mod init 阶段调用一次）</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding}</td>
 *       <td>提交批次时把你的适配器 id 与自己的上下文绑上去</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchContext}</td>
 *       <td>每次回调都会带上的批次上下文</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchResult}</td>
 *       <td>批次终局状态</td></tr>
 * </table>
 *
 * <h2>完整流程</h2>
 *
 * <pre>{@code
 * // 1) mod 初始化阶段注册一次
 * AlloyFurnaceBigIntegerCpuAdapters.register(new MyCpuAdapter());
 *
 * // 2) 发配时（服务器线程）
 * AlloyFurnaceBigIntegerCpuBinding binding =
 *         new AlloyFurnaceBigIntegerCpuBinding(MyCpuAdapter.ID, myCraftingJobHandle);
 * for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
 *     AlloyFurnaceBigIntegerCapacity capacity = target.capacity(pattern, unitPrototype, requested);
 *     if (!capacity.isAvailable()) {
 *         continue;
 *     }
 *     AlloyFurnaceBigIntegerBatch batch = target.admit(pattern, unitPrototype, capacity.accepted(), binding);
 *     if (batch == null) {
 *         continue;
 *     }
 *     if (batch.commit(unitPrototype)) {
 *         break; // 材料所有权已转移；产物会通过 binding 回调给你
 *     }
 *     // commit 失败：回滚你自己扣掉的那 count-1 份，然后试下一台
 * }
 * }</pre>
 *
 * <h2>通用约束</h2>
 *
 * <ol>
 *   <li><b>回调只在服务器线程发生</b>；实现里不要做跨线程共享的可变状态读写。</li>
 *   <li><b>回调里不要抛异常</b>，也不要再调用 {@code admit}/{@code commit}（会重入）。</li>
 *   <li><b>不要持有机器引用</b>；上下文里刻意没有任何机器对象。</li>
 *   <li><b>存档重载后 {@code cpuToken} 为 {@code null}</b>（它不可序列化）。请用
 *       {@link com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchContext#batchId()}
 *       重新绑定；找不到对应批次就当作「机器仍在替我保管产物」处理。</li>
 *   <li><b>未注册适配器不会引起任何降级问题</b>：产物仍按段写回 ME 网络，仅不发送回调。</li>
 * </ol>
 */
package com.sorrowmist.useless.api.crafting.bigint.cpu;
