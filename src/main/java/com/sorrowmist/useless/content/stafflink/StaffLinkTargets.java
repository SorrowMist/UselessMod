package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import com.sorrowmist.useless.compat.ae.AeChemicalCompatLoader;
import com.sorrowmist.useless.compat.ae.AeEnergyCompatLoader;
import com.sorrowmist.useless.compat.ae.AeGenericInvCompatLoader;
import com.sorrowmist.useless.compat.ae.AeLogisticsCompatLoader;
import com.sorrowmist.useless.compat.ae.AePressureCompatLoader;
import com.sorrowmist.useless.compat.ae.AeSourceCompatLoader;
import com.sorrowmist.useless.compat.ars.ArsSourceCompatLoader;
import com.sorrowmist.useless.compat.create.CreateStressCompatLoader;
import com.sorrowmist.useless.compat.modernindustrialization.MiEnergyCompatLoader;
import com.sorrowmist.useless.compat.pneumaticcraft.PneumaticCraftPressureCompatLoader;
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
import java.util.concurrent.atomic.AtomicLong;

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
            LinkMedium.AE_CHEMICAL, LinkMedium.AE_SOURCE, LinkMedium.AE_ENERGY,
            // 应力排在最后：它只对动力学方块解析得出，而动力学方块永远不是容器，
            // 所以放在哪一位都不会抢走别的介质；放最后纯粹是让「默认介质」的语义保持好读。
            LinkMedium.STRESS,
            // 气压同样排最后：它只对气动方块 / AE 网络解析得出，而气动方块通常也带物品能力
            // （升级槽），排最后才不会把「绑定一台气动机器」默认判成物品线路。
            LinkMedium.PRESSURE, LinkMedium.AE_PRESSURE
    };

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
            case ITEM -> genericItems(level, pos, side);
            case FLUID -> genericFluids(level, pos, side);
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
            // 应力没有「能力」可查：它的端点是动力学方块实体本身，交给桥去判定。
            // 常驻代码只拿到一个不透明的对象做 null 判断，因此没装对应模组时这段照常加载。
            case STRESS -> CreateStressCompatLoader.resolveEndpoint(level, pos);
            // 气压：方块侧走气动工艺的空气处理器，网络侧走 Applied Pneumatics 存进 ME 网络的空气。
            case PRESSURE -> PneumaticCraftPressureCompatLoader.pressureEndpoint(level, pos, side);
            case AE_PRESSURE -> AePressureCompatLoader.pressureEndpoint(level, pos);
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
     * 物品端点：<b>优先</b>走 AE2 的「局部通用库存」，探不到再退回原生 {@code IItemHandler}。
     *
     * <p>ME 接口这类方块同时有两条路可达：AE2 为它们额外注册了 {@code ItemHandler.BLOCK}
     * （把 generic 库存投影成 {@code GenericStackItemStorage}），于是原先走的是投影。直接走
     * generic 能省掉「每次读数量 / 抽取都要 {@code AEKey → ItemStack} 物化」的那一大笔
     * （见内部无线物流性能分析报告第十三节）。</p>
     *
     * <p><b>两条路谁赢结果一样</b>：投影本就是 generic 的纯视图，且没有方块会同时是「真容器」
     * 和「generic 库存」（{@code AEBaseInvBlockEntity} 的子类如驱动器/箱子只注册后者中的
     * {@code ItemHandler.BLOCK}，不注册 generic）。</p>
     *
     * <p><b>不复用 {@link #capability}</b>：generic 的注册 lambda 忽略 context，任何面都返回同一个
     * 库存对象，一次 {@code getCapability} 就够；复用那套「先试记忆面、再试 6 个方向」是白付，
     * 还会污染 {@link #CAPABILITY_HINTS}。</p>
     */
    @Nullable
    private static LongItemHandler genericItems(Level level, BlockPos pos, @Nullable Direction side) {
        LongItemHandler generic = AeGenericInvCompatLoader.itemEndpoint(level, pos, side);
        return generic != null
                ? generic
                : items(capability(level, Capabilities.ItemHandler.BLOCK, pos, side, LinkMedium.ITEM));
    }

    /** 流体端点：优先 AE2 局部通用库存，退回原生 {@code IFluidHandler}。理由同上。 */
    @Nullable
    private static LongFluidHandler genericFluids(Level level, BlockPos pos, @Nullable Direction side) {
        LongFluidHandler generic = AeGenericInvCompatLoader.fluidEndpoint(level, pos, side);
        return generic != null
                ? generic
                : fluids(capability(level, Capabilities.FluidHandler.BLOCK, pos, side, LinkMedium.FLUID));
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
        // 过滤器两端都算数：释放端决定「允许抽出什么」，接收端决定「允许收下什么」，
        // 一个资源必须同时通过两边才允许搬运。只看一端的话，接收端设的过滤器等于没设。
        //
        // 唯一的例外是「从 AE 取出」：源端是 ME 网络时源端过滤器不参与判定，拿什么完全由
        // 接收端的白名单决定（见 LinkMedium#isAe）。网络里动辄上千种物品，让玩家在源端列
        // 白名单既繁琐又容易漏，而「我要往这个箱子拿什么」本来就该由接收端说。
        boolean sourceIsAe = source.medium().isAe();
        List<LinkFilterSlot> sourceFilter = sourceIsAe ? List.of() : source.activeFilters();
        List<LinkFilterSlot> targetFilter = target.activeFilters();

        // ---- Req3：源端是 ME 网络 + 接收端没有任何「类型对得上」的白名单 ⇒ 一个也不搬 ----
        //
        // 理由：上面那条例外把「拿什么」的决定权<b>整条</b>交给了接收端。如果接收端什么都没写，
        // 那条例外就退化成「把整张网络灌进这个容器」——而网络里可能有几千种东西，
        // 这是个破坏性后果。所以「没有白名单」在 AE 源这一侧必须解释为「不搬」，
        // 而不是「不限制」（后者是非 AE 源的语义，保持不变）。
        //
        // ⚠️ 这条规则<b>只对「网络里能装多种资源」的族成立</b>：能量与魔源从网络里出来只有一种，
        // 既没有灌满容器的风险，也根本没有可标记物（界面不给填）。对它们套这条规则 = 把线路锁死，
        // 玩家搬不动又无处解锁。判据封装在 StaffLinkRoute#blocksAeSourceWithoutWhitelist。
        //
        // 放在 resolve(target) 之前：省一次目标端解析。
        if (sourceIsAe && target.blocksAeSourceWithoutWhitelist()) {
            return 0L;
        }

        Object to = resolve(targetLevel, target.anchor().pos(), target.side(), target.medium());
        if (to == null) {
            return 0L;
        }
        // 源端是 ME 网络 ⇒ 抽出来的东西一定塞得回去 ⇒ 不必先问目标能收多少。见方法说明。
        boolean skipTargetProbe = sourceIsAe;
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
            // 应力<b>不走逐对搬运</b>：转速与应力容量是整张网络的连续状态量，不是「一轮搬多少」，
            // 必须拿到同线路上全部输入与输出才能分配。它由 StaffLinkEngine#runNetwork 里的
            // 专用分支整条处理，永远走不到这里。
            case STRESS -> 0L;
            // 气压也不走逐对搬运：它是「把接收端伺服到目标气压」的状态量，可能要把接收端的空气
            // 抽掉（排到环境），且搬运量来自压力差而不是 amount（amount 已被复用为目标气压）。
            // 它由 StaffLinkEngine#runNetwork 里的专用分支整条处理，永远走不到这里。
            case PRESSURE, AE_PRESSURE -> 0L;
        };
    }

    private static long moveItems(ServerLevel sourceLevel, BlockPos sourcePos,
                                  LongItemHandler from, LongItemHandler to, long limit,
                                  List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                  boolean skipTargetProbe) {
        // ---- 路径三：有模式标记（#tag / 通配符）⇒ 只能扫描源端 + 谓词 ----
        //
        // 模式匹配的是一个「类」，没法变成单一模板去调 extractMatching，所以必须逐种试。
        // 这条路的顺序是「先模式、后具体标记」：模式那部分本来就不可能有零枚举的走法。
        if (hasPatternMarker(sourceFilter) || hasPatternMarker(targetFilter)) {
            return sweepItems(sourceLevel, sourcePos, from, to, limit,
                    sourceFilter, targetFilter, skipTargetProbe);
        }

        // ---- 路径一：两侧都只有「具体标记」（或完全没标记）⇒ 按类型直接抽，绝不扫描 ----
        //
        // 「要搬什么」已经被过滤器完全确定了，直接问源端「有没有这一种」即可 ——
        // 这一步合并进了 moveOneItemStackByType（源端试算一次）。
        //
        // 不能改成「扫源端槽位、挑匹配的」：AE 端点的 getSlots() 是整个 ME 网络的资源种类数
        // （成熟网络上千种很常见），扫描会退化成「把整张表走一遍」；而 extractMatching 是
        // 按资源键的哈希查找。
        //
        // 同样不能改成「先 findSlot 定位槽位、再 extract(slot)」：AE 端点的 findSlot 需要先抓
        // 一份整网快照（MEStorage.getAvailableStacks()），等于每次搬运遍历一遍网络里每一种资源。
        // 实测这一条就占了无线物流总耗时的 32%（见内部无线物流性能分析报告第十节）。
        List<ItemStack> wanted = filteredItemMarkers(sourceFilter, targetFilter);
        if (!wanted.isEmpty()) {
            long moved = 0L;
            for (ItemStack template : wanted) {
                if (moved >= limit) {
                    break;
                }
                // 两端一起判定（白名单/黑名单 + 两条控制条件），顺带算出这一种能搬多少。
                Gate gate = evaluateItemGate(sourceFilter, targetFilter, from, to, template, limit - moved);
                if (!gate.allowed) {
                    continue;
                }
                moved += moveOneItemStackByType(sourceLevel, sourcePos, from, to,
                        limit - moved, template, gate.movableLimit, gate.roomLimit, skipTargetProbe);
            }
            return moved;
        }

        // ---- 路径二：完全没有标记 ⇒ 沿用扫描，命中即搬 ----
        return sweepItems(sourceLevel, sourcePos, from, to, limit,
                sourceFilter, targetFilter, skipTargetProbe);
    }

    /**
     * <b>完整的一轮扫描</b>：把源端所有槽走一遍，能搬的都搬，最多搬 {@code limit}。
     *
     * <p><b>刻意没有「扫描预算」这个上限。</b>早先这里有个 {@code MAX_SCAN_SLOTS = 256}，
     * 当时写的理由是「防止每 tick 把上千条目全走一遍」。但那是<b>语义错误</b>而不是性能折衷：
     * 循环每轮都从 slot 0 开始，一旦排在前面的条目总是把 {@code limit} 用满，
     * 排在后面的资源就<b>永远轮不到</b>——表现是「接收端设了过滤的机器一直收不到东西」。</p>
     *
     * <p>去掉预算为什么反而安全：</p>
     *
     * <ul>
     *   <li>循环的退出条件本来就是 {@code moved < limit}。源端有东西可搬时，通常扫几种就够
     *       {@code limit} 退出了；遍历长度由「实际搬没搬到」决定，不是「把整张表走完」。</li>
     *   <li>对 AE 源端点，{@code getSlots()} 会先抓一份整网快照（一次
     *       {@code MEStorage.getAvailableStacks()}）。<b>这份枚举本来就已付</b>——现在的做法
     *       只是「枚举一次 + 逐条试」，预算省的只是「枚举之后」的比较，枚举本身一分没省。</li>
     *   <li>真正担心的「源端一直搬不动、一路扫到底」场景，由 {@link StaffLinkEngine} 的
     *       <b>指数退避</b>兜底：连续空转（{@code moved == 0}）时下一轮间隔按
     *       {@code interval << n} 翻倍、封顶 32 倍。<b>这是删掉预算的前置条件</b>，
     *       已在引擎侧确认成立。</li>
     * </ul>
     */
    private static long sweepItems(ServerLevel sourceLevel, BlockPos sourcePos,
                                   LongItemHandler from, LongItemHandler to, long limit,
                                   List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                   boolean skipTargetProbe) {
        long moved = 0L;
        int slots = from.getSlots();
        for (int slot = 0; slot < slots && moved < limit; slot++) {
            // peekStack 而不是 getStackInSlot：扫描只需判类型，不必付一次 copy。
            // 这个模板在 settleItemLeftover 里若真有余量退回，那边会先固化一份。
            ItemStack template = from.peekStack(slot);
            if (template.isEmpty()) {
                continue;
            }
            // 数量顺手从刚拿到的模板上取（见 LongItemHandler#amountIn(int, ItemStack)）：
            // 通用实现里模板的 count 就是槽内真实数量，不必再把同一个槽位读第二遍。
            // 它同时充当「源端自身存量」查询失败时的回退值，所以先取。
            long available = from.amountIn(slot, template);
            Gate gate = evaluateItemGate(sourceFilter, targetFilter, from, to, template, available);
            if (!gate.allowed) {
                continue;
            }
            moved += moveOneItemStack(sourceLevel, sourcePos, from, to, slot,
                    limit - moved, template, available,
                    gate.movableLimit, gate.roomLimit, skipTargetProbe);
        }
        return moved;
    }

    private static final long NOT_COMPUTED = Long.MIN_VALUE;

    /**
     * 一条线路的「门控结果」：允不允许搬，以及能搬多少。
     *
     * <p>把旧的 {@code keepFor}/{@code maxFor} 合并成一次求值：遍历过滤格时既判定
     * 白名单/黑名单与两条控制条件，又把「测 A 自身」的条件折算成两个搬运量上限。</p>
     *
     * <ul>
     *   <li>{@link #movableLimit}：最多能搬走多少（来自「输出端 ≥ self」，= 源存量 − N）。</li>
     *   <li>{@link #roomLimit}：目标最多还能收多少（来自「输入端 ≤ self」，= N − 目标存量）。</li>
     *   <li>两者默认 {@link Long#MAX_VALUE}，表示不限制——此时一次 {@code amountOf} 都不查。</li>
     * </ul>
     */
    private static final class Gate {
        boolean allowed = true;
        long movableLimit = Long.MAX_VALUE;
        long roomLimit = Long.MAX_VALUE;
        /** 源端 A 自身存量；{@link #NOT_COMPUTED} 表示还没查。 */
        long sourceSelf = NOT_COMPUTED;
        /** 目标端 A 自身存量；{@link #NOT_COMPUTED} 表示还没查。 */
        long targetSelf = NOT_COMPUTED;
        /** 源端存量查询失败（返回 ≤0）时的回退值。 */
        long sourceFallback;
    }

    /**
     * 物品门控：两端过滤器都判定一遍，结果合进一个 {@link Gate}。
     *
     * @param sourceFallback 源端自身存量查询失败时的回退值（扫描路径传槽内存量、按类型路径传请求量）
     */
    private static Gate evaluateItemGate(List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                         LongItemHandler from, LongItemHandler to, ItemStack template,
                                         long sourceFallback) {
        Gate gate = new Gate();
        gate.sourceFallback = sourceFallback;
        gate.allowed = evalItemFilter(sourceFilter, gate, from, to, template)
                && evalItemFilter(targetFilter, gate, from, to, template);
        return gate;
    }

    /**
     * 单侧判定：返回「这一端允不允许这种物品」。
     *
     * <p>规则（兼容旧的「无标记 = 不限制」白名单语义，并加上黑名单与条件）：</p>
     * <ol>
     *   <li>命中任一<b>黑名单</b>格（标记匹配且条件成立）⇒ 直接不允许。</li>
     *   <li>否则若这一端存在<b>白名单</b>格 ⇒ 必须至少命中一个（标记匹配且条件成立）。</li>
     *   <li>否则（没有白名单格）⇒ 不限制。</li>
     * </ol>
     */
    private static boolean evalItemFilter(List<LinkFilterSlot> filter, Gate gate,
                                          LongItemHandler from, LongItemHandler to, ItemStack template) {
        boolean hasInclude = false;
        boolean includeApplies = false;
        for (LinkFilterSlot slot : filter) {
            if (!slot.isItem() && !slot.isPattern()) {
                continue;
            }
            if (!slot.exclude()) {
                hasInclude = true;
            }
            if (!matchesSlot(slot, template)) {
                continue;
            }
            if (!itemConditionsHold(slot, gate, from, to, template)) {
                continue;
            }
            if (slot.exclude()) {
                return false;
            }
            includeApplies = true;
        }
        return !hasInclude || includeApplies;
    }

    /** 这一格的两条控制条件是否都成立；成立时把「测 A 自身」的条件折算进 gate 的两个上限。 */
    private static boolean itemConditionsHold(LinkFilterSlot slot, Gate gate,
                                              LongItemHandler from, LongItemHandler to, ItemStack template) {
        LinkFilterCondition out = slot.outCond();
        if (!out.isOff()) {
            if (out.isSelf()) {
                long source = itemAmountAtSource(gate, from, template);
                if (out.op() == LinkFilterCondition.Op.AT_LEAST) {
                    if (source < out.value()) {
                        return false;
                    }
                    // 保留 N 在输出端：最多搬走 源存量 − N（= 旧的「源端保留」）。
                    gate.movableLimit = Math.min(gate.movableLimit, source - out.value());
                } else if (source > out.value()) {
                    return false;
                }
            } else if (!itemConditionHoldsOn(from, out)) {
                return false;
            }
        }
        LinkFilterCondition in = slot.inCond();
        if (!in.isOff()) {
            if (in.isSelf()) {
                long target = itemAmountAtTarget(gate, to, template);
                if (in.op() == LinkFilterCondition.Op.AT_MOST) {
                    if (target > in.value()) {
                        return false;
                    }
                    // 目标封顶 N：最多再收 N − 目标存量（= 旧的「接收端上限」）。
                    gate.roomLimit = Math.min(gate.roomLimit, in.value() - target);
                } else if (target < in.value()) {
                    return false;
                }
            } else if (!itemConditionHoldsOn(to, in)) {
                return false;
            }
        }
        return true;
    }

    /** 源端 A 自身存量，惰性查询 + 缓存；查询失败时回退到调用方给的值。 */
    private static long itemAmountAtSource(Gate gate, LongItemHandler from, ItemStack template) {
        if (gate.sourceSelf == NOT_COMPUTED) {
            long total = from.amountOf(template);
            gate.sourceSelf = total > 0L ? total : gate.sourceFallback;
        }
        return gate.sourceSelf;
    }

    /** 目标端 A 自身存量，惰性查询 + 缓存。 */
    private static long itemAmountAtTarget(Gate gate, LongItemHandler to, ItemStack template) {
        if (gate.targetSelf == NOT_COMPUTED) {
            gate.targetSelf = to.amountOf(template);
        }
        return gate.targetSelf;
    }

    /** 控制材料是「别的物品」时，在给定端点上量它的存量再比较。 */
    private static boolean itemConditionHoldsOn(LongItemHandler handler, LinkFilterCondition cond) {
        if (!cond.isItemControl()) {
            // 控制材料不是物品（异族）：物品端点上量不了，忽略这条（服务端清洗会把它清掉）。
            return true;
        }
        long amount = handler.amountOf(cond.item());
        return cond.op() == LinkFilterCondition.Op.AT_LEAST
                ? amount >= cond.value()
                : amount <= cond.value();
    }

    /**
     * 处理「已经抽出来了、但目标没全收下」的余量：退回源，退不回就掉在源脚边。返回实际搬走的量。
     *
     * <p>抽出来之后才发现目标不收，是<b>跳过目标试算</b>那条路的正常分支（见
     * {@link #transfer(Object, ServerLevel, StaffLinkRoute, ServerLevel, StaffLinkRoute, long)}）。</p>
     *
     * <p><b>{@code template} 可能是源容器持有的活对象</b>（无过滤器的扫描路径用的是
     * {@link LongItemHandler#peekStack(int)}，它不 copy）。被抽空时那个对象会就地变成空栈，
     * 拿它去退回等于塞空气、物品凭空蒸发 —— 所以退回前这里再固化一份。
     * <b>「写进目标端」那一步同样依赖模板</b>，而它发生在调用方那一侧，所以那边必须<b>在抽取
     * 之前</b>就固化（见 {@code stabilize}）；这里的 copy 是余量路径的最后一道防线。</p>
     *
     * <p>这次 copy 只在<b>真有余量</b>时才会付，正常路径（抽多少收多少）完全不付。</p>
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
            // 固化模板：template 可能正被源容器持有，抽取已经改动了它。
            ItemStack stable = template.copy();
            long unreturned = leftover - from.insert(stable, leftover, false);
            if (unreturned > 0L) {
                // 退回不了就落地。这条以前是静默的，而「东西抽出来了、目标没收、源也塞不回」
                // 正是最容易被误当成「凭空消失」的场景（其实是在源脚边），必须留痕。
                UselessMod.LOGGER.warn(
                        "无线物流：{} 个 {} 未能写入目标端、也退不回源端，已掉落在源容器脚边",
                        unreturned, stable.getItem());
                dropItems(sourceLevel, sourcePos, stable, unreturned);
                actuallyMoved -= unreturned;
            }
        }
        return Math.max(0L, actuallyMoved);
    }

    /**
     * 从指定槽位搬走最多 {@code want} 个 {@code template}；返回实际搬走的数量。
     *
     * <p>只给<b>无过滤器 / 模式过滤器</b>的扫描路径用（只有具体标记的走
     * {@link #moveOneItemStackByType}）。</p>
     *
     * @param available 该槽位的存量；扫描路径已经连同模板一起取过，避免重复读同一个槽位
     * @param movableLimit 输出端条件折算出的搬运量上限（{@link Long#MAX_VALUE} = 不限）
     * @param roomLimit    输入端条件折算出的目标可收量上限（{@link Long#MAX_VALUE} = 不限）
     */
    private static long moveOneItemStack(ServerLevel sourceLevel, BlockPos sourcePos,
                                         LongItemHandler from, LongItemHandler to, int slot,
                                         long want, ItemStack template, long available,
                                         long movableLimit, long roomLimit,
                                         boolean skipTargetProbe) {
        if (want <= 0L || available <= 0L) {
            return 0L;
        }
        long request = requestAmount(want, available, movableLimit, roomLimit);
        if (request <= 0L) {
            return 0L;
        }
        if (skipTargetProbe) {
            long extracted = from.extract(slot, request, false);
            if (extracted <= 0L) {
                return 0L;
            }
            // 这条分支只在源端是 AE 时才会走到（skipTargetProbe 就是 sourceIsAe），
            // 而 AE 端点交出的模板一律是新对象（{@code toStack(1)} / {@code key.toStack(1)}），
            // 抽取不会动它 —— 所以这里不需要下面那次固化。
            return settleItemLeftover(sourceLevel, sourcePos, from, template, extracted,
                    insertInto(to, template, extracted));
        }
        // 模拟：目标最多能接多少（long，跨槽累加精确）。
        long accepted = to.insert(template, request, true);
        if (accepted <= 0L) {
            return 0L;
        }
        // ⚠️ 抽取前必须先固化模板（见 stabilize 的说明）。
        ItemStack stable = accepted >= available ? stabilize(template) : template;
        long extracted = from.extract(slot, accepted, false);
        if (extracted <= 0L) {
            return 0L;
        }
        return settleItemLeftover(sourceLevel, sourcePos, from, stable, extracted,
                insertInto(to, stable, extracted));
    }

    /**
     * 抽取前把源容器持有的那只模板栈固化一份。
     *
     * <p><b>为什么必须固化。</b>扫描路径的模板来自 {@link LongItemHandler#peekStack(int)}，它
     * <b>故意不 copy</b>（省掉实测占无线物流总耗时 13% 的一次拷贝），交出的可能就是源容器
     * <b>槽里那只对象本身</b>。而容器抽空一个槽时就地改写这只栈是家常便饭 —— 最典型的就是
     * <b>原版箱子 / 木桶</b>：{@code InvWrapper.extractItem} 走 {@code Container#removeItem}，
     * 即 {@code ContainerHelper.removeItem} 里的 {@code stack.split(amount)}，而
     * {@code split} 的语义就是<b>就地 shrink 自己</b>。于是我们手里那只模板跟着变成空栈。</p>
     *
     * <p>模板在抽取之后还要用<b>两次</b>：写进目标端、以及余量退回源端。变成空栈之后这两步都会
     * 静默失败（{@code insert} 见到空栈直接返回 0，连日志都不会打），结果就是
     * <b>「东西从源里抽走了，却既没进目标也没退回，凭空蒸发」</b>。流体侧没有这个问题，是因为
     * {@code getFluidInTank} 一律返回副本，注释里写明了同样的理由。</p>
     *
     * <p><b>只在真会被抽空时才付这次拷贝。</b>抽取请求量恒 ≤ 该槽存量，所以
     * {@code accepted >= available}（等价于「目标全要、这一抽正好把槽清空」）是「模板会被就地
     * 抽空」的充要条件；其余情况槽里必然还剩东西，模板对象依旧可用（数量在这条路上不参与任何
     * 判定：写入方一律自己设 count，退回/掉落也走 {@code copyWithCount}）。</p>
     *
     * <p>另一个方向的保守性：容器若每次只让渡 1 个（SophisticatedCore 那类），
     * {@code extract} 会因 {@code MAX_CHUNK_STEPS} 提前收手、槽其实没被抽空，这时多付的这次
     * 拷贝只是浪费，不会改变行为。</p>
     */
    private static ItemStack stabilize(ItemStack template) {
        return template.copy();
    }

    /**
     * 写进目标端，并盯住「模板在抽取途中被抽坏」这种本该不可能发生的死账。
     *
     * <p>正常路径只是转发一次调用、零开销。模板真被抽空时（{@link #stabilize} 的判据没覆盖到
     * 的异常容器：报了部分抽取却把槽清空了），{@code insert} 会静默返回 0，随后余量退回同样
     * 静默失败 —— 物品就此消失且不留任何痕迹。这里补一条限流警告，让这种损失至少可见。</p>
     */
    private static long insertInto(LongItemHandler to, ItemStack template, long amount) {
        if (template.isEmpty()) {
            warnTemplateVoided(amount);
            return 0L;
        }
        return to.insert(template, amount, false);
    }

    /** 「模板被抽空」告警的限流：每 {@link #TEMPLATE_WARN_INTERVAL_MS} 毫秒最多一条。 */
    private static final long TEMPLATE_WARN_INTERVAL_MS = 5000L;
    private static final AtomicLong LAST_TEMPLATE_WARN_AT = new AtomicLong();

    private static void warnTemplateVoided(long amount) {
        long now = System.currentTimeMillis();
        long last = LAST_TEMPLATE_WARN_AT.get();
        if (now - last < TEMPLATE_WARN_INTERVAL_MS || !LAST_TEMPLATE_WARN_AT.compareAndSet(last, now)) {
            return;
        }
        UselessMod.LOGGER.warn(
                "无线物流：源容器抽取时就地清空了模板栈，{} 个物品无法写入目标端（模板固化判据未覆盖）",
                amount);
    }

    /**
     * 把「想要搬多少 / 这一槽有多少 / 两个条件折算出的上限」收敛成一个请求量。
     *
     * <p>两个上限由 {@link Gate} 在门控阶段算好：{@code movableLimit}（输出端保留折算）与
     * {@code roomLimit}（输入端封顶折算）。两者默认 {@link Long#MAX_VALUE} = 不限制，
     * 此时这里不做任何查询——<b>AE 端点上的 {@code amountOf} 会抓整网快照，绝不能被当成免费查询</b>。</p>
     */
    private static long requestAmount(long want, long available, long movableLimit, long roomLimit) {
        long request = Math.min(want, available);
        if (movableLimit != Long.MAX_VALUE) {
            request = Math.min(request, movableLimit);
        }
        if (roomLimit != Long.MAX_VALUE) {
            request = Math.min(request, roomLimit);
        }
        return Math.max(0L, request);
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
                                               long movableLimit, long roomLimit,
                                               boolean skipTargetProbe) {
        if (want <= 0L) {
            return 0L;
        }
        // 上限已在门控阶段算好；这里 available 未知（= want），只做一次取小。
        long request = requestAmount(want, want, movableLimit, roomLimit);
        if (request <= 0L) {
            return 0L;
        }
        if (skipTargetProbe) {
            // 源端一次调用完成「定位 + 抽取」：抽到多少就是多少，目标收不下的余量退回源。
            long extracted = from.extractMatching(template, request, false);
            if (extracted <= 0L) {
                return 0L;
            }
            return settleItemLeftover(sourceLevel, sourcePos, from, template, extracted,
                    to.insert(template, extracted, false));
        }
        // 源端试算：既回答「有没有这一种」，也给出实际可抽量。
        long available = from.extractMatching(template, request, true);
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
            // 黑名单格不是「候选搬运对象」：它只负责挡，不负责搬。
            if (!slot.isItem() || slot.exclude()) {
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
     * 流体搬运。
     *
     * <p>模拟阶段两边都给的是 long 精确上限，因此 {@code limit} 能一次性用满。
     * 与物品侧一样有三条路径（模式 / 具体标记 / 无标记），且<b>同样没有扫描预算</b>
     * （理由见 {@link #sweepItems}）。</p>
     */
    private static long moveFluid(LongFluidHandler from, LongFluidHandler to, long limit,
                                  List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                  boolean skipTargetProbe) {
        if (hasPatternMarker(sourceFilter) || hasPatternMarker(targetFilter)) {
            return sweepFluids(from, to, limit, sourceFilter, targetFilter, skipTargetProbe);
        }

        // 有过滤器时按类型直接抽，理由同 moveItems：AE 端点的 getTanks() 是网络里的流体
        // 种类数，靠扫描要遍历整张表；而 findTank 又要先抓整网快照。
        List<FluidStack> wanted = filteredFluidMarkers(sourceFilter, targetFilter);
        if (!wanted.isEmpty()) {
            for (FluidStack type : wanted) {
                Gate gate = evaluateFluidGate(sourceFilter, targetFilter, from, to, type, limit);
                if (!gate.allowed) {
                    continue;
                }
                long moved = moveOneFluidByType(from, to, limit, type,
                        gate.movableLimit, gate.roomLimit, skipTargetProbe);
                if (moved > 0L) {
                    // 一次只搬一种：搬动了就收工。
                    return moved;
                }
            }
            return 0L;
        }

        return sweepFluids(from, to, limit, sourceFilter, targetFilter, skipTargetProbe);
    }

    /**
     * 流体侧的一轮完整扫描（无预算，理由同 {@link #sweepItems}）。
     *
     * <p>与原实现一致：<b>一次只搬一种</b>——搬动了就返回，不等搬满 {@code limit}。
     * 这是流体侧一直以来的语义（一次搬运给一种流体），保持不动。</p>
     */
    private static long sweepFluids(LongFluidHandler from, LongFluidHandler to, long limit,
                                    List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                    boolean skipTargetProbe) {
        int tanks = from.getTanks();
        for (int tank = 0; tank < tanks; tank++) {
            FluidStack type = from.getFluidInTank(tank);
            if (type.isEmpty()) {
                continue;
            }
            Gate gate = evaluateFluidGate(sourceFilter, targetFilter, from, to, type,
                    from.amountIn(tank));
            if (!gate.allowed) {
                continue;
            }
            long moved = moveOneFluid(from, to, tank, limit, type,
                    gate.movableLimit, gate.roomLimit, skipTargetProbe);
            if (moved > 0L) {
                return moved;
            }
        }
        return 0L;
    }

    /** 流体门控：与 {@link #evaluateItemGate} 同构。 */
    private static Gate evaluateFluidGate(List<LinkFilterSlot> sourceFilter, List<LinkFilterSlot> targetFilter,
                                          LongFluidHandler from, LongFluidHandler to, FluidStack type,
                                          long sourceFallback) {
        Gate gate = new Gate();
        gate.sourceFallback = sourceFallback;
        gate.allowed = evalFluidFilter(sourceFilter, gate, from, to, type)
                && evalFluidFilter(targetFilter, gate, from, to, type);
        return gate;
    }

    /** 单侧流体判定：规则与 {@link #evalItemFilter} 相同。 */
    private static boolean evalFluidFilter(List<LinkFilterSlot> filter, Gate gate,
                                           LongFluidHandler from, LongFluidHandler to, FluidStack type) {
        boolean hasInclude = false;
        boolean includeApplies = false;
        for (LinkFilterSlot slot : filter) {
            if (!slot.isFluid() && !slot.isPattern()) {
                continue;
            }
            if (!slot.exclude()) {
                hasInclude = true;
            }
            if (!matchesSlotFluid(slot, type)) {
                continue;
            }
            if (!fluidConditionsHold(slot, gate, from, to, type)) {
                continue;
            }
            if (slot.exclude()) {
                return false;
            }
            includeApplies = true;
        }
        return !hasInclude || includeApplies;
    }

    /** 流体版的两条控制条件判定；折算规则同 {@link #itemConditionsHold}。 */
    private static boolean fluidConditionsHold(LinkFilterSlot slot, Gate gate,
                                               LongFluidHandler from, LongFluidHandler to, FluidStack type) {
        LinkFilterCondition out = slot.outCond();
        if (!out.isOff()) {
            if (out.isSelf()) {
                long source = fluidAmountAtSource(gate, from, type);
                if (out.op() == LinkFilterCondition.Op.AT_LEAST) {
                    if (source < out.value()) {
                        return false;
                    }
                    gate.movableLimit = Math.min(gate.movableLimit, source - out.value());
                } else if (source > out.value()) {
                    return false;
                }
            } else if (!fluidConditionHoldsOn(from, out)) {
                return false;
            }
        }
        LinkFilterCondition in = slot.inCond();
        if (!in.isOff()) {
            if (in.isSelf()) {
                long target = fluidAmountAtTarget(gate, to, type);
                if (in.op() == LinkFilterCondition.Op.AT_MOST) {
                    if (target > in.value()) {
                        return false;
                    }
                    gate.roomLimit = Math.min(gate.roomLimit, in.value() - target);
                } else if (target < in.value()) {
                    return false;
                }
            } else if (!fluidConditionHoldsOn(to, in)) {
                return false;
            }
        }
        return true;
    }

    private static long fluidAmountAtSource(Gate gate, LongFluidHandler from, FluidStack type) {
        if (gate.sourceSelf == NOT_COMPUTED) {
            long total = from.amountOf(type);
            gate.sourceSelf = total > 0L ? total : gate.sourceFallback;
        }
        return gate.sourceSelf;
    }

    private static long fluidAmountAtTarget(Gate gate, LongFluidHandler to, FluidStack type) {
        if (gate.targetSelf == NOT_COMPUTED) {
            gate.targetSelf = to.amountOf(type);
        }
        return gate.targetSelf;
    }

    private static boolean fluidConditionHoldsOn(LongFluidHandler handler, LinkFilterCondition cond) {
        if (!cond.isFluidControl()) {
            // 控制材料不是流体（异族）：流体端点上量不了，忽略这条。
            return true;
        }
        long amount = handler.amountOf(cond.fluid());
        return cond.op() == LinkFilterCondition.Op.AT_LEAST
                ? amount >= cond.value()
                : amount <= cond.value();
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
                                     long limit, FluidStack type,
                                     long movableLimit, long roomLimit, boolean skipTargetProbe) {
        long available = from.amountIn(tank);
        if (available <= 0L) {
            return 0L;
        }
        long request = fluidRequest(Math.min(limit, available), movableLimit, roomLimit);
        if (request <= 0L) {
            return 0L;
        }
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
     * 把「想要搬多少 / 两个条件折算出的上限」收敛成一个流体请求量。
     *
     * <p>与物品侧的 {@link #requestAmount} 完全对应；上限已在门控阶段算好，
     * {@link Long#MAX_VALUE} 表示不限制。</p>
     */
    private static long fluidRequest(long want, long movableLimit, long roomLimit) {
        long request = Math.max(0L, want);
        if (movableLimit != Long.MAX_VALUE) {
            request = Math.min(request, movableLimit);
        }
        if (roomLimit != Long.MAX_VALUE) {
            request = Math.min(request, roomLimit);
        }
        return Math.max(0L, request);
    }

    /**
     * 从源端按<b>类型</b>搬走最多 {@code limit} 的 {@code type}；返回实际搬走的量。
     *
     * <p>理由同 {@link #moveOneItemStackByType}：不经过「第几号罐」，定位与试算合并成
     * {@link LongFluidHandler#drainMatching(FluidStack, long, boolean)} 一次调用。</p>
     */
    private static long moveOneFluidByType(LongFluidHandler from, LongFluidHandler to,
                                           long limit, FluidStack type,
                                           long movableLimit, long roomLimit, boolean skipTargetProbe) {
        long request = fluidRequest(limit, movableLimit, roomLimit);
        if (request <= 0L) {
            return 0L;
        }
        if (skipTargetProbe) {
            long drained = from.drainMatching(type, request, false);
            if (drained <= 0L) {
                return 0L;
            }
            return settleFluidLeftover(from, to, type, drained, to.fill(type, drained, false));
        }
        long drainable = from.drainMatching(type, request, true);
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
            // 黑名单格不是「候选搬运对象」：它只负责挡，不负责搬。
            if (!slot.isFluid() || slot.exclude()) {
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

    /** 这一格（具体标记或模式）是否匹配这个物品。 */
    private static boolean matchesSlot(LinkFilterSlot slot, ItemStack stack) {
        // 具体标记优先：模式只在「不是物品/流体标记」时才用（三态互斥，这里只是保险）。
        if (slot.isItem()) {
            return ItemStack.isSameItemSameComponents(slot.item(), stack);
        }
        if (slot.isPattern()) {
            LinkFilterPattern pattern = LinkFilterPattern.parse(slot.pattern());
            return pattern != null && pattern.matchesItem(stack);
        }
        return false;
    }

    /** 这一格是否匹配这种流体。 */
    private static boolean matchesSlotFluid(LinkFilterSlot slot, FluidStack fluid) {
        if (slot.isFluid()) {
            return FluidStack.isSameFluidSameComponents(slot.fluid(), fluid);
        }
        if (slot.isPattern()) {
            LinkFilterPattern pattern = LinkFilterPattern.parse(slot.pattern());
            return pattern != null && pattern.matchesFluid(fluid);
        }
        return false;
    }

    /**
     * 这一格是否匹配这个化学品。
     *
     * <p>具体标记走「罐里装的化学品」；模式走 id 通配（化学品没有标签体系，
     * 见 {@link LinkFilterPattern#matchesChemicalId}）。</p>
     */
    private static boolean matchesSlotChemical(LinkFilterSlot slot, ChemicalStackView chemical) {
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return false;
        }
        if (slot.isItem()) {
            ChemicalStackView contained = provider.chemicalInItem(slot.item());
            return contained != null && chemical.isSameType(contained);
        }
        if (slot.isPattern()) {
            LinkFilterPattern pattern = LinkFilterPattern.parse(slot.pattern());
            // 给不出 id 时通配符恒不匹配：宁可什么都不搬（可观察、玩家能改），
            // 也不要因为拿不到 id 就当「不限制」而把整张网络灌出去。
            return pattern != null && pattern.matchesChemicalId(provider.chemicalIdOf(chemical.typeKey()));
        }
        return false;
    }

    /** 某一端有没有「模式」标记（只要有模式，这一端的搬运就必须走扫描 + 谓词）。 */
    private static boolean hasPatternMarker(List<LinkFilterSlot> filter) {
        for (LinkFilterSlot slot : filter) {
            if (!slot.isEmpty() && slot.isPattern()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 化学品过滤：具体标记是「装有该化学品的储罐物品」，比对的是罐里装的化学品种类；
     * 模式标记按 id 通配。
     *
     * <p>白名单/黑名单规则与物品侧一致：命中黑名单格 ⇒ 不允许；否则这一端有白名单格就必须命中一个；
     * 没有白名单格 ⇒ 不限制。<b>化学品不支持控制条件</b>（{@code ChemicalHandlerView} 没有存量查询），
     * 所以这里只看标记。</p>
     */
    private static boolean matchesChemical(List<LinkFilterSlot> filter, ChemicalStackView chemical) {
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return true;
        }
        boolean hasInclude = false;
        boolean includeApplies = false;
        for (LinkFilterSlot slot : filter) {
            if (!slot.isItem() && !slot.isPattern()) {
                continue;
            }
            if (!slot.exclude()) {
                hasInclude = true;
            }
            if (!matchesSlotChemical(slot, chemical)) {
                continue;
            }
            if (slot.exclude()) {
                return false;
            }
            includeApplies = true;
        }
        return !hasInclude || includeApplies;
    }
}
