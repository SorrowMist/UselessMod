/**
 * 多方块万象合金炉的 <b>BigInteger 批次</b> 对外公开 API（供应器侧契约）。
 *
 * <h2>这个 API 解决什么问题</h2>
 *
 * <p>AE2 原生的 {@code ICraftingProvider#pushPattern} 只能用 {@code long} 表达数量，一次物理提交
 * 最多 {@code Long.MAX / 每次消耗量} 份。想发配真正的大数（例如 {@code 1e22} 个物品）只有两条路：</p>
 *
 * <ol>
 *   <li><b>长版 counted 路径</b>：每 tick 派发一个 {@code long} 窗口，靠多次派发累加到 bigint 总量
 *       —— 接口决定的回退路径，慢且占调度；</li>
 *   <li><b>原生 bigint 路径</b>（本 API）：一次派发就能交付<b>超过 long</b> 的批次。</li>
 * </ol>
 *
 * <p>数据能源（Data Energistics）3.3.0 的 Trinity 数据核心走的是第 2 条，但它依赖自己那套
 * {@code BigIntegerCraftingProviderAdapter}。本 API 把这套能力做成<b>本模组自己的公开契约</b>，
 * 让任意第三方 CPU 不必依赖数据能源也能做 bigint 发配。</p>
 *
 * <h2>包结构</h2>
 *
 * <table border="1">
 *   <caption>供应器侧契约</caption>
 *   <tr><th>类型</th><th>用途</th></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerApi}</td>
 *       <td>发现网格里的可用目标</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget}</td>
 *       <td>一台机器：查容量 / 申请批次</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerCapacity}</td>
 *       <td>容量查询结果</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch}</td>
 *       <td>一次性准入凭据，{@code commit} 后所有权转移</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput}</td>
 *       <td>BigInteger 规模的产物（可以超过 long）</td></tr>
 *   <tr><td>{@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerProvider}</td>
 *       <td>由供应器方块实体实现，供发现流程识别（调用方一般不需要碰）</td></tr>
 * </table>
 *
 * <p>CPU 侧的产物回执通道见 {@link com.sorrowmist.useless.api.crafting.bigint.cpu}。</p>
 *
 * <h2>通用约束</h2>
 *
 * <ol>
 *   <li><b>只在服务器线程调用</b>。所有方法、所有回调都是如此；客户端调用行为未定义。</li>
 *   <li><b>不要缓存内部对象</b>。{@code AlloyFurnaceBigIntegerTarget} 由机器的方块实体持有，
 *       跨 tick 缓存会在机器被拆/卸载后变成野指针。每 tick 重新
 *       {@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerApi#findTargets} 即可。</li>
 *   <li><b>不要反射或强转本模组的方块实体类</b>。判定「是不是大数供应器」请用
 *       {@code instanceof AlloyFurnaceBigIntegerProvider}，方块实体类属于内部实现，随时可能改。</li>
 *   <li><b>原型是「单次推送」的量</b>。整批剩下的 {@code count - 1} 份材料由<b>你自己的账本</b>扣，
 *       提交失败时要回滚。详见 {@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch}。</li>
 *   <li><b>一次提交只能用一个凭据一次</b>。凭据是一次性的，不要复用、不要并发提交。</li>
 *   <li><b>产物会切段写回 ME 网络</b>。因为 AE2 存储接口单次最多 {@code long}，超出部分按
 *       {@code Long.MAX_VALUE} 切段、逐 tick 写入；切段粒度与数据能源自己的约定一致。
 *       若你在 CPU 侧注册了适配器，还会额外收到<b>完整</b>的 BigInteger 产物回执。</li>
 *   <li><b>容量会被动态降频收窄</b>。本模组对每条 bigint 路径都有「每 tick 时间预算」的降频
 *       （思路仿数据能源的提交预算），实测耗时超预算时后续批次容量按比例收窄。用
 *       {@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget#isThrottled()}
 *       可以区分「机器能力就这么多」与「机器正忙」，从而主动延后非紧急批次。
 *       降频不会把容量压到 0（完全无容量会引发调度侧空转），要不要停手由你决定。</li>
 * </ol>
 *
 * <h2>能量闸随线圈档次不同</h2>
 *
 * <p>大数批次的能量计费方式取决于线圈档次，这直接影响容量能有多大：</p>
 *
 * <table border="1">
 *   <caption>能量计费</caption>
 *   <tr><th>线圈</th><th>计费方式</th><th>是否限制份数</th></tr>
 *   <tr><td><b>有用线圈（tier 10）</b></td>
 *       <td>整批<b>一次固定能耗</b> {@code ceil(配方能耗 / 1024)}，与份数无关</td>
 *       <td><b>不限制</b>（没有能量闸）</td></tr>
 *   <tr><td>其它档次</td>
 *       <td>{@code count × 单份能耗}</td>
 *       <td>限制：{@code count ≤ 可用能量 / 单份能耗}</td></tr>
 * </table>
 *
 * <p>所以<b>有用线圈上容量只受材料窗口与产物分段预算限制，能吃到完整 bigint 规模</b>；
 * 低档次线圈会被内部能量缓冲（容量随档次增长，只有满级才是 {@code Long.MAX_VALUE}）压小。
 * {@link com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget#capacity}
 * 会如实反映这一点。</p>
 */
package com.sorrowmist.useless.api.crafting.bigint;
