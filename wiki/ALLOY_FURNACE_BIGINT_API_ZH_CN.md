# 万象合金炉 BigInteger 发配 API 使用指南

<p>
    <a href="ALLOY_FURNACE_BIGINT_API.md">English</a> |
    <a href="ALLOY_FURNACE_BIGINT_API_ZH_CN.md">简体中文</a>
</p>

本文档面向需要把**超过 `long` 的合成批次**发配给多方块万象合金炉的附属模组。
这些 API 用于替代对 `MultiblockAlloyFurnaceCoreBlockEntity`、`AdvancedAlloyFurnaceAeManager`
等内部类的反射访问。

本文档对应 NeoForge 1.21.1 分支的当前源码。

## 概述

AE2 原生的 `ICraftingProvider#pushPattern` 只能用 `long` 表达数量，一次物理提交最多
`Long.MAX / 每次消耗量` 份。想发配真正的大数（例如 `1e22` 个物品）只有两条路：

1. **长版 counted 路径**：每 tick 派发一个 `long` 窗口，靠多次派发累加到 bigint 总量 ——
   这是接口决定的回退路径，慢且占调度；
2. **原生 bigint 路径**（本 API）：一次派发就能交付**超过 `long`** 的批次。

本 API 把第 2 条做成 Useless Mod 自己的公开契约，因此你**不需要**依赖数据能源
（Data Energistics），也不需要往 AE2 的合成 CPU 逻辑里写 mixin。

> **范围**：只对**多方块万象合金炉**（ME 样板总成 + 多方块核心）有效。
> 单方块高级合金炉不支持 bigint 批次，它在 `findTargets` 里不会出现。

## 目录

- [包结构](#包结构)
- [通用约束](#通用约束)
- [1. 发现机器](#1-发现机器)
- [2. 查询容量](#2-查询容量)
- [3. 准入与提交](#3-准入与提交)
- [4. 接收产物回执（CPU 侧）](#4-接收产物回执cpu-侧)
- [5. 完整示例](#5-完整示例)
- [常见问题](#常见问题)
- [与数据能源的关系](#与数据能源的关系)

## 包结构

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
| `AlloyFurnaceBigIntegerOutput` | BigInteger 规模的产物 |
| `AlloyFurnaceBigIntegerCpuAdapter` | 你实现的回调接口 |
| `AlloyFurnaceBigIntegerCpuAdapters` | 显式注册表 |
| `AlloyFurnaceBigIntegerCpuBinding` | 把适配器 id 与你的上下文绑到批次上 |
| `AlloyFurnaceBigIntegerBatchContext` | 每次回调都会带上的批次上下文 |
| `AlloyFurnaceBigIntegerBatchResult` | 批次终局状态 |

## 通用约束

1. **只在服务器线程调用**。所有方法、所有回调都是如此。
2. **不要缓存 `AlloyFurnaceBigIntegerTarget`**。它由机器的方块实体持有，跨 tick 缓存会在机器
   被拆/卸载后变成野指针。每 tick 重新 `findTargets` 即可（只是网格节点遍历，很轻）。
3. **不要反射或强转本模组的方块实体类**。判定「是不是大数供应器」请用
   `instanceof AlloyFurnaceBigIntegerProvider`。
4. **原型是「单次推送」的量**。整批剩下的 `count - 1` 份材料由**你自己的账本**扣，
   提交失败时要回滚。详见 [第 3 节](#3-准入与提交)。
5. **不要自己往原型里乘 `count`**。次数只通过 `count` 传递。
6. **回调里不要抛异常**，也不要再调用 `admit`/`commit`（会重入）。抛出的异常会被记录并吞掉。

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

> **实现细节**：AE2 19.2.17 的 `ICraftingService` 没有「枚举全部供应器」的公开接口
> （只有 `getCraftingFor(AEKey)` 与 `getCpus()`），所以本 API 走 `IGrid#getNodes()` +
> `IGridNode#getOwner()`。

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

容量同时受四道闸限制，取最小值：

| 闸 | 含义 |
| --- | --- |
| 配方可用性 | 线圈档次是否够、所需模具是否就位（**仅万象样板**） |
| 材料窗口 | 线程数 N ⇒ 本批最多吃下 N 份「每种材料各 `Long.MAX`」的量 |
| 产物分段预算 | **由「回网时间预算 ÷ 实测单次插入成本」前馈算出**（不再有固定上限）—— 单批规模自动跟随真实交付能力 |
| **能量** | `count × 单份能耗` 必须付得起（**仅万象样板**） |

**AE2 合成样板只有「材料窗口 + 产物分段预算」两道闸**：它在虚拟 3×3 工作台上装配一次就折叠完，
既没有绑定配方、也不收能量，所以容量通常比万象样板大得多。

### 动态降频：`isThrottled()`

本模组对每条 bigint 路径都做了「每 tick 时间预算」的动态降频（思路仿数据能源的提交预算）：
实测耗时超预算时，后续批次的容量会被按比例收窄。粒度是**全局**的 —— 所有机器跑在同一个服务端
线程上，多台机器的耗时会累加、共同把降频压下去。

```java
if (target.isThrottled() && !urgent) {
    return; // 机器正忙，延后非紧急批次
}
```

它的用途是让你**区分**两种"容量很小"：

| 情况 | 含义 |
| --- | --- |
| 容量小 + `isThrottled() == false` | 机器能力就这样（材料窗口 / 能量 / 产物分段 / 配置的线程数） |
| 容量小 + `isThrottled() == true` | 机器（或同一服务端线程上的其它机器）正忙 |

**降频不会让容量变成 0**：完全报「无容量」会引发调度侧「无容量 → 重新提交」的空转，所以降频只
收窄批次规模。要不要停手由你决定。

> **产物回网也受同一套动态降频，但策略不同。** 准入路径降频是收窄**批次规模**（少收新活）；
> 回网路径降频是收窄**每 tick 愿意花的时间**（少投递点已完成的产物）。两者的下限也不同：
> 准入**必须**至少收 1 份（否则触发上述空转），而回网可以真截断到很小（幂等，余额留队列下 tick
> 继续）—— 但仍保留下限，避免持续重载下把队列饿死、进而让背压把准入也压到 0。
> 对你而言的可见结果就是：**服务器越忙，大数批次的 `onBatchOutputs` 到得越晚**，但一定会到。

**`capacity` 无副作用**，可以每次 tick 都调用。

### 单批规模由机器自适应（AIMD），容量不会因积压归零

机器用一套**拥塞控制式**的自适应控制器决定**单批规模**（用产物分段数衡量）：

| 阶段 | 行为 |
| --- | --- |
| 慢启动 | 起步很小（上限的 1/100）；积压未超一批时每 tick ×10，快速探到可用上限 |
| 乘法减小 | 一旦积压超过「一批在飞」就减半，并退出慢启动 |
| 线性回升 | 之后积压回落时逐步缓升，避免「一有空间就暴涨」再次冲垮 |

收敛点是「**一批大约一两个 tick 交付完**」—— 那是最平滑的形态。

**这对你的意义**：
- `capacity` **永远为正**（除非配方失效 / 档次不够 / 缺模具 / 没能量这些硬条件不满足），
  所以调度侧不会停一拍再重启；
- 超大量合成时 `capacity` 会在一个区间里浮动 —— 那是机器在探测合适的批次大小，**正常现象**；
- **不要**把 `capacity` 当成机器能力的固定值。

**为什么不用固定值**：单批该多大取决于「交付能力」与「调度侧派发频率」，两者都无法在编译期算准 ——
固定值要么过大（首批交付几十秒 ⇒ 任务之间停顿），要么过小（吞吐浪费）。控制器用实测反馈收敛。

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
   整批里剩下的 `count - 1` 份材料由**你自己的 BigInteger 账本**负责扣除。
   数据能源的 Trinity 数据核心就是这么做的：它用 `TrinityExactInputTransaction` 扣账，
   并在我们提交失败时回滚。
4. **提交失败不代表材料丢失**：`commit` 返回 `false` 时机器不会清空原型，调用方应据此回滚它
   自己的那 `count - 1` 份扣账，并稍后重试或改走别的供应器。
5. **提交返回 `true` 即转移所有权**：此后原型里的材料归机器所有（机器会清空它），
   产物由机器按 `AlloyFurnaceBigIntegerOutput` 交付回网络 / 回报给已注册的适配器。
6. `admit` **不消费材料、不扣能量**，只做一次快照式检查；真正的检查会在 `commit` 时再跑一遍
   （期间机器状态可能变化，最终以 `commit` 的返回值为准）。

### 产物怎么回网

AE2 的存储接口单次最多接受 `long`，所以产物是按 `Long.MAX_VALUE` 切段、**逐 tick** 写回
ME 网络的。切段粒度与数据能源自己的 `PHYSICAL_CHUNK` 约定一致。

如果你在 CPU 侧注册了适配器，还会额外收到**完整**的 BigInteger 产物回执（见下一节）。

## 4. 接收产物回执（CPU 侧）

### 4.1 实现适配器

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
        // 产物已全部回网。actualOutputs 是 BigInteger 精确值 ——
        // 对动态产物来说，这里的键才是真正的键。
    }

    @Override
    public void onBatchFinished(AlloyFurnaceBigIntegerBatchContext context,
                                AlloyFurnaceBigIntegerBatchResult result) {
        // SUCCESS / CANCELLED / REJECTED
    }
}
```

### 4.2 注册

在你自己 mod 的公共初始化阶段注册一次：

```java
@SubscribeEvent
public static void onCommonSetup(FMLCommonSetupEvent event) {
    event.enqueueWork(() -> AlloyFurnaceBigIntegerCpuAdapters.register(new MyCpuAdapter()));
}
```

注册表是全局的、进程级的，不随存档或世界重载清空。若你的 mod 支持运行期禁用，
用 `AlloyFurnaceBigIntegerCpuAdapters.unregister(id)` 摘掉。

### 4.3 回调时序

| 时机 | 回调 |
| --- | --- |
| `commit` 成功、同一 tick | `onBatchAdmitted` |
| 产物**全部**写回网络之后 | `onBatchOutputs`，紧接着 `onBatchFinished(SUCCESS)` |
| 机器被拆 / 玩家取消 / 任务中止 | 只调用 `onBatchFinished(CANCELLED)` |

**一个批次只会有一个终局回调**（`SUCCESS` 或 `CANCELLED`，二选一）。取消时即使产物在同一 tick 内
被强制刷空，也不会再补一个 `SUCCESS`。

> **大数批次的回网是分 tick 摊开的。** 产物按 key 聚合并按 `Long.MAX_VALUE` 切段写回网络，单台机器
> 每 tick 的写回工作量有上限（预算闸 + 单键分段上限），所以 `1e22` 量级的批次可能要几秒钟才全部入网，
> `onBatchOutputs` / `onBatchFinished(SUCCESS)` 也就相应晚到。**这是刻意的** —— 避免一次巨量写回把
> 服务端线程拖住。若你的任务对交付延迟敏感，请按「回调会晚到但一定到」设计，不要在发出批次后
> 设过短的超时。

**取消时产物不会丢**：机器会把队列里剩余的产物强制写回 ME 网络，只是不再走逐 tick 节奏。
收到 `CANCELLED` 时请按「这批已由机器交付，但我没拿到逐条回执」处理，**不要**补记产物。

### 4.4 存档重载

`cpuToken` 不可序列化，重载后为 `null`；机器的产物队列本身照常持久化（**不会丢物品**），
但**不再补发回调**。请以 ME 网络的实际入账为准，或按 `batchId()` 自行对账。

### 4.5 没有适配器时会怎样

完全没有降级风险：不注册适配器（或 `admit` 时传 `cpu = null`）时，产物依旧照常切段写回
ME 网络，只是不发这些回调。

## 5. 完整示例

`com.sorrowmist.useless.api.crafting.bigint.example.ExampleBigIntegerCpuAdapter`
是一个可直接照抄的参考实现，包含完整的发现 → 容量 → 准入 → 提交 → 产物回执流程。
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

也就是说：**有用线圈上容量只受「材料窗口 + 产物分段预算」限制，能吃到完整 bigint 规模**；
低档次线圈则会被内部能量缓冲（容量随档次增长，只有满级才是 `Long.MAX_VALUE`）压小。

其余可能压小容量的原因：线圈档次不够、缺模具中枢或模具、回网队列被背压占满、正在动态降频
（用 `isThrottled()` 区分最后一种）。

## 附：两条 bigint 调度路径的差异（实测对比笔记）

本机的大数能力目前只实现了**数据能源（Data Energistics）**的调度 API
（`BigIntegerCraftingProviderAdapter` 等，见 `integration/dataenergistics/`）。
其它模组的 BigInteger 合成 CPU 走的是各自的通道，行为差异很大 —— 记录如下，供选型参考。

### OmniSequence: Transfinite（`molecularmanipulator`）为什么快得多

拆包对比后有三个机制性差异：

| 机制 | 数据能源 Trinity | OmniSequence 超限算枢 |
| --- | --- | --- |
| **CPU 车道** | 每个 CPU 每 tick 派发一次 | **一个方块虚拟出多个 AE2 CPU**，自建 `dispatchLaneRotation` / `dispatchLaneAllowances` 轮转分配工作单元 ⇒ 同一 tick 内多次派发 |
| **过载策略** | `MeasuredCraftingServerDispatchBudget(50ms, 1ms)` —— **测量真实 tick 时长，超过 50ms 就砍到 1ms/tick**，合成近乎停摆 | compat 窗口只设 45ms 目标 + 250µs 下限，**不做过载降速** |
| **AE2 侧上限** | 常规 | `INFINITE_STORAGE` / `INFINITE_PARALLELISM = Long.MAX_VALUE`，永不受存储/并行度限制 |

**结论**：服务端越卡，数据能源缩得越狠、OmniSequence 照跑。所以「超限算枢比三位一体快很多」主要不是算得快，
而是**它不肯降速**，以及**一 tick 内派发更多次**。

### 实测基线（2026-09-19，超限算枢 + 200ms 回网预算）

**删除「单批段数天花板」常量前后的对比**（这是本模块第 8 次「固定上限变成隐藏瓶颈」）：

| 指标 | 删天花板前 | 删天花板后 |
| --- | --- | --- |
| 单批最大 | 3.9999e+24 | **3.1999e+25**（8.0 倍） |
| 吞吐 | 3.80e26 次/tick | **2.95e27 次/tick**（7.8 倍） |
| `segmentBudget` | 恒等于 131072（上限） | **浮动 63k / 109k / 123k** ✅ 不再被常量钉住 |
| 派发频率 | 832~893 批次/tick | 876~920 批次/tick |
| 本机降频系数 | 1.000 | 1.000（实测 4.8~5.4ms / 45ms，仍有余量） |

**真实单次插入成本 = `insertUs ÷ insertCalls` ≈ 2.6 µs/次**
（此前记的 0.36µs 是**被逐段 `System.nanoTime()` 埋点污染的假数**，已修）。
单 tick 27,110 次插入 ≈ 70ms。

**当前剩余瓶颈（按优先级）**：
1. **插入成本 2.6µs/次偏高** —— 单 tick 27k 次就是 70ms；
2. **批量通道（claim）不可用 —— 但这<b>不是限制、也不是缺陷</b>**：
   实测 `insertCalls ≈ deliveredChunks`（27111 vs 27110），说明产物走的是**正常的 AE2 存储插入路径**。
   原因见下方「认领链路」—— claim 只对 neoecoae 的合成 CPU 生效；用其它 CPU 时它<b>正确地返回 0</b>，
   产物照常经 `insertRaw`（= `storage.insert`）落网。**这是正常路径，不是降级**，
   兼容层也没有因此限制任何东西（无 ECO 任务时扫描会 `ecoStates.isEmpty()` 早退，开销≈0）；

3. `measuredInsertNanosPerChunk` 因「降档限 1%」的上升限速而估值偏低
   （约 811ns vs 真实 2.6µs）⇒ 批次偏大。当前 `pendingChunks=0` 所以无害，
   但若出现积压，应放宽上升限速。

### 新增：落网前的「整批接收」通道（`claimOutputs`）

这是**唯一能跳过逐段插入**的公开通道。CPU 适配器实现它即可直接收下整批产物，
机器只把余额写回网络：

```java
// AlloyFurnaceBigIntegerCpuAdapter（新增，默认返回空 Map ⇒ 行为零变化）
default @NotNull Map<AEKey, BigInteger> claimOutputs(
        @NotNull AlloyFurnaceBigIntegerBatchContext context,
        @NotNull List<AlloyFurnaceBigIntegerOutput> outputs) {
    return Map.of();
}
```

**调用时机**：产物写回网络**之前**，按键调用；返回量从待回网账本扣除，余额才逐段插入。

**⚠️ 接收即接管**：收下的物品**不进 ME 网络**，只存在于你的账本 ⇒
你必须负责其后续去向；**机器无法退还**（账本在返回时已扣减），批次被取消时已收部分由你自行处理。
实现必须不抛异常。

**触发条件**：只有当本批是通过 `admit(..., cpuBinding)` **绑定了适配器**、
且整趟刷写绑定的都是同一个适配器时才会调用（多 CPU 混绑时退回逐段写回）。

### 认领（claim）链路：认领量由 **neoecoae** 决定

```
GridOutputTarget.claim(key, amount)
  → NeoEcoDynamicOutputCompat.claim(grid, key, amount)              本机兼容层
  → DynamicPatternCpuStateManager.ecoCandidates(grid, itemId)        遍历 neoecoae 的 ECO CPU
  → cn.dancingsnow.neoecoae 的 ECOCraftingOutputClaimSink.claimCraftingOutput(request)
     → claimedAmount()                                               ★ 认领量在此决定
```

- `GridOutputTarget.supportsClaimHoisting()` = `ModList.get().isLoaded("neoecoae")` ——
  **它只表示"模组已加载"，语义是「把认领扫描从每段一次提到每键一次」这个<b>优化</b>是否可用**，
  与"有没有可认领的东西"无关。所以它返回 true 时不会限制任何东西。
- **只有 crafter 是 neoecoae 的合成 CPU 时才找得到候选**；用其它 CPU（如超限算枢）时
  `ecoCandidates` 为空 ⇒ `claim` **正确地**返回 0 ⇒ 产物走正常的 `insertRaw`（`storage.insert`）。
- 也就是说：**这条路径上没有"该修而没修的 bug"**。逐段插入是 AE2 `long` 接口的固有代价，
  想绕开它只能由 CPU 侧提供「插入前按 BigInteger 整批接收」的通道（neoecoae 的 claim 就是这种通道）。
- **注意区分**：超限算枢的「直取 / direct return」是**另一套机制**（插入之后再反射借记本机账本），
  **不能省掉插入**。二者名字相近但无关。

**想减少逐段插入的三条路**：① 改用 neoecoae 的合成 CPU（认领链路才生效）；
② 让第三方 CPU 把「直取」提到插入之前（真正跳过插入）；③ 降低本机插入成本。



| 指标 | 实测值 | 备注 |
| --- | --- | --- |
| 派发频率 | **832~893 批次/tick** | 数据能源约 0.85 批次/tick ⇒ 高约 **1000 倍**（快的根本原因） |
| 上报吞吐 | 1.52e28 / 40 tick = **3.8e26 次/tick** | |
| 单批上限 | **3.9999e+24** | 正好等于超限算枢的 `outputWindow=4e24` |
| 本机降频系数 | **1.000**（实测 5~14ms / 预算 45ms） | **本机限流未被触发** |
| 积压 | `pendingChunks=0`、`oldestAgeTicks=0` | 零积压、零停顿 |
| 直取开销 | `elapsedUs=22~27`（处理 2.25e24 物品） | 纯账本运算，几乎免费 |
| 单段插入成本 | **0.36~0.45 µs/段** | |
| 单 tick 插入量 | 最多 **241,235 段 = 87ms** | ⇒ 一个 tick ≥87ms ⇒ **TPS 约 11** |

**⚠️ 代价**：配置的 200ms 回网预算使单 tick 插入耗时达 87ms，TPS 掉到约 11。
且 `throttleScale≈0.52` 说明回网自身一 tick 就撑爆 50ms 统计窗口，下一 tick 预算被砍半。
由于**本机降频系数是 1.000**（限流未被触发），**降低该配置不会损失吞吐**，但会显著改善 TPS。

**⚠️ 待核对**：上报合成量 3.8e26/tick 对应产物分段约 1.77e8/tick，
而实测交付仅约 2.4e5/tick —— **差约 700 倍**。需用 GUI 产出速率或网络物品实际增长核对
（也可能是诊断日志被限流为每 20 tick 一条、采样不到真实平均）。

### 可用的机会：原生 BigInteger 输出通道

OmniSequence 还提供了一套**原生 BigInteger 通道**，产物可以一次交付：

```java
interface OmniBigIntegerOutputReceiver {
    BigInteger transferOutput(IGrid, AEKey, BigInteger, Consumer<BigInteger>);
}
interface OmniBigIntegerBatchCallbacks {
    void onOutputs(List<OmniBigIntegerOutput>);   // (键, BigInteger) 列表 —— 一次交付
}
interface OmniBigIntegerCraftingProvider extends ICraftingProvider {
    BigInteger getMaximumBigIntegerCrafts(IPatternDetails, KeyCounter[], BigInteger);
    boolean pushBigIntegerCraftingPattern(IPatternDetails, BigInteger, KeyCounter[]);
    default boolean usesNativeBigIntegerBatch();
}
```

**本机确实已经有对应的公开 API**（`com.sorrowmist.useless.api.crafting.bigint`：
`AlloyFurnaceBigIntegerTarget` / `AlloyFurnaceBigIntegerBatch` / `AlloyFurnaceBigIntegerOutput` /
`cpu.AlloyFurnaceBigIntegerCpuAdapter` 等），OmniSequence 就是通过它接入的 ——
其 jar 内含 `integration/useless/UselessBigIntegerApiBridge`，日志会打
`Registered UselessMod public BigInteger API bridge with same-tick direct return`。

**但要分清两层，别混淆**：

| 层 | 语义 | 是否绕开 AE2 的 `long` 上限 |
| --- | --- | --- |
| **公开 API 的 `onBatchOutputs(ctx, List<AlloyFurnaceBigIntegerOutput>)`** | 产物**已全部写回网络之后**，把精确 BigInteger 总数**通知**给 CPU 供其核账 | **不绕开** —— 产物仍逐段插入网络 |
| **`UselessExactOutputReturn`（反射直取内部）** | 反射读取本机队列条目的账本字段并**直接借记**，把整批收进自己的 BigInteger 账本 | **绕开** —— 完全不经过 `IMEInventory.insert` |

⇒ 后者才是「超限算枢快得多」的根本原因：**1e24 个物品一次交付，而不是 1e6 次分段插入**。
物品此后留在 CPU 自己的账本里；等到需要暴露给 AE2 存储（玩家取出等）时 `long` 上限仍然适用，
但那已不在合成热路径上。

> ⚠️ **维护提醒：这 16 个名字是事实上的 ABI，改动会静默打断外部集成。**
>
> OmniSequence 的 `UselessExactOutputReturn$Layout` 用反射按**具体名字**读取本机内部。
> 已逐个核对（当前代码全部存在）：
>
> | 类别 | 名字 |
> | --- | --- |
> | 字段 | `queuedCraftingOutputs`、`owner`、`pendingOutputAmount`、`queuedTick`、`cpuNotified`、`cpuContext`、`ledger`、`amounts` |
> | 方法 | `getLevel`、`getAeGrid`、`resolveAeOutputTarget`、`supportsBigIntegerRecipeBatches`、`markChanged`、`cpuToken`、`notifyCpuBatchCompleted`、`addWork` |
>
> 任何一个改名，该层就会静默失效（日志出现 `UselessMod public BigInteger API is unavailable`，
> 合成仍能跑，但会退回逐段写回网络，吞吐大幅下降）。
> **重命名这些成员前必须先与 OmniSequence 侧对齐**；改完建议在游戏里确认日志出现
> `Registered UselessMod public BigInteger API bridge with same-tick direct return`。

### 线圈并行上百万时，容量为什么也被限制？

因为**产物分段预算由交付能力前馈算出**。分段的物理来源是「AE2 存储接口单次只能收 `long`」，所以
1e24 个物品必然要切成约 100 万段；而回网每 tick 只能插那么多次 —— **交付能力不随线程数增长**。

所以单批规模 ≈ `回网时间预算 ÷ 实测单次插入成本 ÷ 2`（半个 tick 的可交付量）。
**该处曾长期设固定上限（16 → 1024 → 8192 → 20480 → 131072 → 524288），每次都被实测证明成了隐藏瓶颈**
（`segmentBudget` 恒等于那个常量）；现已彻底删除固定上限，天花板只保留「本机线程数」这个物理量。
若不限制，线圈并行上百万时单批会产生百万段，
交付要几十秒，表现为「每个任务之间一段停顿」，而且首批过大之后按积压降频也追不上积压速度。

**这不会降低总吞吐**：总段数不变，只是把一大批拆成若干能及时交付的小批。你的百万线程仍然
决定**材料窗口**（每线程一个 long 窗口），只是不再决定单批的**交付规模**。

### 机器为什么返回 `waiting_mold_hub` / `waiting_tier`？

`statusKey` 是机器配方可用性检查给出的**完整语言键**（例如
`gui.useless_mod.advanced_alloy_furnace.ae_task_status.waiting_tier`），
用 `Component.translatable(statusKey)` 就能显示。常见取值：

| 语言键后缀 | 含义 |
| --- | --- |
| `waiting_structure` | 多方块结构未成形 |
| `waiting_recipe` | 样板绑定的配方已失效（配方被改/删） |
| `waiting_tier` | 线圈档次不够 |
| `waiting_mold_hub` | 没有模具中枢 |
| `waiting_missing_mold` | 模具中枢里缺本次配方需要的模具 |

### 提交返回 `false` 后要不要重试？

可以，但要先把你自己扣掉的那 `count - 1` 份回滚。返回 `false` 的常见原因是：能量刚好被别人用掉、
回网队列满了（背压）、机器在这一瞬间被拆或未成形。

### 万象样板的大数批次怎么执行？

**折叠**语义：机器解析一次绑定配方，把产物 ×`count`（用 BigInteger 记账），再按 `Long.MAX_VALUE`
切段写回网络；能量按 `count × 单份能耗` 扣。它不会真的逐份跑 `count` 次加工。

### 我可以只发配 AE2 合成样板吗？

可以。AE2 合成样板同样支持 bigint 批次，而且**不收能量**（机器在虚拟 3×3 工作台上装配一次
再整体放大）。

## 与数据能源的关系

数据能源（Data Energistics）3.3.0 的 Trinity 数据核心走的是它自己那套
`BigIntegerCraftingProviderAdapter`；本模组的 DE 适配器与本文档描述的公开 API **共用同一套
容量与能量判定**，所以两条入口对同一台机器算出的结果一致。

如果你同时使用数据能源，不需要做任何额外工作 —— 两条路径互不干扰：

- DE 的 exact 分支要求「目标已被异步提案排他预留」，只在满足条件时触发；
- 本 API 是你主动调用，与 DE 的调度无关。

> 本模组只给**真正实现了 bigint 语义**的样板发布 machine identity。因为 DE 的 exact 分支一旦
> 接管某个供应器就**不会再回落**长版路径，所以「本机此刻吃不下」必须体现为**容量为零**，
> 而不是先发布容量再在提交时拒绝。这也是 `capacity` 在无能量/缺模具时返回 0 的原因。

### 数据能源需要为万象样板额外适配吗？

**不需要。** 这是本模组自己就能决定的事，DE 侧无需任何改动。原因是 DE 那条 exact 分支的每一个
门槛都由**供应器发布的身份**驱动，DE 并不判断样板种类：

| DE 侧环节 | 取值来源 |
| --- | --- |
| `ProviderCapacitySnapshot.machineTargetId` | 直接取 `captureCapacityFast` 返回的 `CountedCraftingTarget.machineIdentity()` |
| exact 适配器查找 | 经 DE entrypoint 注册的适配器（实现了 `BigIntegerCraftingProviderAdapter`） |
| 回传给 `prepareBigIntegerBatch` 的 target | 用上面的 route / machine 两个身份字符串原样重建 |

也就是说，只要本模组对万象样板发布带 machine identity 的 `TARGETED` 容量，DE 的
`usingSelectedProposal && machineTargetId.isPresent() && exactContext != null` 三个条件就会满足，
从而进入 exact 分支。**反过来**，如果本模组不发布 machine identity（例如单方块高级合金炉），
DE 的 `machineTargetId` 就为空，万象样板只能走长版 counted 路径 —— 每 tick 一个 `long` 窗口。

### 那么什么情况下还是走不到 bigint？

兼容性上不会，但**运行时**有三种情况会让某一批退回长版路径，都不是缺陷：

1. **本机容量为 0**：缺模具、档次不够、没能量，或回网队列被背压占满。
2. **DE 的异步提案没选中本机**：`usingSelectedProposal` 为 false 时 DE 会走同步回退，
   这一 tick 就不进 exact 分支（下一 tick 会重试）。
3. **DE 自己的派发预算耗尽**：DE 侧的 dispatch window / budget 用完了。

另外注意**能量是两处分别计费的**：DE 按 CPU 功率收它的电，本机按 `count × 单份能耗` 收机器
自己的电 —— 这与长版路径完全一致（长版路径同样是「CPU 电费 + 机器电费」），不是新引入的双重收费。
