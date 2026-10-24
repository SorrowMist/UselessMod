# 更新日志

本文件记录 UselessMod **Forge 1.20.1** 分支的版本变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
每个条目附英文对照，分类固定为「新增 / 变更 / 修复 / 性能」四类。
NeoForge 1.21.1 分支的更新记录见 `1.21` 分支的同名文件。

## [Unreleased]

## [20.2.4.7]

本次为 20.2.4.6 的后续修正，重点是让模组在 ATM9 这类整合包的实际模组版本下正常加载与运行。
This is a follow-up to 20.2.4.6, focused on making the mod load and run correctly against the actual mod versions
shipped by modpacks such as ATM9.

### Changed / 变更

- 可选依赖元数据与编译 pin 按整合包实际版本对齐：Mekanism / Generators 10.4.16.80、AppMek 1.4.3、Mekanism Extras 1.5.0+、Botany Pots 13.0.0-13.x
  - Optional dependency metadata and compile pins now match the versions actually shipped by the target pack: Mekanism / Generators 10.4.16.80, AppMek 1.4.3, Mekanism Extras 1.5.0+, Botany Pots 13.0.0-13.x.
- `mekanism_upgrade.max_upgrade` 收敛为单一配置来源，`UpgradeMixin` 同时覆盖枚举构造、`getMax()` 与 NBT 加载直接读取 `maxStack` 的路径，超过 16 的上限不再被钳制回默认值
  - `mekanism_upgrade.max_upgrade` is now a single config source; `UpgradeMixin` covers enum construction, `getMax()` and the direct `maxStack` read used during NBT loading, so values above 16 are no longer clamped back to the default.
- 移除未使用的贴图、遗留的 NeoForge 模板与陈旧的验证产物，减小 jar 体积
  - Removed unused textures, legacy NeoForge templates and stale verification artifacts, reducing jar size.

### Fixed / 修复

- 接受 ATM9 携带的 AdvancedAE 1.2.7 与血魔法 3.3.3-45；AdvancedAE 1.3 专用的反应仓适配器在 1.2.x 上直接跳过，不再抛 `NoSuchFieldError`
  - Accepts the AdvancedAE 1.2.7 and Blood Magic 3.3.3-45 versions shipped by ATM9; the AdvancedAE 1.3-only reaction chamber adapter is skipped on 1.2.x instead of throwing `NoSuchFieldError`.
- 为 Mekanism Extras 1.5 的 plural registries 与 genregistries 钠 API 加上缺失字段守护，字段不存在时不再导致类加载失败
  - Guarded the Mekanism Extras 1.5 plural registries and genregistries naquadah API so an absent field can no longer fail class loading.
- 修复 ExtendedAE 的 `matrix_pattern_count`：在 `exposedInventory` 包装之前调整样板库存，使服务端库存、客户端跟踪与界面三者保持一致
  - Fixed ExtendedAE `matrix_pattern_count` by resizing the pattern inventory before `exposedInventory` wraps it, keeping the server inventory, client tracker and GUI consistent.
- 可选配方适配器与能力类在当前 Forge 环境中不存在时改为 debug 级别跳过，真实调用失败仍会警告一次
  - Optional recipe-adapter and capability classes that are absent from this Forge profile are now skipped at debug level; real invocation failures still warn once.

## [20.2.4.6]

面向 Forge 1.20.1 的首次正式发布。本版本把 1.20.1 线自 `26.6.16` 起整体对齐到 1.21 线的 `2.4.6` 特性树，
补回此前只存在于 1.21 分支的功能，并针对 Forge API、原生配方与 AE2 15 重新适配。
The first official release for Forge 1.20.1, bringing the 1.20.1 line from `26.6.16` fully in line with the `2.4.6`
feature tree of the 1.21 line, restoring features that previously existed only on 1.21 and re-adapting them to the
Forge API, native recipes and AE2 15.

### Added / 新增

- 新增 `/uselessmod patterns repair [preview]` 命令，扫描 AE 网格上已通电的单方块万象炉与多方块 ME 样板总成，按当前配方目录重新匹配并重编码其中失效的万象样板，就地写回原槽位
  - Added the `/uselessmod patterns repair [preview]` command, which rescans powered single-block omniversal furnaces and multiblock ME pattern assemblies on the AE grid, re-matches and re-encodes their invalid omniversal patterns against the current recipe directory, and writes them back in place.
- 新增万象样板转换器，可在样板格式之间就地转换而无需重新编码
  - Added the Universal Pattern Converter, which converts patterns between formats in place without re-encoding.
- 温度调节器从塑料恒温器中独立为单独方块，按设定温度自发光并支持 Jade 显示
  - Split temperature control out of the Plastic Thermostat into a dedicated Temperature Regulator block, with temperature-driven emission and Jade display.
- 杀戮光环新增结算间隔配置项，可调节群体伤害的结算频率
  - Added a configuration option for the Kill Aura settlement interval, controlling how often area damage is applied.
- 生物捕捉写入刷怪蛋时保留目标完整 NBT，并可按配置精确丢弃指定键与数据附件命名空间
  - Creature capture now preserves the target's full NBT in the spawn egg, with configurable stripping of specific keys and attachment namespaces.
- 生物加速新增新生魔艺的德格米、探宝蟹与旋风精灵支持
  - Added Ars Nouveau Drygmy, Alakarkinos and Whirlisprig support to creature time acceleration.
- 新增建筑手杖功能与可选择的扳手模式
  - Added Building Staff functionality and selectable wrench modes.
- 新增血魔法与余烬的动态配方读取，配方随模组版本自动同步而无需等待本模组更新
  - Added dynamic recipe reading for Blood Magic and Embers, so recipes follow those mods' versions without waiting for a mod update.
- 新增 KubeJS 联动，包含高级合金炉配方 Schema 与配方组件
  - Added KubeJS integration, including the Advanced Alloy Furnace recipe schema and recipe components.
- 新增合金炉 BigInteger 配方 API 与 KubeJS 合金炉指南（见 `wiki/`）
  - Added the Alloy Furnace BigInteger recipe API and a KubeJS Alloy Furnace guide (see `wiki/`).

### Changed / 变更

- 连点模式不再限定造化杖，对其它物品同样生效
  - Auto-click mode is no longer limited to the Creation Staff and now works with other items.
- 智能翻倍在改写样板前先经 ECO 公开 API 判断，仅在同步确认作用域内且任务与材料向量匹配时原样保留确认计划
  - Smart doubling now consults the ECO public API first and preserves the confirmed plan verbatim only within the synchronous confirmation scope when the task and material vectors match.
- 重做紧凑型 ECO 单方块交互，接入单方块计算器，并为 C9/F9/L9 补齐任务持久化、样板预览、流体恢复与总览界面
  - Reworked the Compact ECO single-block interaction, wiring in the single-block calculators and adding task persistence, pattern preview, fluid restoration and an overview screen for the C9/F9/L9 compact machines.
- 合金炉界面在高 GUI 缩放下按可用空间缩放，避免槽位与数值溢出
  - The Alloy Furnace screen now scales to the available space at high GUI scales, preventing slot and value overflow.

### Fixed / 修复

- 恢复 Forge 1.20.1 的工具标签兼容，造化杖的挖掘等级与工具判定重新正确
  - Restored Forge 1.20.1 tool tag compatibility, so the Creation Staff's mining level and tool checks work correctly again.
- 恢复批量快捷配方，此前在 1.20.1 上缺失的批量合成与快捷处理重新可用
  - Restored bulk quick recipes; bulk crafting and quick processing that were missing on 1.20.1 are available again.
- 安装 Configured 时恢复其配置界面入口，不再只能编辑配置文件
  - Restored the Configured config screen when Configured is installed, instead of falling back to editing config files.
- 按最低可用 API 设定 Thermal 与 CoFH 的版本下限，避免与过旧版本组合时静默失效
  - Set Thermal and CoFH version lower bounds to the minimum available API, avoiding silent failures when combined with older versions.
- 修复 Forge 1.20.1 上可选模组的联动判定，未安装对应模组时不再触发加载期异常
  - Fixed optional-mod integration detection on Forge 1.20.1; missing mods no longer cause load-time errors.

### Performance / 性能

- 配方缓存键在构造时预先算好哈希，查缓存不再重复遍历三张输入 map
  - Recipe cache keys precompute their hash at construction, so cache lookups no longer re-traverse the three input maps.
- 合金炉在输入未变时跳过重复的配方搜索，并在自动 IO 空转时按 1、2、4、8、16 tick 逐级退避、封顶 20 tick，本机内容变化或切换设置时立即恢复逐 tick 扫描
  - The Alloy Furnace skips repeated recipe searches while its inputs are unchanged, and its auto-IO backs off as 1, 2, 4, 8 and 16 ticks (capped at 20) while idle, returning to every-tick scanning immediately when contents change or a setting is toggled.
