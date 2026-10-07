# 更新日志

本文件记录 UselessMod 的全部版本变更，格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
每个条目附英文对照，分类固定为「新增 / 变更 / 修复 / 性能」四类。

## [Unreleased]

## [2.4.5.9]

### Added / 新增
- 无线物流过滤器新增黑名单模式，白名单只搬运命中项，黑名单则跳过命中项
  - Added a blacklist mode to the Wireless Logistics filter: whitelist moves only matches, blacklist skips them.
- 无线物流的过滤条件可改用其它材料的存量控制，并按输出端与输入端分别测量
  - Wireless Logistics filter conditions can now be driven by the stock of another material, measured separately on the source and destination side.
- 生物捕捉写进刷怪蛋时保留目标完整 NBT，并可按配置精确丢弃指定键与数据附件命名空间
  - Creature capture now preserves the target's full NBT in the spawn egg, with configurable stripping of specific keys and attachment namespaces.
- 生物加速新增新生魔艺的德格米、探宝蟹与旋风精灵支持
  - Added Ars Nouveau Drygmy, Alakarkinos and Whirlisprig support to creature time acceleration.
- 温控从塑料恒温器独立为温度调节器方块，按设定温度自发光并支持 Jade 显示
  - Split temperature control out of the Plastic Thermostat into a dedicated Temperature Regulator block, with temperature-driven emission and Jade display.
- 新增杀戮光环结算间隔配置项
  - Added a configuration option for the Kill Aura settlement interval.

### Changed / 变更
- 连点模式不再限定造化杖，对其它物品同样生效
  - Auto-click mode is no longer limited to the Creation Staff and now works with other items.
- 杀戮光环不再要求主手持有造化杖，结算期间临时将杖顶到主手以满足依赖主手的逻辑
  - Kill Aura no longer requires the Creation Staff in the main hand; the staff is temporarily moved there during settlement for logic that reads the main hand.
- 优化造化杖的背包扫描逻辑
  - Optimized the Creation Staff inventory scanning logic.
- 塑料恒温器相关类、资源与网络包统一重命名为通用恒温器
  - Renamed the Plastic Thermostat classes, resources and network packets to the generic Thermostat naming.

### Performance / 性能
- 无线物流空转线路启用指数退避，降低空通道的持续开销
  - Enabled exponential backoff for idle Wireless Logistics routes to reduce the overhead of empty channels.

## [2.4.5.8]

### Added / 新增
- 为 PneumaticCraft 气动方块添加无线连接防漏气兼容
  - Added leak-proof wireless connection compatibility for PneumaticCraft pneumatic blocks.
- 玩家保护状态下追加着火免疫
  - Added fire immunity while Player Protection is active.

### Changed / 变更
- 将 Ex Deorum 相关守卫上移至调用方，并合并重复的兼容实现
  - Moved the Ex Deorum guards up to the call sites and merged the duplicated compatibility implementations.

### Fixed / 修复
- 解除 FTB 连锁的形状映射，并修复滚轮切换形状被抢占的问题
  - Removed the FTB chain shape mapping and fixed the scroll-wheel shape switch being preempted.
- 修复浅水挖掘速度被错误乘以 5 的问题
  - Fixed shallow-water mining speed being incorrectly multiplied by 5.

## [2.4.5.7]

### Added / 新增
- 无线物流兼容气动工艺气压（空气流体）
  - Added PneumaticCraft air pressure (air fluid) compatibility to Wireless Logistics.
- 造化杖 Alt 右键可编辑塑料方块的温度选项
  - The Creation Staff can now edit plastic block temperature options with Alt + right-click.

### Changed / 变更
- 更新气动工艺依赖版本
  - Updated the PneumaticCraft dependency version.
- 标注旧存档耐久迁移为可删除的临时措施
  - Documented the legacy save durability migration as a removable temporary measure.

### Fixed / 修复
- 修复造化杖在右键交互后凭空消失的问题
  - Fixed the Creation Staff vanishing after a right-click interaction.
- 修复部分情况下合成卡住的问题
  - Fixed crafting getting stuck in certain cases.

## [2.4.5.6]

### Added / 新增
- 新增造化杖与任意物品合成后为其添加不可破坏属性
  - Added Unbreakable to any item crafted together with the Creation Staff.

## [2.4.5.5]

### Added / 新增
- 造化杖提示框改为分页显示
  - The Creation Staff tooltip now displays in pages.
- 造化杖默认改为时运模式
  - The Creation Staff now defaults to Fortune mode.

### Changed / 变更
- 连锁挖掘改为直接读取 FTB 连锁键，不再自行注册键位
  - Chain mining now reads the FTB chain key directly instead of registering its own keybind.
- 造化杖被排除出 FTB 连锁，并接管其按键与形状
  - The Creation Staff is now excluded from FTB chain mining and takes over its key and shape handling.

### Fixed / 修复
- 修正应力统计页面
  - Fixed the stress statistics page.
- 修复紧凑 F9 的冷却液流体接口
  - Fixed the coolant fluid interface of the Compact F9.
- 转换配方 id 改为内容派生，消除两端下标错位
  - Changed recipe conversion ids to be content-derived, eliminating index misalignment between both sides.
- 未加载 Ex Deorum 时隐藏其专属配置分组
  - Hid the Ex Deorum exclusive config group when the mod is not loaded.

## [2.4.5.4]

### Added / 新增
- 无线物流新增应力支持
  - Added stress support to Wireless Logistics.

### Fixed / 修复
- 修复无线传输错误地与顺手收菜互斥的问题
  - Fixed wireless transmission being incorrectly mutually exclusive with Convenient Harvesting.

## [2.4.5.3]

### Fixed / 修复
- 修复部分情况下输入 AE 物品丢失的问题
  - Fixed AE items being lost on input in certain cases.

## [2.4.5.2]

### Added / 新增
- 催熟支持藤蔓和花，并新增生物加速
  - Ripening now supports vines and flowers, and mob acceleration was added.
- 短距传送支持键盘主键触发
  - Short-range teleport can now be triggered by the keyboard main key.

### Fixed / 修复
- 修复未安装 Ex Deorum 时挖方块掉落消失的问题
  - Fixed block drops disappearing when Ex Deorum is not installed.
- 未手持造化杖时不显示连锁状态面板
  - Hid the chain mining status panel when the Creation Staff is not held.

## [2.4.5]

### Added / 新增
- 新增万象样板的合金炉自动上传链路：编码后自动上传至多方块样板总成
  - Added an automatic Alloy Furnace upload chain for Omni Patterns, uploading to the multiblock Pattern Assembly after encoding.
- 万象样板上传前查重，重复时退回空白样板
  - Omni Patterns are now deduplicated before upload, falling back to a blank Pattern on duplicates.
- 万象样板按模具择优上传到对应的多方块合金炉
  - Omni Patterns are now uploaded to the matching multiblock Alloy Furnace based on their Pattern.
- 万象样板上传后提示目标位置，并支持点击传送
  - After uploading an Omni Pattern, its destination is reported and can be clicked to teleport there.
- 万象样板自动上传改为可配置项并单独成组
  - Made Omni Pattern auto-upload configurable and split it into its own config group.
- 新增植物盆作物配方转换适配器
  - Added a recipe conversion adapter for Botany Pots crops.
- Ex Deorum 新增桶、坩埚、筛子、锤子配方适配
  - Added recipe adaptation for Ex Deorum barrels, crucibles, sieves and hammers.
- 造化杖新增钩子、锤子与压缩锤模式
  - Added Hook, Hammer and Compressed Hammer modes to the Creation Staff.
- 新增造化杖杀戮光环
  - Added a kill aura to the Creation Staff.
- 新增无线物流过滤精细化控制
  - Added fine-grained filtering controls to Wireless Logistics.

### Changed / 变更
- 只读恢复槽位改用未启用样式，并取消悬停高亮
  - Read-only restore slots now use a disabled style and no longer show a hover highlight.
- 无线物流移除红石控制，并进一步优化性能
  - Removed redstone control from Wireless Logistics and further optimized its performance.
- 道路宽度默认改为 1，并随模式切换归一化
  - Changed the default road width to 1 and normalized it when switching modes.
- 迁移 spark_profile.py 出 wiki
  - Moved spark_profile.py out of the wiki.
- 迁移无线物流性能分析报告出 wiki
  - Moved the Wireless Logistics performance analysis report out of the wiki.
- 调整 eco 依赖项
  - Adjusted the eco dependency.
- 更新 eaep 版本
  - Updated the eaep version.
- 为森罗物语厨房声明 1.4.0 最低兼容版本
  - Declared a minimum compatible version of 1.4.0 for the Senluo Story Kitchen.

### Fixed / 修复
- 修复神秘农业精华合成问题
  - Fixed Mystical Agriculture essence crafting.
- 修复紧凑 F9 样板目录同步与依赖声明
  - Fixed the Compact F9 Pattern directory synchronization and dependency declaration.
- 修复紧凑 F9 聚合样板库存视图
  - Fixed the aggregated Pattern inventory view of the Compact F9.
- Ex Deorum 配置键与语言文件键名对齐
  - Aligned the Ex Deorum config keys with the language file keys.
- 修复下拉菜单和搜索高亮被物品或数字遮挡的问题
  - Fixed dropdown menus and search highlights being obscured by items or numbers.

### Performance / 性能
- 优化无线物流的性能开销
  - Optimized the performance overhead of Wireless Logistics.

## [2.4.4.1]

### Added / 新增
- 造化杖配置界面新增原版分组并重新归类模块
  - Added a vanilla group to the Creation Staff config screen and recategorized its modules.
- 新增连锁挖掘形状系统与预览高亮渲染
  - Added a chain mining shape system with preview highlight rendering.

### Fixed / 修复
- 修复打火石无法点燃 TNT、苦力怕与蜡烛蛋糕的问题
  - Fixed flint and steel failing to ignite TNT, creepers and candle cakes.
- 等价组列表空提示改为按列宽折行
  - Made the empty-state hint of the equivalence group list wrap according to column width.
- 修复容器界面输入框吞键与列表空提示压线的问题
  - Fixed the container GUI input box swallowing keystrokes and the empty list hint overlapping the border.
- 修正连锁形状扫描调用已移除的等价组 API
  - Fixed chain shape scanning calling a removed equivalence group API.

## [2.4.4]

### Added / 新增
- 无线物流新增 ME 网络的 EU 支持
  - Added EU support for ME networks to Wireless Logistics.
- 无线物流新增批量选中和编辑
  - Added batch selection and editing to Wireless Logistics.
- 无线物流新增 FTB 队伍共享支持
  - Added FTB team sharing support to Wireless Logistics.
- 造化杖连锁等价组支持在杖内 UI 编辑，并跟随杖子配置导入导出
  - Chain equivalence groups can now be edited in the staff UI and travel with the staff's config import/export.
- 无线物流支持 AE
  - Wireless Logistics now supports AE.

## [2.4.3]

### Added / 新增
- 新增样板搜索框与彩虹高亮，模式按钮与输入框改用 AE 样式
  - Added a Pattern search box with rainbow highlighting, and switched mode buttons and input boxes to AE style.
- 新增星辉魔法配方适配
  - Added Astral Sorcery recipe adaptation.
- 补上星辉魔法配方转换开关配置项
  - Added the missing config toggle for Astral Sorcery recipe conversion.
- 新增神化与 Lychee 铁砧配方转换适配
  - Added recipe conversion adaptation for Apotheosis and Lychee anvils.

### Changed / 变更
- 回退 JDTE 最低版本约束至 0.6.0
  - Reverted the JDTE minimum version constraint to 0.6.0.
- 配方目录改回同步构建并排除地图交易
  - Reverted the recipe directory to synchronous construction and excluded map trades.
- 移除温室适配器中已失效的 botanypots 集成与死代码
  - Removed the obsolete botanypots integration and dead code from the greenhouse adapter.

### Fixed / 修复
- 按类探测区分 Re-Avaritia 与 AvaritiaNeo
  - Distinguished Re-Avaritia from AvaritiaNeo through class detection.

## [2.4.2]

### Added / 新增
- 造化杖新增无线传输能力
  - Added wireless transmission support to the Creation Staff.

### Fixed / 修复
- 修复造化杖无法点出灵火的问题
  - Fixed the Creation Staff failing to ignite Spirit Fire.
- 尝试修复部分模组环境下创造模式切换回生存模式后飞行失效的问题
  - Attempted a fix for flight being disabled after switching from Creative back to Survival with certain mods installed.

## [2.4.1]

### Added / 新增
- 新增强制催熟功能
  - Added a forced ripening feature.
- 造化杖新增催熟与连点功能
  - Added ripening and auto-clicking to the Creation Staff.

### Fixed / 修复
- 尝试修复部分模组环境下创造模式切换回生存模式后飞行失效的问题
  - Attempted a fix for flight being disabled after switching from Creative back to Survival with certain mods installed.

## [2.4.0]

### Added / 新增
- 新增维度方块预览
  - Added a Dimension Block preview.
- 为四类塑料方块添加方块标签
  - Added block tags for the four plastic block types.
- 造化杖新增匠心仪式挎包模式，可从 AE 网络直接摆出整座仪式
  - Added a Crafting Ritual Satchel mode to the Creation Staff that lays out a full ritual from the AE network.
- 造化杖短距传送改为可配置组合键，默认 Shift + 鼠标右键
  - Made the Creation Staff short-range teleport a configurable key combination, defaulting to Shift + right-click.
- 造化杖新增挖掘自动熔炼开关
  - Added a toggle for automatic smelting while mining with the Creation Staff.
- 合金炉支持 Flux Networks 通量粉尘转化（黑曜石模具）
  - The Alloy Furnace now supports Flux Networks flux dust conversion using an obsidian Pattern.
- 新增 EnderIO 火焰合成配方适配器：以水为输入、打火石为模具，无限粉与可疑的种子拆分为独立配方
  - Added an EnderIO fire crafting recipe adapter using water as input and flint and steel as the Pattern, with Infinity Powder and Suspicious Seeds split into separate recipes.
- 合金炉兼容原版钓鱼战利品表与资源蜜蜂的钓鱼配方
  - The Alloy Furnace is now compatible with the vanilla fishing loot table and Resourceful Bees fishing recipes.
- 水晶科技新增水桶模具的水晶生长配方
  - Added a Crystal Growth recipe using a water bucket Pattern for Crystal Technology.
- 将 JDTE 最低版本限制为 0.6.0-pre2，避免旧版本导致服务端崩溃
  - Enforced a minimum JDTE version of 0.6.0-pre2 to prevent server crashes caused by older builds.

### Changed / 变更
- 撤销此前一轮配方加载相关改动
  - Reverted the previous round of recipe loading changes.
- 造化杖短距传送距离改由本模组配置控制
  - Moved the Creation Staff short-range teleport distance under this mod's own configuration.
- 清理代码注释中的口语化表达，统一为专业书面表述
  - Cleaned up colloquial code comments and standardized them to professional written wording.

### Fixed / 修复
- 等待配方目录就绪后再恢复合成任务，避免下单被取消
  - Recipe crafting tasks now resume only after the recipe directory is ready, preventing queued orders from being cancelled.
- 修复紧凑 F9 聚合样板库存视图
  - Fixed the compact F9 aggregated Pattern inventory view.
- 维度配置预览界面不再显示底层方块与背包
  - The dimension config preview no longer renders underlying blocks or the player inventory.
- 修复切换多联模式后维度配置与预览失效的问题
  - Fixed dimension config and preview breaking after switching the multi-link mode.
- 重试并发窗口内的炼金锅配方构建，消除条目静默缺失
  - Retried Alchemy Cauldron recipe building within the concurrency window to eliminate silently missing entries.
- 重试并发窗口内的酿造配方构建，消除目录整段缺失
  - Retried Brewing recipe building within the concurrency window to eliminate whole missing directory segments.
- 跳过藏宝图交易配方，消除配方目录随执行侧漂移
  - Skipped treasure map trade recipes to stop the recipe directory from drifting with the executing side.
- 稳定化注入配方 id 以消除重启后样板失效
  - Stabilized injection recipe ids to prevent Patterns from breaking after a restart.
- 短距传送绑定改挂非激活冲突上下文，恢复方块交互
  - Rebound short-range teleport to a non-conflicting inactive context to restore block interaction.
- 多联模式中心方块改为按填充区居中
  - The multi-link mode center block is now centered on the filled area.
- 消除合金炉在 JEI 注册路径上的渲染线程同步构建
  - Removed the synchronous render-thread build on the Alloy Furnace JEI registration path.
- 回滚合金炉配方加载优化至 2.3.9 基线
  - Rolled the Alloy Furnace recipe loading optimization back to the 2.3.9 baseline.
- 适配 JEI 19.52 新传输入口并修复模具名搜索候选恒为空
  - Adapted to the new JEI 19.52 transfer entrypoint and fixed the Pattern name search always returning empty candidates.
- 修复造化杖药水效果在副手不生效的问题
  - Fixed Creation Staff potion effects not applying when held in the offhand.

### Performance / 性能
- 移除合金炉配方目录构建的临时性能埋点
  - Removed the temporary performance instrumentation from Alloy Furnace recipe directory building.
- 合并酿造试剂集求取逻辑以加速配方构建
  - Merged the Brewing reagent-set lookup logic to speed up recipe building.

## [2.3.10]

### Added / 新增
- 造化杖新增剪刀与打火石开关
  - Added scissor and flint-and-steel toggles to the Creation Staff.
- 补齐 Just Dire Things 配方转换开关并按字母重排配置
  - Completed the Just Dire Things recipe conversion toggle and reordered the config alphabetically.

### Changed / 变更
- 尝试优化
  - Attempted performance optimizations.

### Fixed / 修复
- 修复建筑手杖不能斜对角搭建的问题
  - Fixed the Building Wand failing to place blocks diagonally.
- 修复从创造模式切换回生存模式后手杖飞行失效的问题
  - Fixed wand flight being disabled after switching from Creative back to Survival.
- 修复 Iava 引入的 Placebo 硬依赖问题
  - Fixed the hard Placebo dependency introduced by Iava.
- 修复反应堆配方 id 随重启漂移导致万象样板失效的问题
  - Fixed reactor recipe ids drifting across restarts and breaking Omni Patterns.
- 修复制图师藏宝图配方转换错误
  - Fixed incorrect Cartographer treasure map recipe conversion.

### Performance / 性能
- 优化配方目录构建
  - Optimized recipe directory building.

## [2.3.9]

### Added / 新增
- 新增维度配置导入导出并添加多联模式
  - Added dimension config import/export and a multi-link mode.

### Fixed / 修复
- 修复 Hostile Neural Networks 模型配方无法匹配的问题
  - Fixed Hostile Neural Networks model recipes failing to match.
- 兼容机械动力机械手使用配方
  - Added compatibility for Create mechanical arms using recipes.

## [2.3.8.4]

### Added / 新增
- 增加无限配置
  - Added Infinity configuration.

### Changed / 变更
- 停止同步 .workbuddy-ai 目录到远程
  - Stopped syncing the .workbuddy-ai directory to the remote.
- 移除 net 文件夹的 git 同步
  - Removed git synchronization of the net folder.

### Fixed / 修复
- 修复单总线终端无法连接的问题
  - Fixed the single bus terminal failing to connect.
- 统一范围磁力作用域
  - Unified the area magnet's working range.
- 修复造化杖无法挖掘部分矿物
  - Fixed the Creation Staff failing to mine certain ores.

### Performance / 性能
- 优化大尺寸贴图
  - Optimized large-size textures.

## [2.3.8.3]

### Changed / 变更
- 例行版本发布与内部维护
  - Routine version release and internal maintenance.

## [2.3.8.2]

### Added / 新增
- 增加整批交付 API
  - Added a batch delivery API.

### Changed / 变更
- 更新了 wiki 文件
  - Updated the wiki files.

### Fixed / 修复
- 修复模具集散中心和被动仓重新放置后绑定错误的问题
  - Fixed Pattern hub and passive storage binding errors after being replaced.

## [2.3.8]

### Added / 新增
- 造化杖连接无限频道
  - The Creation Staff can now connect to Infinity channels.
- 数据能源 3.3.0 原生 bigint 接入：单批交付超过 long，退役全部 DE mixin
  - Native bigint integration for Data Energistics 3.3.0: single-batch delivery beyond long, retiring all DE mixins.

### Changed / 变更
- 兼容旧版 DE 不报错
  - Added compatibility so older DE versions no longer throw errors.
- 优化了 bigint 下的自动降频
  - Improved automatic frequency reduction under bigint.
- 优化合成能力
  - Improved crafting capability.
- 更新 bigint 接口
  - Updated the bigint interface.

### Fixed / 修复
- 修复造化杖无法连接部分设备
  - Fixed the Creation Staff failing to connect to certain devices.
- 修复 /reload 崩溃，避免在服务端线程调用 JEI
  - Fixed a /reload crash by avoiding JEI calls on the server thread.
- 修复不读取其他模组村民配方的问题
  - Fixed recipes from other mods' villagers not being read.
- 三位一体大数合成：修复合成样板折叠与大数账目，并放开每 tick 窗口数
  - Trinity large-number crafting: fixed Pattern folding and large-number accounting, and lifted the per-tick window limit.

## [2.3.7.2]

### Added / 新增
- 增加单方块 eco
  - Added single-block eco.
- 增加右键收菜
  - Added right-click crop harvesting.
- 增加铲子锄头优先级配置
  - Added priority configuration for shovels and hoes.
- 新增被动仓单独配置倍率
  - Added a separately configurable multiplier for passive storage.
- 添加 JDTE 生命合成舱、生物工厂、温室大棚配方兼容
  - Added recipe compatibility for JDTE Life Synthesis Chambers, Bio Factories, and Greenhouses.
- 添加对原版村民、流浪者交易配方的兼容
  - Added compatibility with vanilla villager and wandering trader trade recipes.

### Fixed / 修复
- 修复建筑手杖模式放置物品时 NBT 丢失的问题
  - Fixed NBT loss when placing items in Building Wand mode.

## [2.3.7.1]

### Added / 新增
- 添加通量神化附属的聚灵配方兼容，并在该附属存在时兼容神化灌注附魔配方
  - Added spirit gathering recipe compatibility for the Flux Apotheosis addon, and Apotheosis infusion enchanting compatibility when that addon is present.
- 兼容 AE 世界交互配方
  - Added AE world interaction recipe compatibility.
- 兼容充能器配方
  - Added Charger recipe compatibility.
- 兼容水晶修复器配方
  - Added Crystal Fixer recipe compatibility.
- 适配 4096 槽位的分页界面
  - Adapted the paged interface for 4096 slots.

### Changed / 变更
- 修正了 eco 版本要求
  - Corrected the required eco version.

### Fixed / 修复
- 修复维度方块的配置问题
  - Fixed Dimension Block configuration issues.
- 修复越界问题
  - Fixed an out-of-bounds issue.

## [2.3.7]

### Added / 新增
- 万象炉与各舱室改用外置存储，并支持为每个舱室单独配置容量上限
  - The Universal Furnace and its chambers now use external storage, with configurable capacity limits for each chamber.
- 新增神秘农业（Mystical Agriculture）Omnia 兼容
  - Added Mystical Agriculture Omnia compatibility.
- 补充此前遗漏的敌对神经网络（Hostile Neural Networks）兼容配方
  - Added previously missing Hostile Neural Networks compatibility recipes.
- 砧板新增更多模组配方兼容
  - Added more mod recipe compatibility to the Cutting Board.
- 万象合金炉支持自定义合成配方，并新增 Eco 模组最低版本要求
  - The Universal Alloy Furnace now supports custom crafting recipes, and a minimum Eco mod version is now required.

### Changed / 变更
- 槽位可配置上限提升至 4096
  - The configurable slot limit has been raised to 4096.

## [2.3.6.4]

### Added / 新增
- 合金炉新增闪电科技「苍穹粉」「漂浮物质」与「研究笔记」配方
  - Added Lightning Tech Sky Powder, Floating Matter and Research Notes recipes to the Alloy Furnace.
- 万象转换器支持转化合成样板
  - The Universal Converter can now convert crafting patterns.
- 万象转换器支持绑定万象炉
  - The Universal Converter can now be bound to the Universal Furnace.
- 新增更多敌对神经网络兼容配方
  - Added more Hostile Neural Networks compatibility recipes.

### Fixed / 修复
- 修正 Mekanism 加速倍率
  - Corrected the Mekanism speed-up multiplier.

### Performance / 性能
- 优化小炉子取消任务时的退料逻辑
  - Optimized material refund logic when a Small Furnace task is cancelled.

## [2.3.6.3]

### Added / 新增
- 新增 MEK 硅岩反应兼容
  - Added MEK Naquadah reaction compatibility.
- 新增敌对神经网络配方兼容
  - Added Hostile Neural Networks recipe compatibility.

### Changed / 变更
- 内置材质包更新至版本 2.6
  - Updated the bundled resource pack to version 2.6.

### Fixed / 修复
- 修复神秘学部分配方无法生成万象样板的问题
  - Fixed some Occultism recipes failing to generate Universal Patterns.
- 修复万象样板对神秘学绑定之书需重新加载才能忽略 NBT 的问题
  - Fixed Universal Patterns requiring a reload before ignoring NBT on the Occultism binding book.
- 修复服务端崩溃
  - Fixed a server-side crash.
- 修复关闭扳手模式的造化杖无法作为模具使用的问题
  - Fixed the Creation Staff being unusable as a mold when wrench mode is disabled.

## [2.3.6.2]

### Added / 新增
- 新增斩首模式，并为精魂与觉醒粉掉落增加独立开关
  - Added a Decapitation mode and separate toggles for Soul and Awakened Powder drops.

## [2.3.6.1]

### Fixed / 修复
- 修复无尽贪婪（Avaritia）配方问题
  - Fixed Avaritia recipe issues.
- 修复界面尺寸为 4 时牛排工具配置页面显示异常的问题
  - Fixed the Steak Tool config page rendering incorrectly at GUI scale 4.
- 修复无法编码万象样板的问题
  - Fixed the inability to encode Universal Patterns.

## [2.3.6]

### Added / 新增
- 新增造化杖自定义配置界面
  - Added a custom configuration screen for the Creation Staff.
- 兼容 JDTE 高级灌注配方、EIO 灵魂瓶配方、铁魔法炼金炉配方，以及资源蜜蜂的繁殖与转化配方
  - Added compatibility for JDTE advanced infusion recipes, EIO soul vial recipes, Iron's Spells alchemy furnace recipes, and Resourceful Bees breeding and conversion recipes.
- 新增对 Mekanism 融合器与回收机配方的兼容
  - Added recipe compatibility for the Mekanism Fusion Reactor and Recycler.
- 新增对 MekMM 环境气体收集器配方的兼容
  - Added recipe compatibility for the MekMM Environmental Gas Collector.
- 在开发环境中加入全能工具
  - Added the Omni Tool in the development environment.

### Changed / 变更
- 加强无用维度的配置选项功能
  - Enhanced the configuration options for the Useless Dimension.
- 在样板编码期间阻止配方目录重建
  - Prevented recipe registry rebuilding during pattern encoding.
- 修复并加强 KubeJS 联动
  - Fixed and enhanced KubeJS integration.
- 优化强制击杀逻辑
  - Refined the forced-kill logic.
- 将基础玩家保护与高级隐身拆分为独立功能
  - Split basic Player Protection and Advanced Invisibility into separate features.
- 修改造化杖配置界面，并尝试修复玩家保护的若干漏洞
  - Reworked the Creation Staff config UI and attempted to fix several Player Protection exploits.
- 更新 KubeJS 联动，并补充 JDT 凝聚蔓延配方
  - Updated KubeJS integration and added JDT Coalescence Spread recipes.

### Fixed / 修复
- 修复 Blood Mending 静态配方组件编码问题
  - Fixed static recipe component encoding for Blood Mending.
- 修复建筑手杖模式下副手方块优先级不生效的问题
  - Fixed off-hand block priority not taking effect in Building Staff mode.
- 修复天使核心缺少放置预览的问题
  - Fixed the missing placement preview for the Angel Core.

## [2.3.5.2]

### Fixed / 修复
- 修复建筑手杖模式轮盘缺失的问题
  - Fixed the missing radial menu for Building Staff mode.

## [2.3.5.1]

### Added / 新增
- 新增万象炉各等级的配置项
  - Added configuration options for each Universal Furnace tier.

### Fixed / 修复
- 修复轮盘错位的问题
  - Fixed the radial menu being misaligned.

## [2.3.5]

### Added / 新增
- 打草有几率掉落造化杖彩蛋
  - Added an easter egg where cutting grass can drop a Creation Staff.
- 兼容灵灾的哭泣之井与召唤祭坛配方
  - Added compatibility for the Lament's Well and Summoning Altar recipes from the Calamity mod.
- 新增 KubeJS 联动
  - Added KubeJS integration.
- 新增扳手模式可选项与建筑手杖功能
  - Added selectable wrench modes and Building Staff functionality.
- 新增万象样板转换器
  - Added the Universal Pattern Converter.
- JEI 中可查看配方 ID，并新增万象炉配方阶级限制配置
  - Recipe IDs are now viewable in JEI, and a config option for Universal Furnace recipe tier limits was added.
- 造化垂青之杖新增范围伤害与击杀磁力，合金炉适配工业先锋镭射钻
  - The Creation Favor Staff gained area damage and kill magnetism, and the Alloy Furnace now supports the Industrial Foregoing Laser Drill.
- 连锁挖掘支持等价组配置
  - Chain mining now supports equivalent-group configuration.
- 造化杖支持传送，并适配旅行锚
  - The Creation Staff now supports teleportation and works with the Travel Anchor.
- AE 大礼包支持配置，并优化造化杖药水配置界面
  - The AE gift pack is now configurable, and the Creation Staff potion config screen was improved.
- 适配工业先锋更多升级精密工作台、流体工作台与灌注机
  - Added support for the Industrial Foregoing Advanced Precision Crafting Table, Fluid Crafting Table and Infuser.
- 适配工业先锋生物质炉、发酵站、流体筛分机、洗矿厂、乳胶加工机与流体提取机的配方
  - Added recipe support for the Industrial Foregoing Bioreactor, Fermentation Station, Fluid Sieving Machine, Ore Washer, Latex Processing Unit and Fluid Extractor.
- 新增森罗物语及其附属模组兼容
  - Added compatibility for Shinro Monogatari and its addons.
- 新增强制破坏黑名单，支持方块 ID、#方块标签与 * 通配符
  - Added a forced-break blacklist supporting block IDs, #block tags and * wildcards.

### Changed / 变更
- 兼容无尽贪婪重生配方，并修复 AE 大礼包问题
  - Added Infinity Reborn recipe compatibility and fixed issues with the AE gift pack.
- 调整配置文件归属
  - Adjusted config file organization.
- 更新繁体中文（zh_tw）翻译
  - Updated the Traditional Chinese (zh_tw) translation.
- 合并上游 1.21 分支更新，并移除项目测试与测试构建配置
  - Merged upstream 1.21 branch updates and removed the project's test and test-build configuration.

### Fixed / 修复
- 修复 KubeJS 联动问题
  - Fixed KubeJS integration issues.
- 修复 Mekanism 最大升级上限失效的问题
  - Fixed the Mekanism maximum upgrade limit not being enforced.
- 修复造化杖掠夺附魔不更新的问题
  - Fixed the Creation Staff's Looting enchantment not updating.
- 修复高级合金炉批量预检遗漏 AE 物品输入的问题
  - Fixed batch pre-checks on the Advanced Alloy Furnace omitting AE item inputs.
- 修复万象炉超大批次材料展开的问题
  - Fixed material expansion for very large Universal Furnace batches.
- 补全万象炉长数量配方匹配与本地回退
  - Completed long-count recipe matching and local fallback for the Universal Furnace.
- 修复工作流问题
  - Fixed workflow issues.

### Performance / 性能
- 优化合金炉配方性能
  - Optimized Alloy Furnace recipe performance.

## [2.3.4]

### Added / 新增
- 新增农夫乐事及其附属模组兼容
  - Added compatibility for Farmer's Delight and its addons.
- 新增维度白名单设置
  - Added a dimension whitelist setting.

### Changed / 变更
- 更新监工方块，并修改监工模型贴图
  - Updated the Overseer block and revised the Overseer model textures.
- 更新模组信息
  - Updated mod metadata.

## [2.3.3]

### Added / 新增
- 新增维度方块黑名单设置
  - Added a dimension block blacklist setting.
- 新增塑料连接材质及其非荧光变种
  - Added plastic connected textures and their non-glowing variants.
- 新增造化杖加速功能
  - Added a speed-up feature to the Creation Staff.
- 舱室现在会记住上次打开的页码
  - Chambers now remember the last page you had open.

### Changed / 变更
- 调整 JEI 布局
  - Adjusted the JEI layout.
- 本模组不再会被 item-obliterator 禁用
  - The mod is no longer disabled by item-obliterator.

### Fixed / 修复
- 修复沉浸工程灌装机部分配方无法转化的问题
  - Fixed some Immersive Engineering Bottling Machine recipes failing to convert.
- 修复连接材质显示异常的问题
  - Fixed connected texture display issues.

## [2.3.2]

### Changed / 变更
- 进一步加强玩家保护状态下的不可见性
  - Further strengthened invisibility while Player Protection is active.
- 开启玩家保护后将免疫负面状态
  - Enabling Player Protection now grants immunity to negative status effects.

### Fixed / 修复
- 修复动态匹配配方不支持超过 int 范围数值的问题
  - Fixed dynamic recipe matching failing on values beyond the int range.
- 修复流体数量超过 int 范围时无法合成的问题
  - Fixed crafting failing when fluid amounts exceeded the int range.
- 修复部分情况下开启玩家保护后仍会被生物索敌的问题
  - Fixed mobs still targeting the player in some cases while Player Protection was enabled.

## [2.3.1]

### Changed / 变更
- 例行版本发布与内部维护
  - Routine version release and internal maintenance.

## [2.3.0]

### Added / 新增
- 万象炉新增高炉与烟熏炉配方兼容
  - Added Blast Furnace and Smoker recipe compatibility for the Omni Furnace.
- 新增 EAEP 映射搜索兼容
  - Added EAEP mapping search compatibility.
- 新增配方兼容 API 接口，便于其他模组接入配方转换
  - Added a recipe compatibility API for other mods to hook into recipe conversion.
- 新增极限反应堆兼容
  - Added Extreme Reactors compatibility.
- 新增气动工艺兼容
  - Added PneumaticCraft compatibility.
- 新增沉浸工程兼容
  - Added Immersive Engineering compatibility.
- 新增现代工业化兼容
  - Added Modern Industrialization compatibility.
- 新增 UFO 兼容
  - Added UFO compatibility.
- 新增 Neo Vitae 兼容
  - Added Neo Vitae compatibility.
- 新增原版炼药台兼容
  - Added vanilla Brewing Stand compatibility.
- 新增奥瑞科技兼容，并支持配方转化配置
  - Added Aorui Tech compatibility together with recipe conversion configuration.

### Fixed / 修复
- 修复现代工业化中无输入的部分配方无法处理的问题
  - Fixed some Modern Industrialization recipes without inputs failing to process.
- 修复多方块万象炉线圈处理时间减免未生效的问题
  - Fixed the multiblock Omni Furnace coil time reduction not taking effect.

## [2.2.5]

### Added / 新增
- 维度方块新增配置界面 UI
  - Added a configuration screen for the Dimension Block.

### Changed / 变更
- 造化杖基础伤害现在会随万象炉样板总数变化
  - The Creation Staff's base damage now scales with the total number of Omni Furnace patterns.

### Fixed / 修复
- 修复部分样板只能生成万象样板的问题
  - Fixed some patterns only being able to produce Omni Patterns.

## [2.2.4]

### Added / 新增
- 新增机械动力兼容
  - Added Create compatibility.
- 万象炉新增 tag 匹配支持
  - Added tag matching support for the Omni Furnace.
- 万象炉新增多模具支持
  - Added multi-mold support for the Omni Furnace.
- 新增矿石生成器 UI
  - Added a UI for the Ore Generator.
- 支持三位一体按万象模具检索样板
  - Trinity now supports searching patterns by Omni mold.
- 数据能源终端支持保留万象样板模具
  - The Data Energistics terminal now retains Omni Pattern molds.
- 新增 Data Energistics 合金炉样板供应器集成，并适配 3.0.2 重组器配方 API
  - Added a Data Energistics Alloy Furnace pattern provider integration and adapted to the 3.0.2 Recombinator recipe API.
- 万象样板支持手动取消副产物
  - Omni Patterns now allow byproducts to be cancelled manually.
- 新增 Mekanism 全量兼容与禁忌与奥秘兼容
  - Added full Mekanism compatibility and Forbidden Arcanus compatibility.
- 新增 EIO 兼容
  - Added Ender IO compatibility.
- 兼容闪电科技的粉末配方
  - Added compatibility with Lightning Tech powder recipes.
- 新增静态工作台配方兼容
  - Added Static Workbench recipe compatibility.
- 补充万象样板的数据能源集成测试与样板供应器集成测试
  - Added integration tests for Omni Patterns with Data Energistics and its pattern provider.

### Changed / 变更
- 更新 Data Energistics 依赖至 3.0.2
  - Updated the Data Energistics dependency to 3.0.2.
- 优化强制击杀逻辑
  - Reworked the forced-kill logic.
- 常规更新维护
  - Routine update and maintenance.

### Fixed / 修复
- 修复 Mekanism 更新导致氧化机配方无法编码为万象样板的问题
  - Fixed Mekanism updates causing Oxidizer recipes to fail when encoded as Omni Patterns.
- 修复 ECO 部分转化配方丢失的问题
  - Fixed some ECO conversion recipes being lost.
- 修复神秘学部分配方无法生成万象样板的问题
  - Fixed some Occultism recipes failing to generate Omni Patterns.
- 修复神秘学部分生物无工作的问题
  - Fixed some Occultism creatures not performing work.
- 修复转化配方部分失败导致 JEI 配方全部丢失的问题
  - Fixed a single conversion recipe failure wiping all JEI recipes.
- 修复玩家保护可能引起的内存溢出
  - Fixed a potential memory overflow caused by Player Protection.
- 修复样板管理终端查看样板总成时卡顿的问题
  - Fixed lag when viewing pattern assemblies in the Pattern Management Terminal.
- 修复裂变堆流体输入逻辑错误的问题
  - Fixed incorrect fluid input logic in the Fission Reactor.
- 修复 Mekanism 升级配置失效的问题
  - Fixed Mekanism upgrade configuration not taking effect.
- 修复万象样板解析导致的卡顿问题
  - Fixed lag caused by Omni Pattern parsing.
- 修复单方块万象炉样板加载时机的问题
  - Fixed the pattern loading timing of the single-block Omni Furnace.
- 修复神秘学矿工无法生成万象样板的问题
  - Fixed Occultism miners failing to generate Omni Patterns.

### Performance / 性能
- 优化配方加载卡顿
  - Reduced stutter when loading recipes.

## [2.2.3]

### Added / 新增
- 新增灵灾兼容
  - Added Lingzai compatibility.
- 新增神秘学兼容
  - Added Occultism compatibility.
- 新增自然灵气兼容
  - Added Nature's Aura compatibility.
- 新增 ECO 兼容
  - Added ECO compatibility.
- 新增单方块 UI 界面右键回退
  - Added right-click rollback in the single-block UI.
- 造化杖新增捕捉生物刷怪蛋功能
  - The Creation Staff can now capture creatures into spawn eggs.
- 造化杖新增掉落觉醒粉与精魂的功能
  - The Creation Staff can now drop Awakening Powder and Souls.
- 新增被动合成仓
  - Added the Passive Crafting Chamber.
- 初步构造多方块万象炉
  - Added the initial multiblock Omni Furnace.
- 舱室被挖掘后保留内容物
  - Chambers now keep their contents when mined.

### Changed / 变更
- 更新万象样板材质与有用线圈材质，并新增多方块材质
  - Updated Omni Pattern and Useful Coil textures and added multiblock textures.
- 将智能倍增改为自我实现
  - Reimplemented smart doubling as a self-contained implementation.
- 加强多方块线圈能力
  - Strengthened multiblock coil capabilities.
- 补充上次提交遗漏的内容
  - Followed up on changes missed in the previous commit.

### Fixed / 修复
- 修复未安装资源蜜蜂时生物捕捉功能崩溃的问题
  - Fixed creature capture crashing when Resourceful Bees was not installed.
- 修复某些情况下关闭玩家保护后玩家仍然无敌的问题
  - Fixed the player staying invincible in some cases after disabling Player Protection.
- 修复 ECO 导致智能翻倍失效的问题
  - Fixed ECO breaking smart doubling.
- 修复造化杖长按左键错误触发强制效果的问题
  - Fixed holding left-click with the Creation Staff incorrectly triggering the forced effect.
- 修复被动仓远超 int 并行时卡死的问题
  - Fixed freezes when the Passive Chamber ran far beyond int-range parallelism.
- 修复神秘学随机召唤的问题
  - Fixed random summoning in Occultism.
- 修复多方块合金炉无法合成九阶的问题
  - Fixed the multiblock Alloy Furnace failing to craft tier nine.
- 修复卡死问题与单方块偷鸡问题
  - Fixed freeze issues and single-block exploits.
- 修复多方块 UI 问题
  - Fixed multiblock UI issues.
- 修复 XY 右键绑定冲突
  - Fixed a right-click key binding conflict on the X and Y keys.
- 修复数字格式显示错误
  - Fixed incorrect number formatting.
- 修复超 int 数值导致卡死的问题
  - Fixed freezes caused by values beyond the int range.
- 修复 EAEP 中键下单冲突
  - Fixed a middle-click ordering conflict with EAEP.

### Performance / 性能
- 优化被动仓性能表现
  - Improved Passive Chamber performance.

## [2.2.2]

### Added / 新增
- 新增 Powah 与合成拓展兼容
  - Added Powah and Crafting Expansion compatibility.
- 新增龙之进化支持
  - Added Draconic Evolution support.
- 支持有用锭将能量上限提升至 long 范围
  - Useful Ingots can now raise the energy cap to the long range.
- 合金炉新增资源蜜蜂支持
  - Added Resourceful Bees support for the Alloy Furnace.

### Changed / 变更
- 更新繁体中文（zh_tw）翻译
  - Updated the Traditional Chinese (zh_tw) translation.

### Fixed / 修复
- 修复部分情况下龙之进化无法识别的问题
  - Fixed Draconic Evolution not being detected in some cases.
- 修复部分情况下下单会崩溃的问题
  - Fixed crashes when placing orders in some cases.
- 修复龙之进化合成、右键拆卸访问点以及无法挖掘萤石等物品的问题
  - Fixed Draconic Evolution crafting, right-click access point removal, and the inability to mine blocks such as Glowstone.

## [2.2.1]

### Added / 新增
- 万象炉新增 AE 网络抽电，可自动抽取 AppFlux 通量储电，并可选择抽取 AE 原生能量
  - The Omni Furnace can now draw power from the AE network, automatically pulling AppFlux flux storage with an option to draw native AE energy.
- 新增牛排工具飞行速度配置项，可在 0.01 到 1.0 之间调整，默认 0.05
  - Added a flight speed option for the Steak Tool, adjustable from 0.01 to 1.0 with a default of 0.05.
- 新增玩家保护开关选项
  - Added a toggle option for Player Protection.
- 新增无线终端依赖
  - Added the Wireless Terminal dependency.
- 新增造化杖强制击杀与触及距离配置
  - Added configuration for the Creation Staff's forced kill and reach distance.
- 造化杖放在背包内时玩家获得无敌
  - The player is now invincible while the Creation Staff is in their inventory.

### Changed / 变更
- 更新翻译文件
  - Updated translation files.
- 强化造化杖无敌效果并进一步加强无敌表现
  - Strengthened the Creation Staff invincibility and further reinforced overall invincibility.
- 简化挖掘相关代码，经验值改为直接吸收
  - Simplified the mining code so experience is absorbed directly.
- 移除调试（dbg）文件
  - Removed debug (dbg) files.

### Fixed / 修复
- 修复合金炉配方识别问题与部分配方不识别的问题
  - Fixed Alloy Furnace recipe detection and some recipes not being recognized.
- 修复串配方与挖掘红石的问题
  - Fixed cross-recipe issues and Redstone mining problems.
- 修复无线终端依赖
  - Fixed the Wireless Terminal dependency.
- 修复万象炉 AE 任务材料丢失或复制、能耗结算错误以及超大批量合成失败的问题
  - Fixed AE task material loss or duplication, incorrect energy accounting, and failed very large batch crafts in the Omni Furnace.

## [2.2.0]

### Added / 新增
- 万象炉新增 Mekanism 锇压缩机、粉碎机、锯木厂与闪电科技苍穹转换核心的配方支持
  - Added recipe support for the Mekanism Osmium Compressor, Crusher, Sawmill, and the Lightning Tech Sky Conversion Core in the Omni Furnace.

### Changed / 变更
- 移除速度升级 time() 的硬截断下限，改用 isFinite 防御 NaN/Infinity
  - Removed the hard truncation floor of time() in speed upgrades and switched to isFinite to guard against NaN/Infinity.

### Fixed / 修复
- 修复强制挖掘被削弱的问题
  - Fixed forced mining being weakened.
- 修复 R 键强制挖掘导致 AE2 系列母岩与陨石方块退化的问题，新增降级掉落检测并在触发时回退为掉落方块本身
  - Fixed R-key forced mining degrading AE2 bedrock and meteorite blocks, adding downgraded-drop detection that falls back to dropping the block itself.

## [2.1.0]

### Added / 新增
- 合金炉 GUI 新增「取消 AE 任务」与「产物回送 AE」按钮，并调整能量条与红石按钮位置
  - Added "Cancel AE Task" and "Return Output to AE" buttons to the Alloy Furnace GUI and repositioned the energy bar and Redstone button.
- 适配 EAP 智能翻倍
  - Added EAP smart doubling support.
- 万象炉新增 tooltips 提示，且使用有用锭时不再产生额外能量消耗
  - Added tooltips to the Omni Furnace and stopped Useful Ingots from causing extra energy consumption.
- 新增红石控制功能
  - Added Redstone control.
- 新增输入输出配置功能
  - Added input/output configuration.
- 新增 bedrock_at_bottom 配置项，可设置基岩层是否固定生成在世界最底层，并补充中英文翻译
  - Added the bedrock_at_bottom option to control whether the bedrock layer always generates at the bottom of the world, with Chinese and English translations.
- 合金炉支持重命名
  - The Alloy Furnace can now be renamed.
- 添加 data-energistics 支持并调整 UI 布局
  - Added Data Energistics support and adjusted the UI layout.
- 万象合金炉新增神秘农业、新生魔艺、闪电科技与水晶科技的配方支持
  - Added Mystical Agriculture, Ars Nouveau, Lightning Tech, and Crystal Tech recipe support for the Omni Alloy Furnace.
- 添加样板槽专用物品处理类，仅允许放置 AE 编码样板
  - Added a dedicated handler for pattern slots that only accepts AE encoded patterns.
- 新增 AE 网络集成，高级合金炉可接入 AE 网络自动合成
  - Added AE network integration so the Advanced Alloy Furnace can autocraft through the AE network.
- 新增 AE2 大礼包物品，仅在加载 AE2 时注册，并补充材质、模型与中英文本地化
  - Added the AE2 Gift Pack item, registered only when AE2 is loaded, with textures, models, and Chinese/English localization.
- 牛排工具新增 buff、飞行、按键绑定与专用 Component
  - Added buffs, flight, key bindings, and a dedicated Component for the Steak Tool.
- 创造模式物品栏新增自带附魔的牛排工具
  - Added an enchanted Steak Tool to the creative inventory.
- 牛排工具新增挖掘模式与功能选择轮盘（G 键轮盘）
  - Added a mining mode and a function selection wheel (G-key wheel) to the Steak Tool.
- 牛排工具新增当前工具模式的 tooltips 显示
  - The Steak Tool now shows its current tool mode in its tooltip.
- 新增各种锭物品与自有稀有度
  - Added various ingot items and a custom rarity.
- 使用 datagen 生成 tag、loot 与配方
  - Added datagen output for tags, loot, and recipes.
- 新增 config 用于配置牛排效果与飞行的启用关闭
  - Added config options to enable or disable steak effects and flight.
- 连锁挖掘新增显示界面与相关数据
  - Added a display overlay and supporting data for chain mining.
- 完成 AE 存储优先功能并完善 AE 存储功能
  - Completed AE storage priority and improved AE storage.
- 完善牛排功能切换的网络包
  - Improved the network packets for switching steak functions.
- 使用 POI 系统重构传送方块的传送逻辑（参考 AllTheModium 实现）
  - Reworked the Teleport Block's teleport logic around the POI system, following the AllTheModium approach.
- 支持自定义战利品大爆发的概率
  - Added configuration for the loot explosion chance.
- 支持自定义牛排杖的药水效果与挖掘速度
  - Added configuration for the Steak Staff's potion effects and mining speed.
- 维度支持全部太阳能功能
  - Dimensions now support all solar power features.
- 新增两个闪电产出配方
  - Added two lightning production recipes.
- 完善牛排工具对 GT 工具的支持与选择功能，未安装 GTCEu 时不显示工具选择轮盘
  - Improved Steak Tool support and selection for GT tools, hiding the tool wheel when GTCEu is not installed.
- 补全牛排工具相关 tag 与工具功能
  - Completed the tags and tool functions related to the Steak Tool.
- 同步配置文件为 1.20 版本，牛排工具附魔同步 1.20 使用 config 配置
  - Synced the configuration files with the 1.20 version and moved Steak Tool enchantments to 1.20-style config.
- 新增 R 键强制挖掘功能
  - Added R-key forced mining.
- 新增节日彩蛋内容
  - Added holiday easter egg content.
- 植物盆栽新增渲染开启开关与版本限制
  - Added a render toggle and version restriction to the Plant Pot.
- 新增熔炉内容与初步配方，并整理包结构
  - Added the initial furnace content and recipes and reorganized the package structure.
- 新增原版熔炉配方、AE 压印器适配、EAE 电路切片器与水晶装配器配方、Mekanism 富集仓与冶金灌注机配方、IF 溶解成型机配方
  - Added vanilla furnace recipes plus AE Inscriber, EAE Circuit Slicer and Crystal Assembler, Mekanism Enrichment Chamber and Metallurgic Infuser, and IF Dissolution Chamber recipe support.
- 实用拓展充能台与原子再构机配方支持
  - Added Actually Additions Charging Station and Atomic Reconstructor recipe support.
- 熔炼支持高堆叠并完善渲染
  - Added high-stack smelting support and improved its rendering.
- 熔炼配方需要熔炉作为标志物
  - Smelting recipes now require a furnace as a catalyst.

### Changed / 变更
- 全面重构合金炉逻辑、配方匹配逻辑与主类结构
  - Fully reworked the Alloy Furnace logic, recipe matching, and main class structure.
- 重构催化剂系统，并调整高级合金炉处理时间与并行计算逻辑以对齐新配方系统
  - Reworked the catalyst system and adjusted the Advanced Alloy Furnace processing time and parallelism to match the new recipe system.
- CraftingTask 持久化 progress 与运行时字段，重载后从原进度继续；空闲线程时拆分队列后部子任务并行运行
  - CraftingTask now persists progress and runtime fields so crafting resumes after reload, and tail subtasks are split off to run in parallel when threads are idle.
- 熔炉支持自动输出，可用扳手设置固定输出面，并修正 JEI 显示
  - Furnaces now auto-output with a wrench-set fixed output face, and JEI display was corrected.
- 新增熔炉升级机制与锭并行合成机制，并完善熔炉相关内容
  - Added furnace upgrade and ingot parallel crafting mechanics and fleshed out furnace content.
- 熔炉相关内容改用翻译键并调整翻译键，补充玻璃相关内容
  - Moved furnace content to translation keys, adjusted keys, and added glass content.
- 任务数量与熔炉等级挂钩，并调整代码结构
  - Task count now scales with furnace tier, with a code structure cleanup.
- 调整维度时间与荧光塑料配方
  - Adjusted dimension time and the Fluorescent Plastic recipe.
- 拆分模组配置类型并完善牛排工具飞行开关逻辑
  - Split mod configuration types and improved the Steak Tool flight toggle logic.
- 去除飞行与水中挖掘惩罚
  - Removed flight and underwater mining penalties.
- 调整锭配方与 1.20 版本保持一致
  - Adjusted ingot recipes to match the 1.20 version.
- 更新材质并内置新材质包
  - Updated textures and bundled a new resource pack.
- 更新依赖文件，将 Mekanism 依赖调整为第一个正式版本
  - Updated dependency files and moved the Mekanism dependency to its first release version.
- 资源文件移植调整与搬迁，并修改牛排工具相关模型
  - Ported, adjusted, and relocated resource files and revised the Steak Tool models.
- 牛排工具适配全能工具，并更换普通挖掘的处理方式
  - Adapted the Steak Tool to the Omni Tool and changed how normal mining is handled.
- 修正自定义挖掘速度逻辑
  - Fixed the custom mining speed logic.
- 调整万象合金炉代码结构
  - Adjusted the Omni Alloy Furnace code structure.
- 调整传送方块的搜索逻辑
  - Adjusted the Teleport Block search logic.
- 将 VS Code 配置目录 .vscode 加入 git 忽略列表，并修正 git 同步文件
  - Added the .vscode directory to the git ignore list and fixed the git sync files.
- 更新维度相关内容并优化相关逻辑
  - Updated dimension content and optimized its logic.
- 修改 UI 布局与按键绑定逻辑
  - Modified the UI layout and key binding logic.
- 调整牛排工具功能选择轮盘及相关数据包，初步完成 G 键轮盘
  - Adjusted the Steak Tool function wheel and its data pack, delivering an initial G-key wheel.
- 调整混沌水晶相关处理
  - Adjusted Chaos Crystal handling.
- 修改工具提示界面样式
  - Restyled the tooltip interface.
- 补充牛排工具提示
  - Expanded the Steak Tool tooltip.

### Fixed / 修复
- 修复合金炉样板槽位不显示产物图标的问题
  - Fixed Alloy Furnace pattern slots not showing output icons.
- 修复部分塑料方块配方冲突的问题
  - Fixed recipe conflicts for some plastic blocks.
- 修复药水效果无法添加的问题
  - Fixed potion effects failing to apply.
- 补充万象合金炉的 JEI 提示与 Jade 插件配置中英文翻译，并修复重载资源失败
  - Added Chinese and English translations for the Omni Alloy Furnace JEI tooltips and Jade plugin config and fixed resource reload failures.
- 修复牛排工具挖掘逻辑与 FTB 连锁交互 bug 及其它挖掘逻辑问题
  - Fixed Steak Tool mining logic, FTB chain interaction bugs, and other mining issues.
- 修复牛排工具破坏异界树时掉落普通橡木的问题
  - Fixed the Steak Tool dropping plain Oak Wood when breaking Otherworld trees.
- 修复合金炉中 AE 样板存储问题
  - Fixed AE pattern storage in the Alloy Furnace.
- 修复万象合金炉无法被样板管理终端识别的问题
  - Fixed the Omni Alloy Furnace not being recognized by the Pattern Management Terminal.
- 修复维度平台层数生成错误的问题
  - Fixed incorrect layer generation for dimension platforms.
- 修复 Mekanism 速度升级崩溃与升级配置失效的问题
  - Fixed Mekanism speed upgrade crashes and upgrade configuration not taking effect.
- 修复 Mekanism 机器速度倍率显示问题
  - Fixed incorrect speed multiplier display on Mekanism machines.
- 修复与 Mekanism: Empowered 冲突的问题
  - Fixed conflicts with Mekanism: Empowered.
- 修复某些情况下打开菜单崩溃的问题
  - Fixed crashes when opening menus in some cases.
- 修复牛排切换到万能工具模式时创造飞行失效的问题
  - Fixed creative flight breaking when the Steak Tool switched to Omni Tool mode.
- 修复转换后的 omni_wrench 无法飞行的问题
  - Fixed the converted omni_wrench being unable to fly.
- 修复 Mekanism 能量升级问题
  - Fixed Mekanism energy upgrades.
- 修正 IF 溶解罐配方耗电，并修正 IF 溶解成型机与 Mekanism 冶金灌注机配方适配
  - Corrected IF Dissolution Chamber energy usage and fixed recipe adaptation for the IF Dissolution Chamber and the Mekanism Metallurgic Infuser.
- 修正 AE 系列压印器内容
  - Corrected AE Inscriber content.
- 尝试修复线程死锁导致游戏卡死的问题，并修复取消任务后流体不返回的问题
  - Attempted to fix a thread deadlock that froze the game and fixed fluids not being returned when a task is cancelled.
- 修复上次修复导致的部分配方失效与部分魔改配方不生效的问题
  - Fixed recipes broken by the previous fix and some modified recipes not taking effect.
- 修复并行被限制为 100 万上限的问题
  - Fixed parallelism being capped at one million.
- 修复 AE 任务有时无法完成的问题
  - Fixed AE tasks sometimes failing to complete.
- 修复部分配方无法运行、闪退与卡死的问题
  - Fixed some recipes failing to run, crashing, or freezing.
- 修复区块生成崩溃的问题
  - Fixed crashes during chunk generation.
- 修复硬依赖植物盆的问题
  - Fixed a hard dependency on the Plant Pot.
- 修复闪电科技配方不使用闪电的问题
  - Fixed Lightning Tech recipes not consuming lightning.
- 修复服务器端崩溃相关内容
  - Fixed server-side crash issues.

### Performance / 性能
- 优化配方查找机制
  - Optimized the recipe lookup mechanism.
- 连锁挖掘性能优化并添加缓存机制，同时优化工具切换
  - Improved chain mining performance with a caching mechanism and optimized tool switching.

## [2.0.0]

### Added / 新增
- 初步移植牛排工具，包含扳手、模型切换、磁力与附魔相关功能
  - Ported the Steak Tool for the first time, including wrench, model switching, magnetism, and enchantment features.
- 新增矿物生成器
  - Added the Ore Generator.
- 新增植物盆栽及其依赖
  - Added the Plant Pot and its dependencies.
- 新增维度与维度传送方块相关内容
  - Added dimensions and Dimension Teleport Block content.
- 新增塑料方块及相关资源文件与 init 注册类
  - Added plastic blocks with their resources and init registration classes.
- 新增 EAE 与 Mekanism 的 Mixin 兼容
  - Added EAE and Mekanism Mixin compatibility.
- 新增配置类与工具类
  - Added configuration and utility classes.

### Changed / 变更
- 调整资源文件
  - Adjusted resource files.

### Fixed / 修复
- 修复植物盆弹出逻辑
  - Fixed the Plant Pot ejection logic.
