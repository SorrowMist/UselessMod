# 无线物流性能分析报告（spark 采样）

分析对象：`x9JK4nTULX.sparkprofile`（1.4 MB）
采样日期：2026-09-28
代码版本：`useless_mod 1.21.1-2.4.4`

---

## 一、采样原始数据

| 项 | 值 |
|---|---|
| 文件 | `x9JK4nTULX.sparkprofile`（spark 1.10.124 原始 protobuf） |
| 平台 | NeoForge 21.1.234 / MC 1.21.1，**单人存档（IntegratedServer）** |
| 采样线程 | 仅 `Server thread` |
| 时间窗 | 6 段，起止 `1790557358313` → `1790557658659` ms，**共 300.3 秒 / 6000 tick** |
| CPU | AMD Ryzen 5 5600G with Radeon Graphics |

### 这次测的是什么装置

从 profile 的类表（类名 + modid）与调用链还原：

- **源 = AE2 的 ME 接口**：`appeng.blockentity.misc.InterfaceBlockEntity`、
  `appeng.helpers.InterfaceLogic`、`appeng.helpers.externalstorage.GenericStackItemStorage`、
  `appeng.util.ConfigInventory`
- **目标 = 虚空垃圾桶**：`com.supermartijn642.trashcans.TrashCanBlockEntity$1`
- **介质 = 物品（`LinkMedium.ITEM`）**，**无过滤器** → 走 `moveItems` 的扫描路径
- **`AeLogistics*` 在整份 profile 里出现 0 次** → 我们写好的 AE 原生端点一次都没跑到

---

## 二、结论：存档没有卡

| 指标 | 值 |
|---|---|
| 服务端线程总时长 | 300,000 |
| 其中空闲等待（`Unsafe.park` / `waitUntilNextTick`） | 258,184（**86%**） |
| **实际 tick 工作量（`MinecraftServer.tickServer`）** | **36,056 → 6.01 ms/tick、20 TPS** |
| **无线物流（`EventHandler.onStaffLinkTick`）** | **3,956 → 0.66 ms/tick、tick 工作量的 11.0%** |
| 同上，占 `ServerTickEvent.Post` 全部监听器 | **56%**（`fireServerTickPost` = 7,068） |

**0.66 ms/tick 不是「延迟」，是「11% 的 tick 预算」。有优化空间，但属于「削无效开销」，不是救火。**

每 50 秒窗口的无线物流耗时：`284 / 676 / 760 / 780 / 780 / 668`
—— 稳态 ~0.7–0.78 ms/tick，是持续开销而非尖峰。

---

## 三、开销拆解（共 3,956）

| 项 | ms | 占比 |
|---|---|---|
| `StaffLinkTargets.transfer` 整条搬运链 | 3,312 | 84% |
| ├ **ItemStack 物化 / 拷贝** | **2,136** | **54%** |
| │ ├ AE `AEItemKey.toStack()` ×3/次搬运 | 1,136 | 29% |
| │ ├ 适配器 `getStackInSlot().copy()` | 368 | 9% |
| │ └ `insert` 内 `copyWithCount` | 632 | 16% |
| ├ **AE2 `InterfaceLogic.updatePlan`**（被 extract 触发） | 604 | 15% |
| ├ `resolve` / `capability` | 212 | 5% |
| └ 其它（适配器循环 / `getSlots` / 过滤器） | ~360 | 9% |
| `SignalGetter.getBestNeighborSignal` | 312 | 8% |
| `StaffLinkEngine.anyNodeDue` | 112 | 3% |
| 调度表 `setNextRun` / `nextRunAt` | 44 | 1% |
| 每 tick 列表分配 + `refreshLiveNetworks` + `pruneStaleRoutes` | ~60 | 1.5% |
| 其它 | ~92 | 2% |

### 3.1 那 3 次 `toStack()` 是怎么来的

`LongItemHandler` 的契约是「`getStackInSlot` 只判类型、数量看 `amountIn`」。但 AE2 内部存的是
`AEKey`，通用 `IItemHandler` 只认 `ItemStack`，于是每次读槽位都要**现造**一个 `ItemStack`。

1.21 里造一个 ItemStack 不只是 new 个对象 —— 它要连带复制一整份 DataComponent 映射
（profile 里的 `DerivedComponentMap.derive` / `PatchedDataComponentMap.restorePatch` /
`AbstractReference2ObjectMap.putAll` 就是干这个的）。

一次搬运读同一个槽位 **3 遍**：

| # | 位置 | 调用 | `toStack` 耗时 |
|---|---|---|---|
| 1 | `StaffLinkTargets.java:459` | 扫描拿模板 → `getStackInSlot` | 444 |
| 2 | `StaffLinkTargets.java:475` | `amountIn(slot)` → **又读一遍同一个槽位** | 424 |
| 3 | `LongResourceAdapters.java:87` | `extractItem` | 268 |

外加：

- `LongResourceAdapters.java:62` 的 `.copy()`（368）
- `LongResourceAdapters.java:109` 的 `copyWithCount`（632）—— 一次搬运 `insert` 被调两次
  （模拟 + 执行）

### 3.2 为什么 AE2 那边还要 `updatePlan`

```
extract
  → GenericStackItemStorage.extractItem
    → GenericStackInv.extract
      → setStack → onChange → notifyListener
        → InterfaceLogic.onStorageChanged
          → updatePlan                                  ← 604 ms
              ├ storedRequestEquals
              │   → IUpgradeInventory.isInstalled
              │     → UpgradeInventory.getInstalledUpgrades
              │       → ItemDefinition.asItem
              │         → DeferredHolder.value          ← 272 ms
              └ hasWorkToDo / alertDevice …
```

即「**每抽一次就让接口把整个计划重算一遍**」。其中 272 ms 是 AE2 自己在重复解析 `DeferredHolder`，
属于 AE2 侧的实现开销。

---

## 四、优化清单（按收益排序）

### 4.1 立即可用 —— 不改代码

**C1. 把这条线路的介质从「物品」改成「AE 物品」。** 省 ~1,740 ms（44%）。

- **选项本身是好用的、也是生效的**（已逐环节核实）：

  | 环节 | 位置 |
  |---|---|
  | 下拉列表 | `LinkMedium.supported():105` —— 装了 AE2 就列出 `AE_ITEM` |
  | 界面渲染/点选 | `StaffLinkScreen.java:235, 964-1032, 1315-1320` |
  | 服务端校验 | `StaffLinkConfigurePacket.java:64` —— 只拒 `!isSupported()` |
  | 运行时生效 | `StaffLinkTargets.java:293` —— `case AE_ITEM -> itemEndpoint(...)` |

- ⚠️ **前提是先做 O3（端点缓存）。** `AeLogisticsCompat.itemEndpoint:81` 每次 `resolve` 都
  `new ItemEndpoint(level, pos)`，而整网快照是在端点首次被问内容时才抓的
  （`snapshot():139-156`，类注释 `:50` 也写明「每次 resolve 新建端点时重新抓」）。
  所以切过去之后，每次搬运会变成**完整遍历一遍 ME 网络的 `getAvailableStacks()`**。
  小网络更快；上千种的成熟网络未必比 `toStack` 便宜。
  **O3 没做之前不要切。**

**C2. 调大线路的「周期」（`interval`）。** 耗时与运行频率线性相关 —— 周期从 1 调到 5 直接省 80%。
这是眼下唯一零风险的立竿见影手段。

### 4.2 低风险 —— 建议先做（合计省 ~1,409–1,759 ms，即 -36% ~ -44%）

| # | 位置 | 问题 | 修法 | 省 |
|---|---|---|---|---|
| **O1** | `StaffLinkEngine.java:201` | `candidate.trigger().allows(level.getBestNeighborSignal(pos))` 的实参是**急切求值**的，而默认触发条件是 `ALWAYS`（`LinkTrigger.java:27` 根本不用这个信号）—— 白算 6 个方向的红石 | 改成 `trigger() != LinkTrigger.ALWAYS && !trigger().allows(...)` 才短路；或给 `LinkTrigger` 加 `needsSignal()` | 312（8%） |
| **O2** | `StaffLinkTargets.java:459` + `:475` | 扫描路径已拿到模板，`moveOneItemStack` 又调 `from.amountIn(slot)` → 同一个槽位读两遍 | `LongItemHandler` 加 `default long amountIn(int slot, ItemStack known) { return amountIn(slot); }`，`LongResourceAdapters.items()` 覆写成 `known.getCount()`，扫描路径传进去。**不能**在调用方直接写 `template.getCount()`：AE 端点 `getStackInSlot` 返回 `toStack(1)`（count=1），与真实数量不同 | 480（12%） |
| **O3** | `StaffLinkTargets.java:281-355` | 每次 `transfer` 两端各 `resolve` 一次；`side == null` 时最多 7 次 `getCapability`；每次都 `new` 匿名适配器；AE 端点还会重建整网快照 | 加 per-tick（或 20 tick TTL）的 `(dimension, pos, side, medium) → endpoint` 缓存，方块状态变化时失效。**注意保留「快照按次重建」的语义**（否则边抽边变，`slot` 会在遍历途中指向另一个 key）—— 缓存端点对象、每次搬运前 refresh 快照即可 | 212（5%） |
| **O5** | `StaffLinkEngine.java:55-79` | 主线程用 `ConcurrentHashMap`；每次查询 `new NodeKey(route, anchor)`；`GlobalPos` 的 hashCode 要算维度+坐标 | 换 `HashMap`；key 打包成 `(route << 48) \| pos.asLong()`；`anyNodeDue` 先看 `size()` | 50–80（1.5–2%） |
| **O6** | `StaffLinkEngine.java:106,170-176,216-217` | `List.copyOf(data.all())`（`all()` 内部也 copy）；每网络 9 个 `ArrayList`；每条 route 新建 `Comparator.comparingInt(...).reversed()` | `Comparator` 提成 `static final`；空线路复用 `List.of()`；`tick` 直接用 `data.all()` 的返回值 | ~40（1%） |

### 4.3 中等风险 —— 需先验证目标容器的行为

| # | 位置 | 问题 | 修法 | 省 |
|---|---|---|---|---|
| **O4a** | `LongResourceAdapters.java:109` | `insert` 每次调用至少造一个栈，一次搬运调两次 | 适配器实例持一个 scratch 栈，`ItemStack.isSameItemSameComponents` 相同时复用、只 `setCount()` | ~300（8%） |
| **O4b** | `StaffLinkTargets.java:481-490` | `to.insert(..., true)` 模拟 + `to.insert(..., false)` 执行 = 两次完整槽位遍历 | 改成「先抽 → 再插 → 余量退回源」（余量退回逻辑 `490-498` 已存在），去掉模拟那一次 | 再 ~350（9%） |

### 4.4 O7（已降级）：不是「选项不生效」，是「默认值 + 前置条件」

**先说清楚：玩家选「物品 / AE 物品」的选项是生效的。** 真正要处理的是三件事：

1. **默认值落点**：`StaffLinkMenu.defaultRouteFor:221` 新线路默认 `LinkMedium.ITEM`
   （仅当同一锚点已有配置时才沿用）。绑 AE 接口的玩家不知道要手动切，就停在慢路径上。
   - 可选做法：`StaffLinkTargets.defaultMediumFor` 在方块是 AE 端点时给 `AE_ITEM`；
     或至少在绑定界面加一句提示。
2. **「能切」≠「切了就快」**：见 C1 的 ⚠️。**O3 是切 AE 物品的前置条件。**
3. **选 `ITEM` 也是正当选择**（玩家可能就是要接口自身那 9 格；且垃圾桶 / 箱子 / 机器只有 ITEM
   一条路），所以 ITEM 这条路的浪费必须直接修 —— 也就是 O1 / O2 / O4a。

> 与介质无关的 O1 / O2 / O3 / O4 / O5 / O6，选哪个介质都该做。

### 4.5 明确不建议做

- **去掉 `moveItems` 的 `MAX_SCAN_SLOTS` 扫描预算** —— 有事故记录（接收端设了过滤后永远收不到），别动。
- **把 `insert` 的模拟完全去掉** —— 见 O4b，只在确认目标容器行为稳定后再做。

---

## 五、顺带发现（不在无线物流范围）

tick 工作量（36,056）的构成：

| 项 | ms | 占 tick 工作量 |
|---|---|---|
| `MinecraftServer.tickChildren` | 23,164 | 64.2% |
| ├ `ServerLevel.tick` | 14,976 | 41.5% |
| │ └ `ServerChunkCache.tickChunks` | 12,112 | 33.6% |
| │   └ **`NaturalSpawner.spawnForChunk`** | **10,552** | **29.3%** |
| └ `DistanceManager.runAllUpdates`（自身） | 2,904 | 8.1% |
| `EventHooks.fireServerTickPost` | 7,068 | 19.6% |
| └ `EventHandler.onStaffLinkTick` | 3,956 | 11.0% |
| `EventHooks.fireServerTickPre` | 5,132 | 14.2% |

- **想继续压 mspt，大头在刷怪**（`doMobSpawning` / `spawn-limits`），不是本模组。
- `ChunkAccess.getMinBuildHeight` 自身 3,288 ms（9.1%）—— 原生 / 优化 mod 侧热点，值得单独看。
- `fireServerTickPre` 里没有本模组的显著项。

---

## 六、验证方法

1. 按项目约定：改了 `src/main/resources` 必须 `./gradlew processResources`（开发环境加载的是
   `build/resources/main` 的副本）→ 再编译。
2. 用**同一套装置**（AE 接口 → 虚空垃圾桶、同一条线路配置）再采 5 分钟 spark。
3. 对比三个数：
   - `com.sorrowmist.useless.event.EventHandler.onStaffLinkTick` 的 inclusive 值
   - `net.minecraft.world.item.ItemStack.copy` / `appeng.api.stacks.AEItemKey.toStack` 的出现次数
   - `net.minecraft.world.level.SignalGetter.getBestNeighborSignal`
4. 预期：

| 场景 | `onStaffLinkTick` |
|---|---|
| 基线（本次采样） | 3,956 ms |
| 只做 O1–O6 | **2,200–2,550 ms**（-36% ~ -44%） |
| 再加 C1 + O3 | **500–800 ms**（-80% ~ -87%） |

---

## 七、实施状态（2026-09-28 已实施）

已按本报告改完代码，编译通过，产物 `build/libs/useless_mod-1.21.1-2.4.4.jar`。

| # | 状态 | 落点 |
|---|---|---|
| **O1** | ✅ 已实施 | 新增 `StaffLinkEngine.passesGate`：`ALWAYS` 直接放行、**不读红石**；周期判定提到 `levelOf`/红石之前；吸收端的闸门推迟到确认有 due 释放端之后 |
| **O2** | ✅ 已实施 | `LongItemHandler.amountIn(int, ItemStack)` 新默认方法；`LongResourceAdapters.items()` 覆写为复用模板的 count；`StaffLinkTargets` 扫描路径传入已取到的模板 |
| **O3** | ⚠️ **保守实施** | 只做了「上次命中的面」提示（`StaffLinkTargets.CAPABILITY_HINTS`，失手退回完整扫描，零陈旧风险）。**没做跨 tick 的能力对象缓存**，理由见下 |
| **O4a** | ✅ 已实施 | `LongResourceAdapters.items().insert` 复用一只暂存栈 + `setCount`，不再每步 `copyWithCount` |
| **O4b** | ❌ 未实施 | 中等风险（会改变源的抽取时序），按计划只在确认目标容器行为稳定后再做 |
| **O5** | ✅ 已实施 | 新增 `NetworkSchedule`：锚点 → `long[ROUTE_COUNT]`，去掉 `NodeKey` 分配；`anyNodeDue` 加「最快到点」缓存走 O(1) 快路径；`HashMap` 取代 `ConcurrentHashMap` |
| **O6** | ✅ 已实施 | `BY_WEIGHT` 提为静态常量；`byRoute` 改数组 + 惰性建桶；`StaffLinkSavedData.all()` 改返回 `List`，去掉双重拷贝 |
| **O7** | ➖ 未改 | 默认介质保持 `ITEM` 优先。要切 AE 物品，仍需先解决 O3 的完整版 |

### 为什么 O3 只做了提示、没做跨 tick 缓存

跨 tick 缓存「`(维度, 坐标, 面, 资源类型)` → 能力对象」能把 212 ms 全吃掉，但它引入一类
**静默错误**：方块被挖掉、或换成别的方块实体之后，旧的能力对象还指向已经脱离世界的容器，
塞进去的东西会凭空消失。本模组在自愈逻辑上已经反复强调过「不能拿解析结果当存活判据」
（见 `StaffLinkEngine.pruneStaleRoutes` 的注释），不值得为了 5% 再开这个口子。

「上次命中的面」提示不同：它只决定**先试哪一个**，失手就退回完整扫描，读到的永远是当下真实的
能力对象，最坏多付一次查找。

### O4a 的行为假设（若出问题先回滚这里）

复用暂存栈依赖 NeoForge 的契约：`IItemHandler#insertItem` **「不得修改传入的栈」**。
我们改的是自己的副本，且复用前用 `isSameItemSameComponents` 比对过，所以对合规实现完全等价。
若某个容器出现物品错乱 / 凭空增减，第一件事就是把 `LongResourceAdapters.items().insert`
改回 `template.copyWithCount(chunk)`。

### 另外两处刻意保留

- `moveItems` 的 `MAX_SCAN_SLOTS` 扫描预算**没动**（有事故记录，见 `StaffLinkTargets` 注释）。
- `StaffLinkRoute.activeFilters()` 每次仍分配一个列表（28 ms / 0.7%）。`StaffLinkRoute` 是 record，
  加不了惰性缓存字段，为它改成普通类不划算。

### 验证

```bash
./gradlew --stop; ./gradlew compileJava --no-daemon      # 编译通过
./gradlew processResources jar --no-daemon               # build/libs/useless_mod-1.21.1-2.4.4.jar
javap -p -classpath <jar> com.sorrowmist.useless.api.logistics.LongItemHandler   # 新方法在
```

---

## 八、「上次搬运」读数改为按选中线路（2026-09-28 第二轮）

### 问题

原来那行读数**是整张网络的聚合**，却画在「选中锚点 × 选中线路」的配置区里（紧挨过滤槽）。
四个具体的坑：

1. **别人的问题显示在你选的容器上**：选中 A（正常），同网络里 B 的源空了 → `blocker` 取到 B 的
   `SOURCE_EMPTY`，A 的读数区变红显示「源已空」。
2. **数字是全网络之和**：A 请求 64、B 请求 1024 → 选中 A 看到 `1088`。
3. **`blocker` 取「第一个遇到的」**（顺序 = 权重降序 + 线路号升序），不是你选中的那条。
4. **选中接收端时最离谱**：那个 `requested` 是**释放端**的请求量，语义完全对不上。

### 改法

读数现在**只反映界面当前选中的那一条线路**，两种视角：

| 选中 | 主行 | 悬停提示 |
|---|---|---|
| **释放端** | 搬走 / 请求 · 卡在哪（原有） | 分给 N 个输出 |
| **接收端** | 收到 / 预期 · 来自 N 个释放端 | 逐条列出「哪个释放端给了多少」 |

外加一条客户端可自行判断的提示：选中的接收端**这条线路上根本没有释放端**时显示
「这条线路上没有释放端」——与「有释放端但没搬动」区分开，两者要玩家做的事完全不同。

### 关键实现点

- **新增 `StaffLinkSelectionPacket`**（客户端 → 服务端）。选中项本来是纯客户端状态
  （`StaffLinkScreen` 自己维护），服务端不知道；要按它记账就必须先告诉它。
  包体不带网络 ID（沿用 `StaffLinkDetachPacket` 的越权模型），服务端只认当前打开的界面所属网络，
  并再校验一次锚点是否仍在该网络里。
- **`StaffLinkEngine` 新增 `WATCHED` 登记表**（观看者 → 「网络 × 锚点 × 线路」）与
  `ROUTE_STATS` 读数表。`StaffLinkMenu.setSelection` 在客户端发选择包、在服务端登记；
  `removed()` / `setNetworkId()` / 锚点被解绑时注销。
- **接收端的来源明细**：`TransferStats` 增加 `shares`（`List<SourceShare>`，最多 8 条，
  超出只体现在「来自 N 个释放端」上），由 `distribute` 逐项回填每个输出搬了多少得到。
  累加边界靠 `TransferStats.tick()` 认——上一次记的不是当前 tick 就从头攒起。
- 客户端用包里的 `anchor` / `route` 复核「这份读数是不是我现在看的那条」，路上还在飞的旧包
  不会被画到新选中项头上；切换选中项时本地先把读数清成「未搬运」。

### 无人观看时的开销（这次改动的硬要求）

**与没有这个功能时逐字节一致**：

| 位置 | 闸门 |
|---|---|
| `StaffLinkEngine.runNetwork` | `watched = hasWatchers()`；为 false 时 `releaseKey = null`、`perTarget = null`、不查读数表、不构造 `TransferStats`、不做 `diagnose` |
| `StaffLinkEngine.distribute` | `diagnose` 只在**这条释放端正被人看着**时才做 |

> **`pushStaffLinkStatus` 这里刻意不设闸门**（后来改的，见第九节）。它只是每 20 tick 遍历一次
> 在线玩家、给开着界面的那个发一个几十字节的包，**不需要 `stack`/`distribute` 那种昂贵计算**；
> 而设了闸门之后，「界面为什么不动」少了一条排查路径。真正的开销闸门在引擎记账端，那边才要紧。

顺带把原来每 tick 都跑的整网络合计（`requestedTotal` / `movedTotal` / `targetTotal` /
`LAST_TRANSFER.put`）整段删掉了——那些数字现在没有任何地方显示。

> **`diagnose` 因此顺带被彻底消掉了**：它本来就是为了显示「卡在哪一步」，没人看就不需要它。
> 这比单独做节流更彻底。

### 验证

```bash
./gradlew compileJava processResources jar --no-daemon   # BUILD SUCCESSFUL
javap -p -classpath <jar> com.sorrowmist.useless.content.stafflink.StaffLinkEngine
#   watch / unwatch / hasWatchers / statsFor 都在
javap -p -classpath <jar> com.sorrowmist.useless.network.StaffLinkSelectionPacket
unzip -p <jar> assets/useless_mod/lang/zh_cn.json | grep stats_absorb   # lang 已同步
```

---

---

## 九、「读数看起来卡住」的根因与修法（2026-09-28 第三轮）

### 现象

用户实测：**「一直显示的都是0秒前，但中途确实有过传输却没有变化」**

### 根因：不是丢包，是读数语义

`0秒前` 说明服务端**每轮推送都在写这条线路的读数**——线路在跑、也记到账了。
所以「不刷新」不是数据链路问题，而是**读数给错了东西**：

`moved` 是「**这一轮**搬了多少」。稳态下（源一直有货、目标一直收得下）它每轮都等于
`amount × 输出数`，也就是永远 `16/16`。界面**每轮都在刷新，只是刷新成同一个值**——
观感上与「卡住」完全一样。

> 教训：把「值恒定」当成「值不更新」去查丢包，方向从一开始就偏了。**先分清「没刷新」和
> 「刷新了但值不变」，再决定查哪一层。** `ageTicks=0` 这个读数就是分开两者的钥匙。

### 修法：补一个累计值

`TransferStats` 增加第 7 个字段 **`totalMoved`** = 「从选中这条线路起一共搬了多少」。
只要还有货在动它就一定在涨——这才正面回答「它到底动没动」。

| 位置 | 改动 |
|---|---|
| `TransferStats` | 新增 `totalMoved`；`NONE` 里为 `-1`（还没有起算） |
| `StaffLinkEngine.watch` | 换选中项时 `ROUTE_STATS.remove(newKey)`，累计值归零 |
| `recordRelease` | `base + moved`（这一轮搬的量） |
| `appendSource` | `base + received`——**加的是「本次这条来源给了多少」，不是本轮合计** |
| `StaffLinkStatusPacket` | 尾部多写一个 `varLong totalMoved` |
| `StaffLinkScreen` | 主行与 tooltip 都改成显示累计值（本轮值退居 tooltip） |
| lang（zh/en） | `stats` / `stats_absorb` / `stats_hint` / `stats_hint_absorb` / `stats_none` / `stats_blocked` 一并改 |

**`appendSource` 那个坑值得单列**：接收端一 tick 内可能被多个释放端喂，`appendSource`
会被多次调用。若累计值加的是「本轮合计」，同 tick 的第二次调用就把第一次的量又加了一遍。
必须加**单条来源的增量**。

### 现在的显示

| 选中 | 主行 | 悬停 |
|---|---|---|
| 释放端 | `累计 1024 · 本轮 16` | 累计搬走 X（从选中起算）；本轮请求 Y，分给 N 个输出 |
| 接收端 | `累计收到 1024 · 本轮请求 16 · 来自 2 个释放端` | 累计收到 X；本轮实收 Y、打算给 Z；逐条来源 |

`stats_blocked` 保留本轮数字（它要回答的是「**这次**为什么没搬动」）。

### 验证

```bash
./gradlew compileJava processResources jar --no-daemon    # BUILD SUCCESSFUL
javap -p -classpath build/libs/useless_mod-1.21.1-2.4.4.jar ...StaffLinkEngine\$TransferStats
#   → 私有字段里能看到 long totalMoved ✔
unzip -p build/libs/...jar assets/useless_mod/lang/zh_cn.json | grep 'wireless_logistics.stats"'
#   → "累计 %s · 本轮 %s" ✔（改了 resources 就一定要跑 processResources）
```

jar：`build/libs/useless_mod-1.21.1-2.4.4.jar`（15:08，3726611 字节），
已部署到 `G:/mc/.minecraft/versions/EnigmaticSkies/mods/`（旧的移到同目录 `backup/`）。

---

## 附：`sparkprofile` 离线解析手法

`.sparkprofile` 是**原始 protobuf（不是 gzip）**，不依赖任何库、手写 varint 解析即可：

- 顶层字段：`f1` = metadata（`f2`/`f11` = 起止时间、`f12` = tick 数、`f3` = interval、
  `f13` = 模组列表）；`f2` = 调用树；`f3` = 类表（`{1: 类名, 2: modid}`，**用它反查热点属于哪个 mod**）；
  `f6` = 时间窗；`f7` = tick 统计。
- `f2` = `Data`：`f1` = 线程名；`f3` = 扁平节点数组。每个节点的字段：

  | 字段 | 含义 |
  |---|---|
  | `f3` | 类名 |
  | `f4` | 方法名 |
  | `f6` | 行号 |
  | `f7` | 方法签名 |
  | `f8` | 6 个 double —— 各时间窗的耗时 |
  | `f9` | **children 索引（packed varint）** |

- **没有 parent 字段** —— 父节点由 children 反推；根 = 从未出现在任何 children 里的节点。
- self time = `sum(f8) - Σ sum(children 的 f8)`；按 `类.方法` 聚合即得热点排名。
- **坑**：根节点的每窗值可能恰好等于窗长（看起来像 6 × 50,000 = 300,000），别据此推窗长；
  以**总和的比例**为准。

---

## 十、切到「AE 物品」之后反而更慢（2026-09-28 第四轮采样）

用户把源端介质从**物品**改成 **AE 物品**，反馈「用 AE 模式延迟反而更高了」。采样
`xVcG2iDvfr.sparkprofile`（19:48，300 s）证实了这一点，并且**正是 C1 的 ⚠️ 预言的那个坑**。

### 10.1 两次采样对照

| | 基线 `x9JK4nTULX`（12:32） | 本次 `xVcG2iDvfr`（19:48） |
|---|---|---|
| `onStaffLinkTick` | 3,956 ms | **9,396 ms** |
| 占 `tickServer` | 10.7% | **22.7%** |
| `tickServer` 合计 | 37,092 ms | 41,428 ms |
| 稳态 MSPT | 5.07–5.28 | 5.89–6.18 |
| 每个 60 s 窗的无线物流耗时 | ~740 ms | ~2,100 ms（**×2.8**） |

`tickServer` 只涨了 4,336 ms，而无线物流一项就涨了 5,440 ms —— **整个延迟上升都是它**。

> **两次采样的端点并不相同**，所以这不是一次干净的 A/B：

| | 源端 | 目标端 |
|---|---|---|
| 12:32 | ME 接口（介质=物品，走 `IItemHandler`） | 垃圾桶（介质=物品） |
| 19:48 | **ME 网络直连（介质=AE 物品）** | **AE2 库存方块（介质=物品）** |

### 10.2 新开销的两笔（占 9,396 的 89%）

| 位置 | 耗时 | 占比 | 说明 |
|---|---|---|---|
| **源端 `ItemEndpoint.findSlot`** | **3,124** | **33.2%** | 其中 `snapshot` 2,988 —— `MEStorage.getAvailableStacks()` **把整张 ME 网络枚举一遍** |
| **目标端 `LongResourceAdapters$1.insert`** | **5,244** | **55.8%** | 其中 AE2 `GenericStackItemStorage.insertItem` 4,168 + 我们自己的 `copyWithCount` 964 |
| 源端 `extract` | 148 | 1.6% | AE 抽取本身很便宜 |
| `resolve` | 344 | 3.7% | 每次 `transfer` 两端各解析一次 |
| 红石 | 268 | 2.9% | 非 `ALWAYS` 触发的线路 |

目标端 `insert` 的内部（4,168）：

| 帧 | 耗时 | 说明 |
|---|---|---|
| `AEItemKey.of` | 2,256 | 哈希整份 `DataComponentMap`（`hashItemAndComponents` 760） |
| `Platform.copyStackWithSize` | 992 | 又拷一份 `ItemStack` |
| `GenericStackInv.insert` | 776 | 其中 `notifyListener` → `InterfaceLogic.updatePlan` 600 |

`AEItemKey.of` 2,256 里有一大块是**整合包自己的代价**：装了 `owo-lib` 之后，
每次 `ItemStack.copy()` / `hashCode()` 都要重算 / 重拷派生组件
（`DerivedComponentMap.derive` 1,492、`.hashCode` 748 —— 整个 profile 口径）。

### 10.3 为什么「AE 物品」会变成这样

`AeLogisticsCompat` 的类注释（`:50`）已经写明：

> 快照是**每次 `resolve` 新建端点时重新抓的**。

而 `StaffLinkTargets.transfer` 每次调用都 `resolve` 一次源端，`distribute` 又是**按目标逐个**
调 `transfer` —— 于是：

1. 一个释放端接 N 个接收端 → **整张 ME 网络被枚举 N 遍**；
2. 就算只有 1 个目标，`findSlot` 也必须先 `snapshot()` 才能回答「网络里有没有这一种」；
3. `AeNetworks.storage(level, pos)` 在 `snapshot` / `extract` / `insert` 里各调一次
   （每次都要 `getBlockEntity` + 能力查询，profile 里 `Level.getBlockEntity` 232 ms）。

**这正是 C1 的 ⚠️ 警告**：「O3 没做之前不要切」。而 O3 当时只做了「上次命中的面」提示，
**没做端点 / 快照复用**（见第七节）。

### 10.4 修法

**立即可用（零代码）**：把源端介质切回**物品**，或按 C2 调大线路周期。
在 O3 完整版落地之前，AE 物品这条路一定比物品慢。

**要让 AE 物品真的变快**，需要做定向的 O3：

| # | 改法 | 预期 |
|---|---|---|
| **P1** | `LongItemHandler` 加 `default long extractMatching(ItemStack template, long amount, boolean simulate)`（默认 = `findSlot` + `extract`）；`ItemEndpoint` 覆写成 `AEItemKey.of(template)` → 直接 `storage.extract(key, ...)`。**完全不碰 `snapshot()`** | 吃掉 2,988（32%） |
| **P2** | `ItemEndpoint` 里把 `AeNetworks.storage()` 缓存成字段，`snapshot`/`extract`/`insert` 共用 | ~230（2.5%） |
| **P3** | `distribute` 里把源端 `resolve` 一次、复用给所有目标 | 随目标数线性，1→N 时省 (N−1)/N |
| **P4** | 目标端也切 AE 物品 → 走 `MEStorage.insert`，省掉 `Platform.copyStackWithSize` + `GenericStackInv` + `updatePlan` | ~1,768（19%） |

**不变量**：P1 只在「有过滤器」的路径上成立 —— 无过滤器时必须遍历网络才知道有什么，
那时枚举是必要的。而且 P1 保留「先模拟后提交」的时序，语义不变。

**顺带**：`StaffLinkTargets.resolve` 344 ms 里大半是 `capability(...)` 的
`side == null` 兜底扫描（最多 7 次 `getCapability`）。O3 完整版（跨 tick 缓存）能一起吃掉，
但第七节说明了它引入的静默错误风险，**不打算做**。

### 10.5 复现手法

```bash
# 离线解析，不用开游戏
python wiki/tools/spark_profile.py <profile>            # 总览 + 无线物流拆解
python wiki/tools/spark_profile.py <profile> --top 30   # 全局自耗时前 30
python wiki/tools/spark_profile.py <profile> --tree ItemEndpoint.findSlot
```

### 10.6 实施记录（P1 / P2 / P3 已落地）

| # | 落点 | 改动 |
|---|---|---|
| **P1** | `LongItemHandler.extractMatching(ItemStack, long, boolean)`、`LongFluidHandler.drainMatching(FluidStack, long, boolean)` | 新原语：一次调用同时回答「有没有这一种」和「能抽多少」。默认实现 = `findSlot` + `extract`（原生容器照旧）。 |
| **P1** | `AeLogisticsCompat$ItemEndpoint.extractMatching` / `$FluidEndpoint.drainMatching` | 覆写成 `AEItemKey.of(template)` → 直接 `MEStorage.extract(key, ...)`，**完全不碰 `snapshot()`**。字节码自检确认 `extractMatching` 里没有 `getAvailableStacks`。 |
| **P1** | `StaffLinkTargets.moveItems` / `moveFluid` 的过滤器分支 | 改走 `moveOneItemStackByType` / `moveOneFluidByType`，不再 `findSlot` / `findTank`。 |
| **P2** | `ItemEndpoint` / `FluidEndpoint` 新增 `storage()` + `storageResolved` | 网络句柄一个端点实例只解析一次；`snapshot`/`extract`/`insert` 共用。用 `storageResolved` 区分「没查过」与「查了是 null」，网络离线时不反复重查。 |
| **P3** | `StaffLinkTargets.resolveSource` + `transfer(from, ...)`；`StaffLinkEngine.distribute` | 源端点解析一次、所有接收端共用。原来的 6 参数 `transfer` 已无人调用，一并删掉。 |

**刻意保留 / 刻意不做：**

- `ItemEndpoint.findSlot` / `FluidEndpoint.findTank` **保留**：无过滤器的扫描路径本来就必须知道网络里
  有什么，绕不开快照；删掉它会让默认实现退化成「逐条 `toStack(1)` 再比较」，更慢。已在注释里写明
  「过滤器驱动的搬运不再走这里」。
- `insert` / `fill` 里**不**作废快照：无过滤器扫描路径靠「一次搬运沿用同一份快照」保证 `slot`
  在遍历途中始终指向同一个 key（类注释原有约定）。作废它会让扫描循环每插一次就重抓一遍整网。
- 只在 `extractMatching` / `drainMatching` **提交后**作废快照（那时才真的会与扫描路径交叉）。

**已知取舍**：过滤器路径上源端被问两次（试算 + 提交）。AE 端点是两次哈希查找，可忽略；
默认实现多一次槽位扫描 + 一次 `extract(..., true)`。换来的收益是「源端没货时不必碰目标」——
而目标端一次 `insert` 试算实测占无线物流总耗时的 56%，比那次扫描重要得多。

**验证**：

```bash
./gradlew --stop; ./gradlew compileJava processResources jar --no-daemon   # BUILD SUCCESSFUL
javap -p <jar> LongItemHandler      # extractMatching 在
javap -p <jar> LongFluidHandler     # drainMatching 在
javap -p <jar> 'AeLogisticsCompat$ItemEndpoint'   # extractMatching + storage() 在
javap -c  ... extractMatching 的方法体里没有 snapshot() / getAvailableStacks  ← 关键
```

产物 `build/libs/useless_mod-1.21.1-2.4.4.jar`（20:07，3705876 字节）。

**下一步**：把这版 jar 部署上去，用**同一套装置**（源端保持 AE 物品、目标端不变）再采一次 spark。
预期 `onStaffLinkTick` 从 9,396 降到 **5,900–6,400**（源端 2,988 与 resolve 的 344 基本消失，
目标端 5,244 不动）。若目标端也切成 AE 物品（P4），还能再省 ~1,700。

---

## 十一、P1/P2/P3 实测结果与下一步（2026-09-28 第五轮采样）

用户在**同一台机器、同一套装置**上跑了两版：

- `8r3vgmP6fl`（20:31）= 改之前的版本
- `8ab5ESULNl`（20:40）= P1/P2/P3 版本（`build/libs/useless_mod-1.21.1-2.4.4.jar`，20:07）

### 11.1 效果

| | 旧（8r） | 新（8a） | Δ |
|---|---|---|---|
| `onStaffLinkTick` | 8,372 ms | **4,732 ms** | **−43.5%** |
| 占 `tickServer` | 24.7% | **10.7%** | −14 pt |
| 每个 60 s 稳态窗 | ~1,633 ms | ~874 ms | −46% |
| `ItemEndpoint.findSlot` | 2,628 | **0** | 整网枚举完全消失 |
| `ItemEndpoint.snapshot` | 2,540 | **0** | 同上 |
| `ItemEndpoint.extractMatching` | — | 320 | 新的廉价路径 |

P1 达到预期（源端 2,988 的枚举归零）。P3 在这个装置上没有体现 —— `resolve` 356 → 348，
说明每条线路只有一个接收端，本来就只解析一次。

### 11.2 剩下的 4,732 在哪

| 位置 | 耗时 | 占比 |
|---|---|---|
| **目标端 `LongResourceAdapters$1.insert`** | **3,912** | **82.7%** |
| └ AE2 `GenericStackItemStorage.insertItem` | 3,792 | 80.1% |
| &nbsp;&nbsp;└ `AEItemKey.of` | 1,952 | 41.3% |
| &nbsp;&nbsp;└ `Platform.copyStackWithSize` | 1,064 | 22.5% |
| &nbsp;&nbsp;└ `GenericStackInv.insert` | 776 | 16.4% |
| &nbsp;&nbsp;&nbsp;&nbsp;└ 触发 `InterfaceLogic.updatePlan` | 416 | 8.8% |
| 源端 `ItemEndpoint.extractMatching` | 320 | 6.8% |
| `StaffLinkTargets.resolve` | 348 | 7.4% |

自耗时前几名全是「造 / 拷 `ItemStack` 的组件映射」：`ItemStack.<init>` 724、`putAll` 548、
`Reference2ObjectArrayMap.get` 472、`ItemStack.copy` 436、`BasicEntry.hashCode` 360、
`ensureMapOwnership` 192 —— 即 `owo-lib` 的 `DerivedComponentMap` 在放大 AE2 每次 insert 的拷贝。

**目标方块已确认是 ME 接口**：我们的 insert 会经
`GenericStackInv.insert → onChange → notifyListener → InterfaceLogic.onStorageChanged → updatePlan`。

### 11.3 下一步（按性价比）

| # | 改法 | 预计 | 风险 | 类型 |
|---|---|---|---|---|
| **P4** | **目标端介质也切成「AE 物品」** | **−2,250**（`copyStackWithSize` 1,064 + `GenericStackInv` 776 + `updatePlan` 416 全省），4,732 → ~2,500 | 语义：不再经过接口自己的 9 格缓冲，直接进主网 | 配置，零代码 |
| **P5** | `ItemEndpoint` 缓存「上次 template → AEItemKey」（用 `isSameItemSameComponents` 判等，比重新哈希便宜） | 省掉一次 `AEItemKey.of`，约 −800 | 低 | 代码 |
| **P6（=O4b）** | 去掉目标端那次模拟 insert | 约 −1,200（P4 之后） | 中：先抽源才发现目标不收，余量退回源，退不掉就掉地上 | 代码 |
| **C2** | 调大线路周期 / 缩短过滤器清单 | 线性 | 无 | 配置 |

**P5 必须跟 P4 一起做才有意义** —— `GenericStackItemStorage` 是 AE2 的代码，我们改不了。

**P6 在本场景风险比报告 4.3 里写的小**：源端是 ME 网络，余量退回就是 `MEStorage.insert`，
同 tick 内几乎不可能失败。可以退一步，做成「只在源端是 AE 端点时跳过目标模拟」。

### 11.4 无线物流之外（新版整个 tick 的 89%）

| 位置 | 耗时 | 占比 | 说明 |
|---|---|---|---|
| 磁盘 I/O | 5,648 | 12.8% | `saveEverything` → `NbtIo.writeCompressed` 写 level.dat。level.dat 只有 20 KB，但 `CreateFile0` + `write0` 花了约 3 s —— 是 G: 盘的写入延迟。window 4 那一分钟 `tickServer` 12,124 vs 其他 ~7,000、TPS 18.17，就是它在拖 |
| 刷怪 | ~2,872 | 6.5% | `NaturalSpawner` → biome / `getMinBuildHeight` 查询。vanilla，靠 `spawn-limits` / `doMobSpawning` 调 |
| 事件总线 | ~2,500 | 5.7% | `EventBus.post` 1,244 + `ConsumerEventHandler.invoke` 692 + `SubscribeEventListener.invoke` 580 |
| **本模组的一个注入** | **492** | **1.1%** | `EntityGetterMixin` 挂在 `EntityGetter.players()` 上，刷怪时每格每 tick 都调；`hasAnyBeefAdvancedStealthPlayers()` 里两次 `ConcurrentHashMap.isEmpty()`，而 CHM 的 `isEmpty()` 会**遍历整张表**。改成 volatile 标志位即基本免费 |

### 11.5 复现

```bash
python wiki/tools/spark_profile.py <profile>            # 总览 + 无线物流拆解
python wiki/tools/spark_profile.py <profile> --top 30   # 全局自耗时
```

### 11.6 实施记录：P6 / O4b 已落地（2026-09-28）

**改法**：`StaffLinkTargets.transfer` 里算一个 `skipTargetProbe = source.medium().isAe()`，
传给 `moveItems` / `moveFluid`，再传到四个搬运函数。

`skipTargetProbe` 为真时（源端是 ME 网络），一次搬运从**四次调用**变成**两次**：

| | 默认（先模拟后提交） | 源端是 AE |
|---|---|---|
| 1 | 源试算 `extractMatching(..., true)` | — |
| 2 | 目标试算 `insert(..., true)` | — |
| 3 | 源抽 `extractMatching(..., false)` | 源抽 `extractMatching(..., false)` |
| 4 | 目标提交 `insert(..., false)` | 目标收 `insert(..., false)` |

余量处理抽成了 `settleItemLeftover` / `settleFluidLeftover`（退回源，退不回就掉在源脚边），
两条路共用 —— 跳过试算时「抽出来才发现目标不收」是正常分支，不是异常。

**为什么判据是「源端是 AE」而不是「源端或目标端是 AE」**：余量退回的是**源**，风险只取决于
源端收不收得回。源是 ME 网络 ⇒ 一定收得回（同一 tick 内）；源是普通容器 ⇒ 有可能塞不回
（机器的输出槽只出不进），那时东西会掉在源脚边。**目标端是不是 AE 与这条底线无关**，
所以不看它 —— 用户最初的说法是「输入进 AE 或从 AE 输出都可以不用模拟」，
按目标端判断会把「普通容器 → AE 网络」也卷进来，那是唯一有风险的组合。

**预期**：4,732 → **~2,550**（目标端 insert 3,912→~1,956，源端 extractMatching 320→~160）。

**顺带作废了 P5（缓存 `AEItemKey`）**：原本想缓存 `AEItemKey.of(template)` 的结果，
但 P6 正好把「同一次搬运里同一个 key 算两遍」这件事消掉了 —— 现在每个端点实例每次搬运只算一次，
缓存永远命不中。**所以 P5 不做了。**

**P4 在 P6 之后仍有价值**：目标端切「AE 物品」可再省 `Platform.copyStackWithSize`（1,064→~532）
+ `GenericStackInv.insert`（776→~388）≈ **920**，2,550 → ~1,630。

**验证**：

```bash
./gradlew --stop; ./gradlew compileJava processResources jar --no-daemon   # BUILD SUCCESSFUL
javap -p <jar> StaffLinkTargets   # moveOne* 都带 boolean 参数，settle*Leftover 在
```

产物 `build/libs/useless_mod-1.21.1-2.4.4.jar`（20:53，3706140 字节）。

### 11.7 P6 实测 + 目标端「白问一次」的修法（2026-09-28 第六轮采样）

`WrdepzSsAw`（21:05）= P1/P2/P3 + P6 的版本（`build/libs/...2.4.4.jar`，20:53）。
判定依据：profile 里出现 `StaffLinkTargets.settleItemLeftover` —— 只有 20:53 那版有这个帧。

| | 8a（P1/P2/P3） | Wrd（+P6） | Δ |
|---|---|---|---|
| `onStaffLinkTick` | 4,732 ms | **3,568 ms** | −24.6% |
| 每个 60 s 稳态窗 | ~874 ms | **~712 ms** | −18.5% |
| `ItemEndpoint.extractMatching` | 320 | **156** | **−51%** ✓ 与预测一致 |
| `LongResourceAdapters$1.insert` | 3,912 | 2,968 | −24% |
| └ `GenericStackItemStorage.insertItem` | 3,792 | 2,876 | −24% |
| &nbsp;&nbsp;└ `AEItemKey.of` | 1,952 | 1,332 | −32% |
| &nbsp;&nbsp;└ `Platform.copyStackWithSize` | 1,064 | 856 | −20% |
| &nbsp;&nbsp;└ `GenericStackInv.insert` | 776 | 688 | −11% |
| `StaffLinkTargets.settleItemLeftover` | — | 116 | 新增（余量退回源） |
| └ `ItemEndpoint.insert` | — | 116 | 退回 ME 网络的实测代价 |

**源端按预期减半（−51%），目标端只降 24%** —— 这就是总收益没到预期 −46% 的原因。

#### 为什么目标端没减半

`LongResourceAdapters.items().insert` 的内层 `while` 会**对同一个槽位问两次**：

```java
for (int slot = 0; ...) {
    while (inserted < amount && steps < MAX_CHUNK_STEPS) {
        int chunk = (int) Math.min(amount - inserted, Integer.MAX_VALUE);
        offer.setCount(chunk);
        ItemStack leftover = handler.insertItem(slot, offer, simulate);
        long accepted = chunk - leftover.getCount();
        if (accepted <= 0L) break;      // ← 第二次问才会跳到这里
        inserted += accepted;
        if (simulate) break;            // ← 只有模拟路径在这里跳
    }
}
```

`IItemHandler#insertItem` 的契约是「尽量往这个槽里塞，塞不下的退回来」，所以**收到的比给出去的少
就说明槽满了** —— 拿剩下的量再问同一个槽必然被拒。以 9 格的 ME 接口、数量 1600、每格 64 为例：
每个槽 2 次 `insertItem`（1 次有用 + 1 次白问）。

去掉 P6 之前是「模拟 9 次 + 提交 18 次 = 27 次」，去掉后是 18 次 → 只降 33%，
正是实测的 −24% ~ −32%。

#### 修法（已实施）

每个槽位只问一次：

```java
for (int slot = 0; slot < handler.getSlots() && inserted < amount; slot++) {
    int chunk = (int) Math.min(amount - inserted, Integer.MAX_VALUE);
    offer.setCount(chunk);
    ItemStack leftover = handler.insertItem(slot, offer, simulate);
    long accepted = chunk - leftover.getCount();
    if (accepted <= 0L) continue;       // 这个槽不收，换下一个
    inserted += accepted;
}
```

收到的正好等于给出去的 ⇒ `inserted == amount` ⇒ 外层条件自然不成立，也不需要再问。
所以「每槽一次」在两种情况下都够用，且与旧行为等价。

**预期**：目标端 `insert` 2,968 → ~1,480，`onStaffLinkTick` 3,568 → **~2,100**。

**风险说清楚**：唯一不成立的是「单次调用有上限、但槽还没满」的实现（例如某些背包实现每次只让渡
1 个）。那种实现下本循环会换到下一个槽，单 tick 搬得比旧版少 —— **只是吞吐，不会丢东西，也不会
卡死**（改动只减少循环次数，不可能重新引入 `MAX_CHUNK_STEPS` 注释里记的那次卡死事故）。
真遇到这种容器，把 `LongResourceAdapters.items().insert` 改回带 `MAX_CHUNK_STEPS` 的 `while` 即可。
本模组自己的 `HighStackItemStackHandler` 与 AE2 的 `GenericStackItemStorage` 都已核对过，符合契约。

### 11.8 无线物流之外（Wrd，整个 tick 31,740 ms）

| 位置 | 耗时 | 占比 | 说明 |
|---|---|---|---|
| **Architectury 事件代理** | **2,160** | **6.8%** | `EventHooks.fireServerTickPre` → `dev.architectury.event.forge.EventHandlerImplCommon.event` → `jdk.proxy4.$Proxy297.tick` → Guava `AbstractInvocationHandler.invoke` → `EventFactory.invokeMethod` → **`MethodHandles$Lookup.unreflect`（每次调用重新反射方法）**。Architectury 库的写法问题，不是本模组；要省只能去掉注册该事件的模组 |
| 刷怪 | 2,116 | 6.7% | `NaturalSpawner.isValidSpawnPostitionForType` → `canSpawnMobAt` → `mobsAt` → `getBiome` → `ImposterProtoChunk.getNoiseBiome` → `getMinBuildHeight`。vanilla，靠 `spawn-limits` / `doMobSpawning` 调 |
| 区块 | ~2,800 | 8.8% | `ServerChunkCache.getChunk` 1,260 + `PalettedContainer.get` 1,060 + `ChunkMap.processUnloads` 484 |
| 事件总线 | ~1,900 | 6.0% | `EventBus.post` 640 + `ConsumerEventHandler.invoke` 556 + `SubscribeEventListener.invoke` 364 + `LockHelper.get` 356 |
| **本模组的 stealth 注入** | **276** | **0.87%** | `EntityGetterMixin` 挂在刷怪路径的 `EntityGetter.players()` 上，每次调用都要走 `hasAnyBeefAdvancedStealthPlayers()` 的两次 `SetFromMap.isEmpty()`。**注意：JDK 21 的 `ConcurrentHashMap.isEmpty()` 是 `sumCount() <= 0`，本身已经很便宜**（JDK 8 才是遍历整张表）——这 276 ms 是**调用量**（约 9 千次/tick），不是单次代价。换成 volatile 标志位最多省一半（~0.4%），**不值得**，见 11.9 |
| 磁盘 I/O | **4** | ~0% | 上一轮那 12.8% 是 autosave 那一下（`saveEverything` 写 level.dat），**不是持续问题** |

### 11.9 关于「顺手修 stealth 注入」——更正与结论：**不做**

**先更正一条我自己写错的说法。** 11.8 原先写「`ConcurrentHashMap.isEmpty()` 会遍历整张表」，
那是 **JDK 8** 的实现。JDK 21（NeoForge 1.21.1 要求的版本）里它是：

```java
public boolean isEmpty() {
    return sumCount() <= 0L;   // 读 baseCount + 遍历 counterCells（通常是 null）
}
```

本身已经很便宜（几个纳秒）。所以那 276 ms 不是**单次代价**高，而是**调用量**大
（`NaturalSpawner` 刷怪时每 tick 约 9 千次 `getNearestPlayer`）。
换成单个 volatile 标志位，最多省掉一半 → **~0.4% 的 tick**。

**代价与风险不划算**：

1. 收益只有 ~0.4%，且这一项随刷怪量浮动（另一轮采样是 492 ms / 1.1%）。
2. 唯一安全的改法是「把两个集合的『非空』镜像成一个 `volatile boolean`」，
   而它要在 **7 个改集合的地方**同步刷新（`EventHandler:391/392/648/841` 四个 +
   `setClientBeefAdvancedStealthState` / `clearClientBeefAdvancedStealthStates` 两处内部共三处）。
   漏一处 → 守卫返回过期的 `false` → **过滤被跳过 → 隐身悄悄失效**（怪又能锁定你、
   人又能看见你）。这是个「静默失效」的失败模式，为一个 0.4% 不值得。
3. **不能**图省事把 `Collections.newSetFromMap(new ConcurrentHashMap<>())` 换成普通 `HashSet`：
   `ClientLevelMixin`（客户端线程）也会调 `hasAnyBeefAdvancedStealthPlayers()`，
   而它读的第一个集合 `BEEF_ADVANCED_STEALTH_PLAYERS` 是**服务端线程**在改的
   （单人存档里集成服务端有自己的线程）。跨线程读一个正在 rehash 的 `HashSet`
   会抛 `ConcurrentModificationException` 或读到坏状态 —— 这也正是当初选 CHM 的原因。
   `hasBeefAdvancedStealthItem` 里的 `.contains` 同理。

**顺带确认：改这个不影响玩家保护的效果。** `hasAnyBeefAdvancedStealthPlayers()` 只是三个 mixin
里的**短路判断**（false 就原样返回，不做过滤）；真正「这个玩家要不要隐身」的判定是
`hasBeefAdvancedStealthItem(player)`，与它无关。所以只要返回值相同，行为逐位相同 ——
风险全部来自「镜像标志和集合不同步」，而不是来自判断本身。
