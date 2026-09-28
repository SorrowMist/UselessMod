package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import com.sorrowmist.useless.compat.ae.AeChemicalCompatLoader;
import com.sorrowmist.useless.compat.ae.AeEnergyCompatLoader;
import com.sorrowmist.useless.compat.ae.AeLogisticsCompatLoader;
import com.sorrowmist.useless.compat.ae.AeSourceCompatLoader;
import com.sorrowmist.useless.compat.ars.ArsSourceCompatLoader;
import com.sorrowmist.useless.compat.modernindustrialization.MiEnergyCompatLoader;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.energy.IEnergyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 造化杖无线物流的「容器能力」解析与搬运实现。
 *
 * <p><b>全链路 long 语义。</b> 搬运不再经过 NeoForge 原生的 int 槽位接口，而是统一走
 * {@code com.sorrowmist.useless.api.logistics} 的 long 级契约：一次请求要搬多少就是多少，
 * 由处理器内部决定怎么把它凑出来。超 {@link Integer#MAX_VALUE} 的运输因此不再需要外层
 * 反复循环补足。</p>
 *
 * <p><b>本模组自己的 long 能力原生长途直通。</b> 能量优先识别 {@link IEnergyManager}：它是
 * long 契约，一次调用直接搬走全部请求量，不存在任何 int 中转，也没有补循环。</p>
 *
 * <p><b>能量还有两个额外来源</b>：方块侧兼容 Modern Industrialization 的 EU（按 MI 自己的
 * {@code forgeEnergyPerEu} 换算成 FE，且不查电压等级），网络侧兼容 AppliedFlux 存进 ME 网络的
 * 通量 FE（即 {@link LinkMedium#AE_ENERGY}）。两者都被归一成同一个 {@link LongEnergyHandler}
 * 契约，所以搬运与诊断对它们一视同仁。</p>
 *
 * <p>物品 / 流体的底层容器多数仍是 int 槽位，因此由 {@link LongResourceAdapters} 在内部
 * 分片——分片细节封在适配器里，调用方（也就是本类）只看得到 long。</p>
 *
 * <p>化学品经本模组已有的 {@link ChemicalCompatProviders} 抽象层（本就是 long 计数，因此
 * 无需包装）；魔源经 {@link ArsSourceCompatLoader} 提供的端点（Ars Nouveau 自身是 int 契约，
 * 上限处夹取，魔源的量级远到不了 int 边界）。</p>
 *
 * <p><b>每种资源都有「方块容器」与「AE 网络」两种承载。</b> 两者在解析阶段被归一成同一个
 * 端点契约，于是搬运与诊断对它们一视同仁，配对时也只需比较 {@link ResourceFamily}。</p>
 *
 * <p>所有解析都会先确认区块已加载，<b>绝不</b>触发强制加载。</p>
 */
public final class StaffLinkTargets {
    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * 绑定新锚点时挑选默认资源类型的优先级。
     *
     * <p>AE 类型排在最后：它们只有在方块<b>本身不是容器</b>时才会被选中，因此把一个
     * 无线访问点当作普通箱子绑定也没问题——普通容器先命中，访问点则落到 AE 上。</p>
     */
    private static final LinkMedium[] DEFAULT_ORDER = {
            LinkMedium.ITEM, LinkMedium.FLUID, LinkMedium.ENERGY, LinkMedium.CHEMICAL,
            LinkMedium.SOURCE, LinkMedium.AE_ITEM, LinkMedium.AE_FLUID,
            LinkMedium.AE_CHEMICAL, LinkMedium.AE_SOURCE, LinkMedium.AE_ENERGY
    };

    /**
     * 「没有过滤器」时一次搬运最多检视的条目数。
     *
     * <p>普通容器的条目数很小，这个上限对它们没有意义；它存在是为了 AE：ME 网络的
     * {@code getSlots()} 是网络里的资源种类数，动辄上千。没有过滤器时命中即搬运、循环会早早
     * 退出，所以预算只在「一直搬不动」时兜底，防止每 tick 把上千条目全走一遍。</p>
     *
     * <p><b>有过滤器时一律不走这条扫描路径</b>（见 {@code moveItems}）：那时「要搬什么」已经
     * 被过滤器完全确定，直接按类型定位即可。这里曾经注释成「截断只影响速率——本 tick 没轮到的
     * 条目下一 tick 继续」，那是<b>错的</b>：扫描每次从 0 开始，排在预算之外的条目永远轮不到，
     * 于是「接收端设了过滤的机器一直收不到东西」。</p>
     */
    private static final int MAX_SCAN_SLOTS = 256;

    private StaffLinkTargets() {
    }

    // ------------------------------------------------------------------ 解析

    /** 该坐标是不是「有东西可搬」的容器——决定潜行右键要不要接管。 */
    public static boolean isBindable(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        for (LinkMedium medium : DEFAULT_ORDER) {
            if (medium.isSupported() && resolve(level, pos, null, medium) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * 该坐标上的锚点方块是否还在——自愈逻辑唯一的删除判据。
     *
     * <p><b>刻意不碰资源能力。</b> 「解析不出当前介质」不等于「方块没了」：一个 ME 接口在
     * 资源类型选成流体、化学品或魔源时照样解析不出端点，可它明明还在那里。若拿解析结果
     * 当存活判据，玩家刚配好的线会在下一个自愈周期被整条删掉。搬运失败有界面提示，
     * 条件恢复后自动继续，不需要删配置。</p>
     *
     * <p>因此这里只看方块本身：空气才算「没了」。被换成别的方块时保留配置——玩家可能只是
     * 中途替换，重新放回去就能继续用；要彻底解绑有手动解绑。</p>
     */
    public static boolean anchorPresent(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return false;
        }
        return !level.getBlockState(pos).isAir();
    }

    /** 该坐标最适合的默认资源类型；什么都解析不出来时返回 {@code null}。 */
    @Nullable
    public static LinkMedium defaultMediumFor(Level level, BlockPos pos) {
        for (LinkMedium medium : DEFAULT_ORDER) {
            if (medium.isSupported() && resolve(level, pos, null, medium) != null) {
                return medium;
            }
        }
        return null;
    }

    /**
     * 解析目标容器在指定资源类型下的处理器。
     *
     * @return {@link LongItemHandler} / {@link LongFluidHandler} / {@link LongEnergyHandler} /
     *         {@code ChemicalHandlerView} / {@link SourceBridge}；不可用返回 {@code null}
     */
    @Nullable
    public static Object resolve(Level level, BlockPos pos, @Nullable Direction side, LinkMedium medium) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return switch (medium) {
            case ITEM -> items(capability(level, Capabilities.ItemHandler.BLOCK, pos, side, medium));
            case FLUID -> fluids(capability(level, Capabilities.FluidHandler.BLOCK, pos, side, medium));
            case ENERGY -> energyHandler(level, pos, side, medium);
            case CHEMICAL -> chemicalHandler(level, pos, side);
            case SOURCE -> ArsSourceCompatLoader.sourceHandler(level, pos);
            // AE 这一端交给桥去解析：常驻代码只拿到一个 long 契约的端点，
            // 不出现任何 AE2 类型，因此没装 AE2 时这段代码照常加载、只是永远解析不出结果。
            case AE_ITEM -> AeLogisticsCompatLoader.itemEndpoint(level, pos);
            case AE_FLUID -> AeLogisticsCompatLoader.fluidEndpoint(level, pos);
            // 化学品/魔源/能量要进 ME 网络得靠各自的附属（appmek / arseng / appflux），
            // 所以走独立的桥。
            case AE_CHEMICAL -> AeChemicalCompatLoader.chemicalEndpoint(level, pos);
            case AE_SOURCE -> AeSourceCompatLoader.sourceEndpoint(level, pos);
            case AE_ENERGY -> AeEnergyCompatLoader.energyEndpoint(level, pos);
        };
    }

    @Nullable
    private static LongItemHandler items(@Nullable net.neoforged.neoforge.items.IItemHandler handler) {
        return handler == null ? null : LongResourceAdapters.items(handler);
    }

    @Nullable
    private static LongFluidHandler fluids(@Nullable net.neoforged.neoforge.fluids.capability.IFluidHandler handler) {
        return handler == null ? null : LongResourceAdapters.fluids(handler);
    }

    /**
     * 能量解析。按优先级依次尝试：
     *
     * <ol>
     *   <li>本模组的 {@link IEnergyManager}：long 契约，<b>原生直通</b>，一次调用搬走全部请求量。
     *       本模组机器的能量能力注册时交出的就是 {@code EnergyManager}，所以在自家机器上必然命中。</li>
     *   <li>外部模组 int 级的 {@link IEnergyStorage}：由 {@link LongResourceAdapters} 包装成分片的
     *       long 契约。</li>
     *   <li>Modern Industrialization 的 EU：MI 的机器只注册自己的 {@code EnergyApi.SIDED}，
     *       不提供 NeoForge 的 FE 能力，所以上面两步都拿不到。交给 MI 的桥，由它按
     *       MI 自己的 {@code forgeEnergyPerEu} 换算成 FE，并且不查电压等级。</li>
     * </ol>
     */
    @Nullable
    private static LongEnergyHandler energyHandler(Level level, BlockPos pos, @Nullable Direction side,
                                                   LinkMedium medium) {
        IEnergyStorage storage = capability(level, Capabilities.EnergyStorage.BLOCK, pos, side, medium);
        if (storage instanceof IEnergyManager manager) {
            return LongResourceAdapters.energy(manager);
        }
        if (storage != null) {
            return LongResourceAdapters.energy(storage);
        }
        return MiEnergyCompatLoader.energyEndpoint(level, pos, side);
    }

    @Nullable
    private static <T> T capability(Level level, BlockCapability<T, Direction> capability,
                                    BlockPos pos, @Nullable Direction side, LinkMedium medium) {
        if (side != null) {
            return level.getCapability(capability, pos, side);
        }
        CapabilityHint hint = new CapabilityHint(level.dimension(), pos.immutable(), medium);
        if (CAPABILITY_HINTS.containsKey(hint)) {
            // 上次命中的那个面：先试它，命中就省下最多 6 次 getCapability。
            Direction remembered = CAPABILITY_HINTS.get(hint);
            T found = remembered == null
                    ? level.getCapability(capability, pos, null)
                    : level.getCapability(capability, pos, remembered);
            if (found != null) {
                return found;
            }
            // 提示失手：方块多半被换过了。退回下面的完整扫描并重新记，所以绝不会读错，
            // 最多多付一次查找。
            CAPABILITY_HINTS.remove(hint);
        }
        T unspecified = level.getCapability(capability, pos, null);
        if (unspecified != null) {
            CAPABILITY_HINTS.put(hint, null);
            return unspecified;
        }
        for (Direction direction : DIRECTIONS) {
            T found = level.getCapability(capability, pos, direction);
            if (found != null) {
                CAPABILITY_HINTS.put(hint, direction);
                return found;
            }
        }
        return null;
    }

    /**
     * 「上次哪个面解析成功」的提示表。
     *
     * <p>{@link #capability} 在 {@code side == null} 时要依次试「不限面 + 6 个方向」，最坏 7 次
     * {@code getCapability}；而一个锚点的可用面在方块没被换掉之前是稳定的。这里只记「上次命中
     * 的那个面」，下次先试它。</p>
     *
     * <p><b>提示失手一律退回完整扫描</b>，所以方块被换掉、能力被撤回都不会读错，最多多付一次
     * 查找。值写 {@code null} 表示「不限面那次就命中了」——用 {@code containsKey} 区分
     * 「没记过」与「记的是 null」。</p>
     *
     * <p>键里带 {@code medium}：不同资源类型可能落在不同的面上。只在服务端线程访问
     * （右键判定、绑定包、引擎 tick 都在服务端线程）。</p>
     */
    private static final Map<CapabilityHint, Direction> CAPABILITY_HINTS = new HashMap<>();

    private record CapabilityHint(ResourceKey<Level> dimension, BlockPos pos, LinkMedium medium) {
    }

    /**
     * 清空「上次命中的面」提示表。
     *
     * <p>提示本身不会读错（失手就退回完整扫描），这里只是防止它随着历史锚点无限增长。
     * 引擎在自愈周期与服务器停止时调用。</p>
     */
    public static void clearCapabilityHints() {
        CAPABILITY_HINTS.clear();
    }

    @Nullable
    private static ChemicalHandlerView chemicalHandler(Level level, BlockPos pos, @Nullable Direction side) {
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return provider.getAdjacentHandler(level, pos, state, entity, side);
    }

    // ------------------------------------------------------------------ 搬运

    /**
     * 解析释放端（源）的端点。解析不出来返回 {@code null}。
     *
     * <p>单独暴露是因为一次分配里源端要发给多个接收端：源端点只需解析一次，
     * 由 {@link StaffLinkEngine#distribute} 拿到后交给
     * {@link #transfer(Object, ServerLevel, StaffLinkRoute, ServerLevel, StaffLinkRoute, long)} 复用。
     * AE 端点尤其要紧 —— 它每次解析都要走「取方块实体 → 取能力」，还会新建一份端点实例。</p>
     */
    @Nullable
    public static Object resolveSource(ServerLevel sourceLevel, StaffLinkRoute source) {
        return resolve(sourceLevel, source.anchor().pos(), source.side(), source.medium());
    }

    /**
     * 把资源从 {@code source} 搬到 {@code target}。
     *
     * <p><b>默认「先模拟后提交」</b>：模拟阶段算出目标能接多少，提交阶段只搬那个数量；万一提交时
     * 目标仍然吐回余量（同 tick 内不该发生），余量会退回源，源也塞不回时掉落到源脚边，
     * 保证不凭空消失。</p>
     *
     * <p><b>源端是 ME 网络时跳过「先问目标能收多少」这一步</b>（{@code skipTargetProbe}）。
     * 理由有两条：</p>
     *
     * <ol>
     *   <li><b>模拟本来就是纯开销。</b>它存在的意义是「别从源里抽出超过目标能收的量」，
     *       而那要靠「抽出来的东西能塞回源」兜底。ME 网络的 {@code insert} 一定收得下刚刚
     *       从它里面抽出来的东西（同一 tick 内），所以这条底线本来就成立。</li>
     *   <li><b>模拟很贵。</b>目标端的 {@code insert(..., true)} 会完整走一遍
     *       {@code AEItemKey.of} + 槽位试算；实测一次搬运里「源试算 + 目标试算 + 源抽 +
     *       目标提交」四次调用中，目标那两次占了目标端总耗时的全部。去掉模拟等于把
     *       「每次搬运问两遍」变成「问一遍」。</li>
     * </ol>
     *
     * <p><b>为什么只按源端判断，不按目标端判断。</b>余量退回的是<b>源</b>，所以风险只取决于源端
     * 收不收得回：源是 ME 网络 ⇒ 稳；源是普通容器 ⇒ 有可能塞不回（例如机器的输出槽只出不进），
     * 那时东西会掉在源脚边。目标端是不是 AE 与这条底线无关，所以不看它。</p>
     *
     * <p><b>数量全程 long。</b> {@code limit} 多大就请求多大，不存在「先截断成 int、剩下的
     * 交给外层循环」这一步。</p>
     *
     * <p><b>源端由调用方先解析好再传进来</b>（见 {@link #resolveSource}）。一个释放端往往要发给
     * 多个接收端，源端点解析一次就够；AE 端点尤其要紧 —— 它每次解析都要走「取方块实体 → 取能力」
     * 并新建一份端点实例，按接收端个数重复付这笔钱没有意义。</p>
     *
     * @param from 源端点，{@code null} 视为解析失败、直接不搬
     * @return 实际搬运量（0 表示没搬）
     */
    public static long transfer(@Nullable Object from, ServerLevel sourceLevel, StaffLinkRoute source,
                                ServerLevel targetLevel, StaffLinkRoute target, long limit) {
        // 配对按 family 而不是 medium：ITEM 与 AE_ITEM 都是「物品」，箱子接 AE 网络才配得起来。
        if (from == null || limit <= 0L || source.medium().family() != target.medium().family()) {
            return 0L;
        }
        Object to = resolve(targetLevel, target.anchor().pos(), target.side(), target.medium());
        if (to == null) {
            return 0L;
        }
        // 过滤器两端都算数：释放端决定「允许抽出什么」，接收端决定「允许收下什么」，
        // 一个资源必须同时通过两边才允许搬运。只看一端的话，接收端设的过滤器等于没设。
        //
        // 唯一的例外是「从 AE 取出」：源端是 ME 网络时源端过滤器不参与判定，拿什么完全由
        // 接收端的白名单决定（见 LinkMedium#isAe）。网络里动辄上千种物品，让玩家在源端列
        // 白名单既繁琐又容易漏，而「我要往这个箱子拿什么」本来就该由接收端说。
        List<LinkFilterSlot> sourceFilter = source.medium().isAe() ? List.of() : source.activeFilters();
        List<LinkFilterSlot> targetFilter = target.activeFilters();
        // 源端是 ME 网络 ⇒ 抽出来的东西一定塞得回去 ⇒ 不必先问目标能收多少。见方法说明。
        boolean skipTargetProbe = source.medium().isAe();
        return switch (source.medium()) {
            case ITEM, AE_ITEM -> moveItems(sourceLevel, source.anchor().pos(),
                    (LongItemHandler) from, (LongItemHandler) to, limit, sourceFilter, targetFilter,
                    skipTargetProbe);
            case FLUID, AE_FLUID -> moveFluid((LongFluidHandler) from, (LongFluidHandler) to, limit,
                    sourceFilter, targetFilter, skipTargetProbe);
            case ENERGY, AE_ENERGY -> moveEnergy((LongEnergyHandler) from, (LongEnergyHandler) to, limit);
            case CHEMICAL, AE_CHEMICAL -> moveChemical((ChemicalHandlerView) from,
                    (ChemicalHandlerView) to, limit, sourceFilter, targetFilter);
            // 魔源那边的接口是 int，超出的部分只能夹掉——魔源本身也到不了那么大的量。
            case SOURCE, AE_SOURCE -> moveSource((SourceHandlerView) from, (SourceHandlerView) to, limit);
        };
    }

    private static long moveItems(ServerLevel sourceLevel, BlockPos sourcePos,
                                  LongItemHandler from, LongItemHandler to, long limit,
                                  List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                  boolean skipTargetProbe) {
        // ---- 有过滤器：按类型直接抽，绝不扫描 ----
        //
        // 「要搬什么」已经被过滤器完全确定了，直接问源端「有没有这一种」即可 ——
        // 这一步合并进了 moveOneItemStackByType（源端试算一次）。
        //
        // 不能改成「扫源端槽位、挑匹配的」：AE 端点的 getSlots() 是整个 ME 网络的资源种类数
        // （成熟网络上千种很常见），而扫描有 MAX_SCAN_SLOTS 预算。靠扫描找过滤器指定的那一种，
        // 只要它在网络里的序号超过预算就<b>永远</b>搬不过来 —— 表现就是「接收端设了过滤的机器
        // 一直收不到东西」，而没设过滤的机器（第一种就命中）一切正常。
        //
        // 同样不能改成「先 findSlot 定位槽位、再 extract(slot)」：AE 端点的 findSlot 需要先抓
        // 一份整网快照（MEStorage.getAvailableStacks()），等于每次搬运遍历一遍网络里每一种资源。
        // 实测这一条就占了无线物流总耗时的 32%（见 wiki/WIRELESS_LOGISTICS_PERF_REPORT.md 第十节）。
        List<ItemStack> wanted = filteredItemMarkers(sourceFilter, targetFilter);
        if (!wanted.isEmpty()) {
            long moved = 0L;
            for (ItemStack template : wanted) {
                if (moved >= limit) {
                    break;
                }
                // 候选来自其中一端，另一端仍要自己判一次：两端过滤器都得通过。
                if (!matches(sourceFilter, template) || !matches(targetFilter, template)) {
                    continue;
                }
                moved += moveOneItemStackByType(sourceLevel, sourcePos, from, to,
                        limit - moved, template, skipTargetProbe);
            }
            return moved;
        }

        // ---- 没有过滤器：沿用扫描，命中即搬 ----
        //
        // 这时循环本来就会早早退出（第一种可搬的搬完就够 limit 了），预算只在「一直搬不动」
        // 时兜底，防止每 tick 把上千条目全走一遍。
        long moved = 0L;
        int budget = Math.min(from.getSlots(), MAX_SCAN_SLOTS);
        for (int slot = 0; slot < budget && moved < limit; slot++) {
            ItemStack template = from.getStackInSlot(slot);
            if (template.isEmpty()) {
                continue;
            }
            // 数量顺手从刚拿到的模板上取（见 LongItemHandler#amountIn(int, ItemStack)）：
            // 通用实现里模板的 count 就是槽内真实数量，不必再把同一个槽位读第二遍。
            long available = from.amountIn(slot, template);
            moved += moveOneItemStack(sourceLevel, sourcePos, from, to, slot,
                    limit - moved, template, available, skipTargetProbe);
        }
        return moved;
    }

    /**
     * 处理「已经抽出来了、但目标没全收下」的余量：退回源，退不回就掉在源脚边。返回实际搬走的量。
     *
     * <p>抽出来之后才发现目标不收，是<b>跳过目标试算</b>那条路的正常分支（见
     * {@link #transfer(Object, ServerLevel, StaffLinkRoute, ServerLevel, StaffLinkRoute, long)}）。</p>
     *
     * @param extracted 从源里实际抽出的量
     * @param accepted  目标实际收下的量（调用方已经拿到，不必再问一次）
     */
    private static long settleItemLeftover(ServerLevel sourceLevel, BlockPos sourcePos,
                                           LongItemHandler from, ItemStack template,
                                           long extracted, long accepted) {
        long leftover = extracted - accepted;
        long actuallyMoved = extracted;
        if (leftover > 0L) {
            long unreturned = leftover - from.insert(template, leftover, false);
            if (unreturned > 0L) {
                dropItems(sourceLevel, sourcePos, template, unreturned);
                actuallyMoved -= unreturned;
            }
        }
        return Math.max(0L, actuallyMoved);
    }

    /**
     * 从指定槽位搬走最多 {@code want} 个 {@code template}；返回实际搬走的数量。
     *
     * <p>只给<b>无过滤器的扫描路径</b>用（有过滤器的走 {@link #moveOneItemStackByType}）。</p>
     *
     * @param available 该槽位的存量；扫描路径已经连同模板一起取过，避免重复读同一个槽位
     */
    private static long moveOneItemStack(ServerLevel sourceLevel, BlockPos sourcePos,
                                         LongItemHandler from, LongItemHandler to, int slot,
                                         long want, ItemStack template, long available,
                                         boolean skipTargetProbe) {
        if (want <= 0L || available <= 0L) {
            return 0L;
        }
        long request = Math.min(want, available);
        if (skipTargetProbe) {
            long extracted = from.extract(slot, request, false);
            if (extracted <= 0L) {
                return 0L;
            }
            return settleItemLeftover(sourceLevel, sourcePos, from, template, extracted,
                    to.insert(template, extracted, false));
        }
        // 模拟：目标最多能接多少（long，跨槽累加精确）。
        long accepted = to.insert(template, request, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = from.extract(slot, accepted, false);
        if (extracted <= 0L) {
            return 0L;
        }
        return settleItemLeftover(sourceLevel, sourcePos, from, template, extracted,
                to.insert(template, extracted, false));
    }

    /**
     * 从源端按<b>类型</b>搬走最多 {@code want} 个 {@code template}；返回实际搬走的数量。
     *
     * <p>与 {@link #moveOneItemStack} 的区别是<b>不经过「第几号槽」</b>：定位与试算合并成
     * {@link LongItemHandler#extractMatching(ItemStack, long, boolean)} 一次调用。对 AE 端点
     * 这一步是一次按资源键的哈希查找；走槽位的话得先抓一份整网快照，也就是把 ME 网络里的
     * 每一种资源遍历一遍（实测占无线物流总耗时的 32%）。</p>
     *
     * <p><b>{@code skipTargetProbe} 为真时只问两次</b>（源抽一次、目标收一次），不为真的话
     * 是四次（源试算 → 目标试算 → 源抽 → 目标收）。判据见
     * {@link #transfer(Object, ServerLevel, StaffLinkRoute, ServerLevel, StaffLinkRoute, long)}。</p>
     */
    private static long moveOneItemStackByType(ServerLevel sourceLevel, BlockPos sourcePos,
                                               LongItemHandler from, LongItemHandler to,
                                               long want, ItemStack template,
                                               boolean skipTargetProbe) {
        if (want <= 0L) {
            return 0L;
        }
        if (skipTargetProbe) {
            // 源端一次调用完成「定位 + 抽取」：抽到多少就是多少，目标收不下的余量退回源。
            long extracted = from.extractMatching(template, want, false);
            if (extracted <= 0L) {
                return 0L;
            }
            return settleItemLeftover(sourceLevel, sourcePos, from, template, extracted,
                    to.insert(template, extracted, false));
        }
        // 源端试算：既回答「有没有这一种」，也给出实际可抽量。
        long available = from.extractMatching(template, want, true);
        if (available <= 0L) {
            return 0L;
        }
        long accepted = to.insert(template, available, true);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = from.extractMatching(template, accepted, false);
        if (extracted <= 0L) {
            return 0L;
        }
        return settleItemLeftover(sourceLevel, sourcePos, from, template, extracted,
                to.insert(template, extracted, false));
    }

    /**
     * 收集两端过滤器里列出的物品种类（按类型去重）。
     *
     * <p>次序是「先源端、后接收端」，同权重下靠前的先拿，这个顺序要可预期。</p>
     */
    private static List<ItemStack> filteredItemMarkers(List<LinkFilterSlot> sourceFilter,
                                                       List<LinkFilterSlot> targetFilter) {
        List<ItemStack> result = new ArrayList<>(sourceFilter.size() + targetFilter.size());
        collectItemMarkers(result, sourceFilter);
        collectItemMarkers(result, targetFilter);
        return result;
    }

    private static void collectItemMarkers(List<ItemStack> out, List<LinkFilterSlot> filter) {
        for (LinkFilterSlot slot : filter) {
            if (!slot.isItem()) {
                continue;
            }
            boolean duplicate = false;
            for (ItemStack existing : out) {
                if (ItemStack.isSameItemSameComponents(existing, slot.item())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(slot.item());
            }
        }
    }

    /** 塞不回去的余量掉在源脚边；按堆叠上限分片，避免做出超过栈上限的非法栈。 */
    private static void dropItems(ServerLevel level, BlockPos pos, ItemStack template, long amount) {
        int stackLimit = Math.max(1, template.getMaxStackSize());
        long remaining = amount;
        while (remaining > 0L) {
            int chunk = (int) Math.min(remaining, stackLimit);
            Block.popResource(level, pos, template.copyWithCount(chunk));
            remaining -= chunk;
        }
    }

    /**
     * 流体搬运：一次只处理源里第一种可搬的流体。
     *
     * <p>模拟阶段两边都给的是 long 精确上限，因此 {@code limit} 能一次性用满。</p>
     */
    private static long moveFluid(LongFluidHandler from, LongFluidHandler to, long limit,
                                  List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                  boolean skipTargetProbe) {
        // 有过滤器时按类型直接抽，理由同 moveItems：AE 端点的 getTanks() 是网络里的流体
        // 种类数，靠扫描会被预算截断，排在后面的那些永远搬不过来；而 findTank 又要先抓整网快照。
        List<FluidStack> wanted = filteredFluidMarkers(sourceFilter, targetFilter);
        if (!wanted.isEmpty()) {
            for (FluidStack type : wanted) {
                if (!matchesFluid(sourceFilter, type) || !matchesFluid(targetFilter, type)) {
                    continue;
                }
                long moved = moveOneFluidByType(from, to, limit, type, skipTargetProbe);
                if (moved > 0L) {
                    // 一次只搬一种：搬动了就收工。
                    return moved;
                }
            }
            return 0L;
        }

        int budget = Math.min(from.getTanks(), MAX_SCAN_SLOTS);
        for (int tank = 0; tank < budget; tank++) {
            FluidStack type = from.getFluidInTank(tank);
            if (type.isEmpty()) {
                continue;
            }
            long moved = moveOneFluid(from, to, tank, limit, type, skipTargetProbe);
            if (moved > 0L) {
                return moved;
            }
        }
        return 0L;
    }

    /**
     * 处理「已经抽出来了、但目标没全收下」的余量：退回源，退不回就记一条警告。返回实际搬走的量。
     *
     * @param drained 从源里实际抽出的量
     * @param filled  目标实际收下的量
     */
    private static long settleFluidLeftover(LongFluidHandler from, LongFluidHandler to,
                                            FluidStack type, long drained, long filled) {
        if (filled < drained) {
            long leftover = drained - filled;
            long returned = from.fill(type, leftover, false);
            if (returned < leftover) {
                UselessMod.LOGGER.warn(
                        "无线物流：流体回填失败，{} mB {} 无法回收（源 {}，目标 {}）",
                        leftover - returned, type.getFluid(),
                        from.getClass().getSimpleName(), to.getClass().getSimpleName());
            }
        }
        return Math.max(0L, filled);
    }

    /** 从指定储罐搬走最多 {@code limit} 的 {@code type}；返回实际搬走的量（0 = 没搬动）。 */
    private static long moveOneFluid(LongFluidHandler from, LongFluidHandler to, int tank,
                                     long limit, FluidStack type, boolean skipTargetProbe) {
        long available = from.amountIn(tank);
        if (available <= 0L) {
            return 0L;
        }
        long request = Math.min(limit, available);
        if (skipTargetProbe) {
            long drained = from.drain(tank, request, false);
            if (drained <= 0L) {
                return 0L;
            }
            return settleFluidLeftover(from, to, type, drained, to.fill(type, drained, false));
        }
        long drainable = from.drain(tank, request, true);
        if (drainable <= 0L) {
            return 0L;
        }
        long fillable = to.fill(type, drainable, true);
        long target = Math.min(drainable, fillable);
        if (target <= 0L) {
            return 0L;
        }

        long drained = from.drain(tank, target, false);
        if (drained <= 0L) {
            return 0L;
        }
        return settleFluidLeftover(from, to, type, drained, to.fill(type, drained, false));
    }

    /**
     * 从源端按<b>类型</b>搬走最多 {@code limit} 的 {@code type}；返回实际搬走的量。
     *
     * <p>理由同 {@link #moveOneItemStackByType}：不经过「第几号罐」，定位与试算合并成
     * {@link LongFluidHandler#drainMatching(FluidStack, long, boolean)} 一次调用。</p>
     */
    private static long moveOneFluidByType(LongFluidHandler from, LongFluidHandler to,
                                           long limit, FluidStack type, boolean skipTargetProbe) {
        if (skipTargetProbe) {
            long drained = from.drainMatching(type, limit, false);
            if (drained <= 0L) {
                return 0L;
            }
            return settleFluidLeftover(from, to, type, drained, to.fill(type, drained, false));
        }
        long drainable = from.drainMatching(type, limit, true);
        if (drainable <= 0L) {
            return 0L;
        }
        long fillable = to.fill(type, drainable, true);
        long target = Math.min(drainable, fillable);
        if (target <= 0L) {
            return 0L;
        }
        long drained = from.drainMatching(type, target, false);
        if (drained <= 0L) {
            return 0L;
        }
        return settleFluidLeftover(from, to, type, drained, to.fill(type, drained, false));
    }

    /** 收集两端过滤器里列出的流体种类（按类型去重）。 */
    private static List<FluidStack> filteredFluidMarkers(List<LinkFilterSlot> sourceFilter,
                                                         List<LinkFilterSlot> targetFilter) {
        List<FluidStack> result = new ArrayList<>(sourceFilter.size() + targetFilter.size());
        collectFluidMarkers(result, sourceFilter);
        collectFluidMarkers(result, targetFilter);
        return result;
    }

    private static void collectFluidMarkers(List<FluidStack> out, List<LinkFilterSlot> filter) {
        for (LinkFilterSlot slot : filter) {
            if (!slot.isFluid()) {
                continue;
            }
            boolean duplicate = false;
            for (FluidStack existing : out) {
                if (FluidStack.isSameFluidSameComponents(existing, slot.fluid())) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                out.add(slot.fluid());
            }
        }
    }

    /**
     * 能量搬运。
     *
     * <p>两端都已经是 long 契约（本模组 {@link IEnergyManager} 原生直通、MI 的 EU 端点按配置
     * 换算、AppliedFlux 的 ME 通量走 AE 的 long 接口），所以这里一次到位，没有分片循环。</p>
     */
    private static long moveEnergy(LongEnergyHandler from, LongEnergyHandler to, long limit) {
        long available = from.extract(limit, true);
        if (available <= 0L) {
            return 0L;
        }
        long accepted = to.receive(available, true);
        long target = Math.min(available, accepted);
        if (target <= 0L) {
            return 0L;
        }

        long extracted = from.extract(target, false);
        if (extracted <= 0L) {
            return 0L;
        }
        long received = to.receive(extracted, false);
        if (received < extracted) {
            from.receive(extracted - received, false);
        }
        return received;
    }

    /**
     * 魔源搬运。
     *
     * <p>接口是 int：Ars Nouveau 自身的 {@code ISourceTile} 就是 int 契约，魔源的量级离
     * {@link Integer#MAX_VALUE} 极远，所以这里把 long 的请求夹到 int 再进端点，端点在内部
     * 还会按各自的 {@code transferRate()} 再夹一次。两端的速率因此都得到尊重。</p>
     *
     * <p>时序与其它资源一致：先模拟出「源能出多少 ∩ 目标能收多少」，再按这个数量提交，
     * 提交时若目标吐回余量则退回源，保证不凭空消失。</p>
     */
    private static long moveSource(SourceHandlerView from, SourceHandlerView to, long limit) {
        int request = clampToInt(limit);
        if (request <= 0) {
            return 0L;
        }
        int available = from.extract(request, true);
        if (available <= 0) {
            return 0L;
        }
        int accepted = to.receive(available, true);
        int target = Math.min(available, accepted);
        if (target <= 0) {
            return 0L;
        }

        int extracted = from.extract(target, false);
        if (extracted <= 0) {
            return 0L;
        }
        int received = to.receive(extracted, false);
        if (received < extracted) {
            from.receive(extracted - received, false);
        }
        return received;
    }

    /** 把 long 请求夹进 int。魔源侧只可能是 int，超出的部分本来就搬不动。 */
    private static int clampToInt(long value) {
        if (value <= 0L) {
            return 0;
        }
        return (int) Math.min(value, Integer.MAX_VALUE);
    }

    private static long moveChemical(ChemicalHandlerView from, ChemicalHandlerView to, long limit,
                                     List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter) {
        ChemicalStackView simulated = from.extractChemical(limit, true);
        if (simulated == null || simulated.isEmpty() || !matchesChemical(sourceFilter, simulated)
                || !matchesChemical(targetFilter, simulated)) {
            return 0L;
        }
        ChemicalStackView rejected = to.insertChemical(simulated, true);
        long accepted = simulated.amount() - (rejected == null ? 0L : rejected.amount());
        if (accepted <= 0L) {
            return 0L;
        }

        ChemicalStackView taken = from.extractChemical(accepted, false);
        if (taken == null || taken.isEmpty()) {
            return 0L;
        }
        ChemicalStackView leftover = to.insertChemical(taken, false);
        long moved = taken.amount() - (leftover == null ? 0L : leftover.amount());
        if (leftover != null && !leftover.isEmpty()) {
            from.insertChemical(leftover, false);
        }
        return moved;
    }

    /**
     * 物品过滤：只比物品标记。
     *
     * <p>一格标记要么是物品要么是流体，这里只认物品那些格。没有<b>任何物品标记</b>时视为
     * 「不限制」——换过资源类型之后残留的流体标记因此不会把搬运整条堵死（那种情况下过滤器
     * 一个也匹配不上，玩家还完全看不出原因）。</p>
     */
    private static boolean matches(List<LinkFilterSlot> filter, ItemStack stack) {
        boolean anyMarker = false;
        for (LinkFilterSlot slot : filter) {
            if (!slot.isItem()) {
                continue;
            }
            anyMarker = true;
            if (ItemStack.isSameItemSameComponents(slot.item(), stack)) {
                return true;
            }
        }
        return !anyMarker;
    }

    /**
     * 流体过滤：只比流体标记。
     *
     * <p>标记存的就是流体本身（不是装它的桶），所以这里直接比流体种类与组件，不再去翻容器的
     * 流体能力——没有桶的流体也能标记上了。没有任何流体标记时视为「不限制」。</p>
     */
    private static boolean matchesFluid(List<LinkFilterSlot> filter, FluidStack fluid) {
        boolean anyMarker = false;
        for (LinkFilterSlot slot : filter) {
            if (!slot.isFluid()) {
                continue;
            }
            anyMarker = true;
            if (FluidStack.isSameFluidSameComponents(slot.fluid(), fluid)) {
                return true;
            }
        }
        return !anyMarker;
    }

    /**
     * 化学品过滤：标记是「装有该化学品的储罐物品」，比对的是罐里装的化学品种类。
     *
     * <p>化学品集成只提供了「从物品里读出化学品」这一条路，没有可序列化的化学品栈，所以这一族
     * 仍然是物品标记。没有任何物品标记时视为「不限制」。</p>
     */
    private static boolean matchesChemical(List<LinkFilterSlot> filter, ChemicalStackView chemical) {
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return true;
        }
        boolean anyMarker = false;
        for (LinkFilterSlot slot : filter) {
            if (!slot.isItem()) {
                continue;
            }
            anyMarker = true;
            ChemicalStackView contained = provider.chemicalInItem(slot.item());
            if (contained != null && chemical.isSameType(contained)) {
                return true;
            }
        }
        return !anyMarker;
    }
}
