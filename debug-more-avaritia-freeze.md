# Debug Session: more-avaritia-freeze

Status: [OPEN]

## Symptom

安装或触发 More Avaritia 相关逻辑后游戏卡死，日志文件为 `references/latest (1).log`，参考源码为 `references/More-Avaritia-main`。

## Hypotheses

- H1: More Avaritia 的 `InfinitySwordGodItem.onEntitySwing` 在客户端或服务端每次挥动时执行 256 次射线/实体扫描，攻击实体较多时导致单 Tick 卡死。
- H2: More Avaritia 的事件 Tick 补刀列表反复处理实体死亡/移除，和牛排保护恢复逻辑互相拉扯，导致服务端单 Tick 超时。
- H3: 牛排保护状态同步包每 Tick 广播给追踪客户端，玩家较多或实体状态频繁恢复时造成网络/主线程压力。
- H4: 卡死并非 More Avaritia 或牛排保护导致，而是退出/保存世界、JEI、ModernFix 或其他模组在同一时间阻塞。

## Evidence

- `references/latest (1).log` lines 1516-1517 show a kill message immediately before the issue.
- `references/latest (1).log` line 1518 reports `Duplicate entity UUID ... LocalPlayer['123'/3, l='ClientLevel', x=0.50, y=-62.00, z=0.50]`.
- `references/latest (1).log` line 1524 shows the client timed out after JEI shutdown and chunk worker shutdown.
- More Avaritia `removeEntity` player path uses `setPos(-9999,-9999,-9999)` and `setHealth(0)`, but does not directly call player `remove`.

## Confirmed Root Cause

- The client got into a death/respawn-like replacement path, but our client-side `remove` / `setRemoved` protection could block removal of the old `LocalPlayer`.
- Blocking legitimate client-side player removal leaves two client entities with the same UUID, matching the duplicate UUID log and subsequent timeout.

## Fix

- Keep dangerous `setPos` protection active on both client and server.
- Restrict `remove` / `setRemoved` cancellation back to server-side only, so the client can legitimately replace/remove the old local player during respawn/recovery synchronization.
