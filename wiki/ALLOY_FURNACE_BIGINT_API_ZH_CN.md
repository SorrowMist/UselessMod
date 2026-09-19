# 万象合金炉 BigInteger 发配 API

<p>
    <a href="ALLOY_FURNACE_BIGINT_API.md">English</a> |
    <a href="ALLOY_FURNACE_BIGINT_API_ZH_CN.md">简体中文</a>
</p>

本文档面向需要把**超过 `long` 的合成批次**发配给多方块万象合金炉的附属模组。

这些 API 是 Useless Mod 的公开契约，用于**替代**对 `MultiblockAlloyFurnaceCoreBlockEntity`、
`AdvancedAlloyFurnaceAeManager` 等内部类的反射访问。

对应 NeoForge 1.21.1 分支。

## 概述

AE2 原生的 `ICraftingProvider#pushPattern` 只能用 `long` 表达数量，一次物理提交最多
`Long.MAX / 每次消耗量` 份。想发配真正的大数（例如 `1e22` 个物品）只有两条路：

1. **长版 counted 路径**：每 tick 派发一个 `long` 窗口，靠多次派发累加到 bigint 总量 ——
   这是接口决定的回退路径，慢且占调度；
2. **原生 bigint 路径**（本 API）：一次派发就能交付**超过 `long`** 的批次。

本 API 把第 2 条做成公开契约，因此你**不需要**任何第三方调度框架，
也不需要往 AE2 的合成 CPU 逻辑里写 mixin。

> **适用范围**：只对**多方块万象合金炉**（ME 样板总成 + 多方块核心）有效。
> 单方块高级合金炉不支持 bigint 批次，它不会出现在 `findTargets` 的结果里。

## 目录

- [包结构与类型](#包结构与类型)
- [通用约束](#通用约束)
- [1. 发现机器](#1-发现机器)
- [2. 查询容量](#2-查询容量)
- [3. 准入与提交](#3-准入与提交)
- [4. 产物交付（CPU 侧）](#4-产物交付cpu-侧)
- [5. 完整示例](#5-完整示例)
- [常见问题](#常见问题)

## 包结构与类型

| 包 | 用途 |
| --- | --- |
| `com.sorrowmist.useless.api.crafting.bigint` | 供应器侧契约：发现、容量、准入、提交 |
| `com.sorrowmist.useless.api.crafting.bigint.cpu` | CPU 侧 SPI：注册适配器，接收产物与终局回执 |
| `com.sorrowmist.useless.api.crafting.bigint.example` | 参考实现（不参与运行，供照抄） |

| 类型 | 用途 |
| --- | --- |
| `AlloyFurnaceBigIntegerApi` | 发现入口 |
| `AlloyFurnaceBigIntegerTarget` | 一台机器：查容量 / 申请批次 |
| `AlloyFurnaceBigIntegerCapacity` | 容量查询结果 |
| `AlloyFurnaceBigIntegerBatch` | 一次性准入凭据 |
| `AlloyFurnaceBigIntegerOutput` | BigInteger 规模的产物（键 + 精确数量） |
| `AlloyFurnaceBigIntegerProvider` | 供应器侧标记接口（判定「是不是大数供应器」用） |
| `AlloyFurnaceBigIntegerCpuAdapter` | **你实现的回调接口** |
| `AlloyFurnaceBigIntegerCpuAdapters` | 适配器注册表 |
| `AlloyFurnaceBigIntegerCpuBinding` | 把适配器 id 与你的上下文绑到批次上 |
| `AlloyFurnaceBigIntegerBatchContext` | 每次回调都会带上的批次上下文 |
| `AlloyFurnaceBigIntegerBatchResult` | 批次终局状态 |

## 通用约束

1. **只在服务器线程调用**。所有方法、所有回调都是如此。
2. **不要缓存 `AlloyFurnaceBigIntegerTarget`**。它由机器的方块实体持有，跨 tick 缓存会在机器
   被拆 / 卸载后变成野指针。每 tick 重新 `findTargets` 即可（只是网格节点遍历，很轻）。
3. **不要反射或强转本模组的方块实体类**。判定「是不是大数供应器」请用
   `instanceof AlloyFurnaceBigIntegerProvider`。
4. **原型是「单次推送」的量**。整批剩下的 `count - 1` 份材料由**你自己的账本**扣，
   提交失败时要回滚。详见 [第 3 节](#3-准入与提交)。
5. **不要自己往原型里乘 `count`**。次数只通过 `count` 传递。
6. **回调里不要抛异常**，也不要再调用 `admit` / `commit`（会重入）。抛出的异常会被记录并吞掉。

## 1. 发现机器

```java
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerApi;
import com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget;

IGrid grid = /* 你的 CPU 所在网格 */;
for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
    String machine = target.machineIdentity(); // 例：minecraft:overworld@128,64,-256
    // ...
}
```

- `findTargets(IGrid)` 返回网格里**当前可用**的全部目标，已按 `machineIdentity()` 去重
  （同一台物理机器只出现一次）。
- `findTarget(IGrid, String machineIdentity)` 按身份精确查找；适合「我上次用的是哪台」这种场景。
- `acceptedPatternKinds()` 返回机器接受的样板物品 id 集合，可用于在昂贵的解码之前做一次廉价过滤。

## 2. 查询容量

```java
AlloyFurnaceBigIntegerCapacity capacity =
        target.capacity(pattern, unitPrototype, requestedCount);

if (!capacity.isAvailable()) {
    String reason = capacity.statusKey(); // 语言键，可直接显示给玩家；可能为空串
    return;
}
BigInteger accepted = capacity.accepted(); // 可能小于 requestedCount，也可能远超 long
```

**`capacity` 无副作用**，可以每次 tick 都调用。

容量同时受四道闸限制，取最小值：

| 闸 | 含义 |
| --- | --- |
| 配方可用性 | 线圈档次是否够、所需模具是否就位（**仅万象样板**） |
| 材料窗口 | 线程数 N ⇒ 本批最多吃下 N 份「每种材料各 `Long.MAX`」的量。**这是一道配平闸，可由机器配置 `ae_unlimited_bigint_parallelism` 解除**（解除后单批规模只由交付能力决定） |
| 产物交付能力 | 单批规模由机器按**当前交付能力**自动决定，会随时间浮动（见下） |
| 能量 | `count × 单份能耗` 必须付得起（**仅万象样板**） |

**AE2 合成样板只有「材料窗口 + 产物交付能力」两道闸**：它在虚拟 3×3 工作台上装配一次就折叠完，
既没有绑定配方、也不收能量，所以容量通常比万象样板大得多。

### 单批规模是浮动的

机器按**当前的交付能力**决定单批能有多大（产物必须逐段写回 ME 网络，每 tick 能写多少是有限的）。
因此：

- 超大量合成时 `capacity` 会在一个区间里浮动 —— **正常现象**，不要把它当成机器能力的固定值；
- `capacity` **不会因积压而归零**（除非配方失效 / 档次不够 / 缺模具 / 没能量这些硬条件不满足），
  所以你的调度循环不需要「停一拍等队列排空再重启」；
- 想让批次更大，请调大机器配置里的**回网时间预算**（见下节），而不是反复重试。

### 动态降频：`isThrottled()`

机器对每条 bigint 路径都做了「每 tick 时间预算」的动态降频：实测耗时超预算时，
后续批次的容量会被按比例收窄。粒度是**全局**的 —— 所有机器跑在同一个服务端线程上，
多台机器的耗时会累加、共同把降频压下去。

```java
if (target.isThrottled() && !urgent) {
    return; // 机器正忙，延后非紧急批次
}
```

它的用途是让你**区分**两种「容量很小」：

| 情况 | 含义 |
| --- | --- |
| 容量小 + `isThrottled() == false` | 机器能力就这样（材料窗口 / 能量 / 交付能力 / 配置的线程数） |
| 容量小 + `isThrottled() == true` | 机器（或同一服务端线程上的其它机器）正忙 |

**降频不会让容量变成 0**：完全报「无容量」会引发调度侧「无容量 → 重新提交」的空转，
所以降频只收窄批次规模。要不要停手由你决定。

> 产物回网也受同一套降频，但策略不同：准入降频收窄**批次规模**（少收新活），
> 回网降频收窄**每 tick 愿意花的时间**（少投递点已完成的产物）。
> 对你而言的可见结果是：**服务器越忙，大数批次的产物回执到得越晚**，但一定会到。

## 3. 准入与提交

```java
AlloyFurnaceBigIntegerBatch batch =
        target.admit(pattern, unitPrototype, capacity.accepted(), binding);
if (batch == null) {
    return; // 此刻不可接受
}
if (batch.commit(unitPrototype)) {
    // 成功：材料所有权已转移给机器
} else {
    // 失败：机器没消费任何材料。请回滚你自己扣掉的那 (count - 1) 份，再试下一台。
}
```

### 调用契约（务必读完）

1. **只能提交一次**。`commit` 第二次调用抛 `IllegalStateException`。
2. **提交必须用「准备时那一份原型」**：调用 `admit` 时传入的 `KeyCounter[]` 数组对象本身，
   不能复制、不能重建。用别的数组提交抛 `IllegalArgumentException`。
3. **机器只消费手里那一份原型**。`admit` 传入的原型是**单次推送**的材料，不是整批的 `count` 倍。
   整批里剩下的 `count - 1` 份材料由**你自己的 BigInteger 账本**负责扣除，
   并在我们提交失败时回滚。
4. **提交失败不代表材料丢失**：`commit` 返回 `false` 时机器不会清空原型，
   调用方应据此回滚自己的那 `count - 1` 份扣账，并稍后重试或改走别的供应器。
5. **提交返回 `true` 即转移所有权**：此后原型里的材料归机器所有（机器会清空它），
   产物由机器交付（见 [第 4 节](#4-产物交付cpu-侧)）。
6. `admit` **不消费材料、不扣能量**，只做一次快照式检查；真正的检查会在 `commit` 时再跑一遍
   （期间机器状态可能变化，最终以 `commit` 的返回值为准）。

## 4. 产物交付（CPU 侧）

产物有**两条**交付路径。默认走第 1 条；若你实现了适配器并愿意接管，可走第 2 条。

**① 默认 —— 逐段写回网络。** AE2 的存储接口单次最多接受 `long`
（`IMEInventory#insert(AEKey, long, Actionable)`），所以机器把产物按 `Long.MAX_VALUE` 切段、
**逐 tick** 写回 ME 网络。**这条路径下你什么都不用做**，产物照常进入 ME 网络。

**② 批量 —— `claimOutputs`（可选，可跳过逐段写回）。** 见 4.2 节。

### 4.1 默认路径：逐段写回网络

产物照常进入 ME 网络，你的 CPU 从网络里取用即可。这条路径**不需要你做任何事**。

需要留意的只有时序：**大数批次的交付是分 tick 摊开的**（见 4.5），
所以 `1e22` 量级的批次可能要几秒钟才全部入网。

### 4.2 批量路径：`claimOutputs`（可跳过逐段写回）

如果你的 CPU 愿意**直接收下整批**（例如你本来就用 BigInteger 账本记账、不打算让物品先进网络），
实现这个回调即可让机器跳过逐段写回：

```java
@Override
public @NotNull Map<AEKey, BigInteger> claimOutputs(
        @NotNull AlloyFurnaceBigIntegerBatchContext context,
        @NotNull List<AlloyFurnaceBigIntegerOutput> outputs) {
    // outputs 是本趟**准备写回网络**的产物。
    // 返回你收下的量（按 key 计）；机器会把这些量从待交付账本中扣除，只把余额写回网络。
    Map<AEKey, BigInteger> taken = new HashMap<>();
    for (AlloyFurnaceBigIntegerOutput output : outputs) {
        BigInteger accept = decideHowMuchToTake(output);   // 你的策略；可以只收一部分
        if (accept.signum() > 0) {
            taken.put(output.what(), accept);
        }
    }
    return taken;
}
```

**调用时机**：产物写回网络**之前**，按**键**调用。默认实现返回空 Map ⇒
行为与不实现该方法**完全一致**（全部写回网络）。

**对两类样板都适用**：万象样板与 AE2 合成样板走的是同一条产物队列、同一套交付逻辑，
只要 `admit` 时绑定了适配器，两者都会触发本回调。回调里的 `key` 是**实际键**
（万象样板的动态产物在折叠时才会解析出真实键），直接按它记账即可。

> **⚠️ 接收即接管。** 收下的物品**不会进入 ME 网络**，只存在于你自己的账本里。因此：
> - 你必须负责它们后续的去向（自行落网、或作为中间产物消耗）；
> - **机器无法退还** —— 账本在调用返回时已被扣减。若批次随后被取消
>   （`onBatchFinished` 收到 `CANCELLED`），已收下的部分需由你自行处理；
> - 实现必须**不抛异常**；真抛了机器会记录并退回逐段写回（不会中断，但可能造成归属不一致）。

**触发条件**：只有当本批是通过 `admit(..., cpuBinding)` **绑定了适配器**、
且整趟写回绑定的都是同一个适配器时才会调用。多 CPU 混绑同一台机器时不会调用（退回逐段写回）。

### 4.3 实现适配器

```java
public final class MyCpuAdapter implements AlloyFurnaceBigIntegerCpuAdapter {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("mymod", "bigint_cpu");

    @Override public ResourceLocation id() { return ID; }

    @Override
    public void onBatchAdmitted(AlloyFurnaceBigIntegerBatchContext context) {
        // 材料所有权已转移。把「本批计划产出」记进自己的账本 / 让任务进入等待状态。
    }

    @Override
    public void onBatchOutputs(AlloyFurnaceBigIntegerBatchContext context,
                               List<AlloyFurnaceBigIntegerOutput> actualOutputs) {
        // 产物已全部交付完成。actualOutputs 是 BigInteger 精确值 ——
        // 对动态产物来说，这里的键才是真正的键。
        // 注意：若你实现了 claimOutputs 并收下了全部产物，本回调依然会到（作为「本批交付完成」的信号），
        //      但那时物品在你的账本里，不要把 actualOutputs 再加一遍。
    }

    @Override
    public void onBatchFinished(AlloyFurnaceBigIntegerBatchContext context,
                                AlloyFurnaceBigIntegerBatchResult result) {
        // SUCCESS / CANCELLED / REJECTED
    }
}
```

### 4.4 注册

在你自己 mod 的公共初始化阶段注册一次：

```java
@SubscribeEvent
public static void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> AlloyFurnaceBigIntegerCpuAdapters.register(new MyCpuAdapter()));
}
```

注册表是全局的、进程级的，不随存档或世界重载清空。若你的 mod 支持运行期禁用，
用 `AlloyFurnaceBigIntegerCpuAdapters.unregister(id)` 摘掉。

### 4.5 回调时序

| 时机 | 回调 |
| --- | --- |
| `commit` 成功、同一 tick | `onBatchAdmitted` |
| 产物**全部**交付完成之后 | `onBatchOutputs`，紧接着 `onBatchFinished(SUCCESS)` |
| 机器被拆 / 玩家取消 / 任务中止 | 只调用 `onBatchFinished(CANCELLED)` |

**一个批次只会有一个终局回调**（`SUCCESS` 或 `CANCELLED`，二选一）。取消时即使产物在同一 tick 内
被强制刷空，也不会再补一个 `SUCCESS`。

> **大数批次的交付是分 tick 摊开的。** 单台机器每 tick 的写回工作量有上限，
> 所以 `1e22` 量级的批次可能要几秒钟才全部入网，`onBatchOutputs` /
> `onBatchFinished(SUCCESS)` 也就相应晚到。**这是刻意的** —— 避免一次巨量写回把服务端线程拖住。
> 若你的任务对交付延迟敏感，请按「回调会晚到但一定到」设计，不要在发出批次后设过短的超时。

**取消时产物不会丢**：机器会把队列里剩余的产物强制写回 ME 网络，只是不再走逐 tick 节奏。
收到 `CANCELLED` 时请按「这批已由机器交付，但我没拿到逐条回执」处理，**不要**补记产物。

### 4.6 存档重载

`cpuToken` 不可序列化，重载后为 `null`；机器的产物队列本身照常持久化（**不会丢物品**），
但**不再补发回调**。请以 ME 网络的实际入账为准，或按 `batchId()` 自行对账。

### 4.7 没有适配器时会怎样

完全没有降级风险：不注册适配器（或 `admit` 时传 `cpu = null`）时，产物依旧照常逐段写回
ME 网络，只是不发这些回调、也不会调用 `claimOutputs`。

## 5. 完整示例

`com.sorrowmist.useless.api.crafting.bigint.example.ExampleBigIntegerCpuAdapter`
是一个可直接照抄的参考实现，包含完整的发现 → 容量 → 准入 → 提交 → 产物交付流程。
其发配入口简化后是这样：

```java
public static BigInteger dispatch(IGrid grid, IPatternDetails pattern,
                                  KeyCounter[] unitPrototype, BigInteger requested,
                                  Object jobHandle, boolean urgent) {
    AlloyFurnaceBigIntegerCpuBinding binding =
            new AlloyFurnaceBigIntegerCpuBinding(MyCpuAdapter.ID, jobHandle);

    for (AlloyFurnaceBigIntegerTarget target : AlloyFurnaceBigIntegerApi.findTargets(grid)) {
        // 机器正忙时按需延后非紧急批次（降频不会把容量压到 0，停不停手由你决定）。
        if (target.isThrottled() && !urgent) {
            continue;
        }
        AlloyFurnaceBigIntegerCapacity capacity = target.capacity(pattern, unitPrototype, requested);
        if (!capacity.isAvailable()) {
            continue;
        }
        AlloyFurnaceBigIntegerBatch batch =
                target.admit(pattern, unitPrototype, capacity.accepted(), binding);
        if (batch == null) {
            continue;
        }
        if (batch.commit(unitPrototype)) {
            return batch.count(); // 材料所有权已转移
        }
        // commit 失败：回滚你自己扣掉的 (count - 1) 份，然后试下一台
    }
    return null;
}
```

## 常见问题

### 为什么容量比 `long` 小得多？

先看是不是**能量闸**。能量的计费方式**随线圈档次不同**：

| 线圈 | 计费方式 | 是否限制份数 |
| --- | --- | --- |
| **有用线圈（tier 10）** | 整批**一次固定能耗** `ceil(配方能耗 / 1024)`，**与份数无关** | **不限制** —— 没有能量闸 |
| 其它档次 | `count × 单份能耗` | 限制：`count ≤ 可用能量 / 单份能耗` |

也就是说：**有用线圈上容量只受「材料窗口 + 交付能力」限制，能吃到完整 bigint 规模**；
低档次线圈则会被内部能量缓冲（容量随档次增长，只有满级才是 `Long.MAX_VALUE`）压小。

其余可能压小容量的原因：线圈档次不够、缺模具中枢或模具、正在动态降频
（用 `isThrottled()` 区分最后一种）。

### 线圈并行上百万时，容量为什么也被限制？

因为**产物必须逐段写回 ME 网络**（AE2 存储接口单次只能收 `long`），
而回网每 tick 只能插那么多次 —— **交付能力不随线程数增长**。

所以单批规模由「回网时间预算 ÷ 单次插入成本」决定，而不是由线程数决定。
**你的百万线程仍然决定材料窗口**（每线程一个 long 窗口），只是不再决定单批的交付规模。

想加大单批规模，请调大机器配置里的**回网时间预算**；调大后会同步加大每 tick 的写回耗时。

### 机器为什么返回 `waiting_mold_hub` / `waiting_tier`？

`statusKey` 是机器配方可用性检查给出的**完整语言键**（例如
`gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_tier`），
用 `Component.translatable(statusKey)` 就能显示。常见取值：

| 语言键后缀 | 含义 |
| --- | --- |
| `waiting_structure` | 多方块结构未成形 |
| `waiting_recipe` | 样板绑定的配方已失效（配方被改 / 删） |
| `waiting_tier` | 线圈档次不够 |
| `waiting_mold_hub` | 没有模具中枢 |
| `waiting_missing_mold` | 模具中枢里缺本次配方需要的模具 |

### 提交返回 `false` 后要不要重试？

可以，但要先把你自己扣掉的那 `count - 1` 份回滚。返回 `false` 的常见原因是：
能量刚好被别人用掉、机器在这一瞬间被拆或未成形。

### 万象样板的大数批次怎么执行？

**折叠**语义：机器解析一次绑定配方，把产物 ×`count`（用 BigInteger 记账），再逐段写回网络；
能量按 `count × 单份能耗` 扣。它不会真的逐份跑 `count` 次加工。

### 我可以只发配 AE2 合成样板吗？

可以。AE2 合成样板同样支持 bigint 批次，而且**不收能量**（机器在虚拟 3×3 工作台上装配一次
再整体放大）。
