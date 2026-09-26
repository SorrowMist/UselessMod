package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import com.sorrowmist.useless.api.logistics.LongFluidHandler;
import com.sorrowmist.useless.api.logistics.LongItemHandler;
import com.sorrowmist.useless.compat.ae.AeChemicalCompatLoader;
import com.sorrowmist.useless.compat.ae.AeLogisticsCompatLoader;
import com.sorrowmist.useless.compat.ae.AeSourceCompatLoader;
import com.sorrowmist.useless.compat.ars.ArsSourceCompatLoader;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProvider;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalCompatProviders;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalHandlerView;
import com.sorrowmist.useless.content.machines.advanced_alloy_furnace.chemical.ChemicalStackView;
import com.sorrowmist.useless.energy.IEnergyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
     * <p>AE 四种类型排在最后：它们只有在方块<b>本身不是容器</b>时才会被选中，因此把一个
     * 无线访问点当作普通箱子绑定也没问题——普通容器先命中，访问点则落到 AE 上。</p>
     */
    private static final LinkMedium[] DEFAULT_ORDER = {
            LinkMedium.ITEM, LinkMedium.FLUID, LinkMedium.ENERGY, LinkMedium.CHEMICAL,
            LinkMedium.SOURCE, LinkMedium.AE_ITEM, LinkMedium.AE_FLUID,
            LinkMedium.AE_CHEMICAL, LinkMedium.AE_SOURCE
    };

    /**
     * 一次搬运最多检视的条目数。
     *
     * <p>普通容器的条目数很小，这个上限对它们没有意义；它存在是为了 AE：ME 网络的
     * {@code getSlots()} 是网络里的资源种类数，动辄上千。每 tick 全量扫描会让服务端
     * 长时间卡在搬运循环里。截断只影响速率——本 tick 没轮到的条目下一 tick 继续。</p>
     */
    private static final int MAX_SCAN_SLOTS = 256;

    private StaffLinkTargets() {
    }

    /** 一个都没搬动时，卡在哪一步。给界面显示用，方便一眼定位。 */
    public enum TransferBlocker {
        /** 没问题（搬动了，或还没跑过）。 */
        NONE,
        /** 源那边解析不出库存：区块没加载 / 方块没了 / 没有对应能力。 */
        SOURCE_UNREACHABLE,
        /** 目标那边解析不出库存。 */
        TARGET_UNREACHABLE,
        /** 源里确实有东西，但全被过滤器挡住了。 */
        FILTERED,
        /** 源里确实有东西，也通过了过滤，但源自己拒绝抽出（只进不出 / 只读）。 */
        SOURCE_REJECTED,
        /** 源里没有可搬的东西。 */
        SOURCE_EMPTY,
        /** 源有东西、过滤也过了，但目标不收（装满 / 只认别的物品）。 */
        TARGET_REJECTED
    }

    /**
     * 搬运量为 0 时判断卡在哪一步。
     *
     * <p>只在真的一次都没搬动时调用，正常路径不付代价。</p>
     */
    public static TransferBlocker diagnose(ServerLevel sourceLevel, StaffLinkRoute source,
                                           ServerLevel targetLevel, StaffLinkRoute target) {
        Object from = resolve(sourceLevel, source.anchor().pos(), source.side(), source.medium());
        if (from == null) {
            return TransferBlocker.SOURCE_UNREACHABLE;
        }
        Object to = resolve(targetLevel, target.anchor().pos(), target.side(), target.medium());
        if (to == null) {
            return TransferBlocker.TARGET_UNREACHABLE;
        }
        // 与 transfer 同一套语义：从 AE 取出时源端过滤器不参与判定，只看接收端白名单。
        List<ItemStack> sourceFilter = source.medium().isAe() ? List.of() : source.activeFilters();
        List<ItemStack> targetFilter = target.activeFilters();
        return switch (source.medium()) {
            case ITEM, AE_ITEM -> diagnoseItems((LongItemHandler) from, sourceFilter, targetFilter);
            case FLUID, AE_FLUID -> diagnoseFluid((LongFluidHandler) from, (LongFluidHandler) to,
                    sourceFilter, targetFilter);
            case ENERGY -> diagnoseEnergy((LongEnergyHandler) from);
            case CHEMICAL, AE_CHEMICAL -> diagnoseChemical((ChemicalHandlerView) from, sourceFilter, targetFilter);
            // 魔源的端点就是「一个量」，没有条目列表可查；有量就一定搬得动，没量就是源空。
            case SOURCE, AE_SOURCE -> ((SourceHandlerView) from).amount() > 0
                    ? TransferBlocker.TARGET_REJECTED : TransferBlocker.SOURCE_EMPTY;
        };
    }

    private static TransferBlocker diagnoseItems(LongItemHandler from, List<ItemStack> sourceFilter,
                                                 List<ItemStack> targetFilter) {
        boolean anyItem = false;
        for (int slot = 0; slot < from.getSlots(); slot++) {
            ItemStack probe = from.getStackInSlot(slot);
            if (probe.isEmpty() || from.amountIn(slot) <= 0L) {
                continue;
            }
            anyItem = true;
            // 两端的过滤器都放行，才谈得上「目标不收」；任一端挡住就是被过滤。
            if (matches(sourceFilter, probe) && matches(targetFilter, probe)) {
                return TransferBlocker.TARGET_REJECTED;
            }
        }
        return anyItem ? TransferBlocker.FILTERED : TransferBlocker.SOURCE_EMPTY;
    }

    /**
     * 流体卡在哪一步。
     *
     * <p><b>必须真的去试一次。</b> 早先这里只验证「源里有流体、过滤器放行」就直接断言
     * {@code TARGET_REJECTED}，等于把「我没查到别的原因」写成了「目标不收」——目标满没满、
     * 收不收这种流体，一次都没问过。于是界面会把「源抽不出来」「目标类型不符」乃至纯粹的
     * 方向配反，统统显示成「目标不收」，把人往错的方向引。现在每一步都实际探测：
     * 源能否抽出、目标能否接收，得出的结论才是可执行的。</p>
     */
    private static TransferBlocker diagnoseFluid(LongFluidHandler from, LongFluidHandler to,
                                                 List<ItemStack> sourceFilter,
                                                 List<ItemStack> targetFilter) {
        for (int tank = 0; tank < from.getTanks(); tank++) {
            FluidStack probe = from.getFluidInTank(tank);
            if (probe.isEmpty() || from.amountIn(tank) <= 0L) {
                continue;
            }
            if (!matchesFluid(sourceFilter, probe) || !matchesFluid(targetFilter, probe)) {
                return TransferBlocker.FILTERED;
            }
            // 源真的抽得出来吗？探测量取存量与 1000 的较小值，够判断可行性又不惊动容器。
            long drainable = from.drain(tank, Math.min(1000L, from.amountIn(tank)), true);
            if (drainable <= 0L) {
                // 源有流体却抽不动：只读权限、被别的面独占、或是只进不出的容器。
                return TransferBlocker.SOURCE_REJECTED;
            }
            // 目标真的收得下吗？用同一个探测量问它。
            if (to.fill(probe, drainable, true) <= 0L) {
                return TransferBlocker.TARGET_REJECTED;
            }
            return TransferBlocker.NONE;
        }
        return TransferBlocker.SOURCE_EMPTY;
    }

    private static TransferBlocker diagnoseEnergy(LongEnergyHandler from) {
        return from.stored() > 0L ? TransferBlocker.TARGET_REJECTED : TransferBlocker.SOURCE_EMPTY;
    }

    private static TransferBlocker diagnoseChemical(ChemicalHandlerView from, List<ItemStack> sourceFilter,
                                                    List<ItemStack> targetFilter) {
        ChemicalStackView probe = from.extractChemical(1L, true);
        if (probe == null || probe.isEmpty()) {
            return TransferBlocker.SOURCE_EMPTY;
        }
        boolean allowed = matchesChemical(sourceFilter, probe) && matchesChemical(targetFilter, probe);
        return allowed ? TransferBlocker.TARGET_REJECTED : TransferBlocker.FILTERED;
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
            case ITEM -> items(capability(level, Capabilities.ItemHandler.BLOCK, pos, side));
            case FLUID -> fluids(capability(level, Capabilities.FluidHandler.BLOCK, pos, side));
            case ENERGY -> energyHandler(level, pos, side);
            case CHEMICAL -> chemicalHandler(level, pos, side);
            case SOURCE -> ArsSourceCompatLoader.sourceHandler(level, pos);
            // AE 这一端交给桥去解析：常驻代码只拿到一个 long 契约的端点，
            // 不出现任何 AE2 类型，因此没装 AE2 时这段代码照常加载、只是永远解析不出结果。
            case AE_ITEM -> AeLogisticsCompatLoader.itemEndpoint(level, pos);
            case AE_FLUID -> AeLogisticsCompatLoader.fluidEndpoint(level, pos);
            // 化学品/魔源要进 ME 网络得靠各自的附属（appmek / arseng），所以走独立的桥。
            case AE_CHEMICAL -> AeChemicalCompatLoader.chemicalEndpoint(level, pos);
            case AE_SOURCE -> AeSourceCompatLoader.sourceEndpoint(level, pos);
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
     * 能量解析：本模组的 {@link IEnergyManager} 会被识别出来并<b>原生直通</b>。
     *
     * <p>本模组机器的能量能力注册时交出的就是 {@code EnergyManager}（实现了
     * {@code IEnergyManager}），所以这里的 {@code instanceof} 判定在自家机器上必然命中；
     * 外部模组只提供 int 级 {@code IEnergyStorage} 时才退化成适配器分片。</p>
     */
    @Nullable
    private static LongEnergyHandler energyHandler(Level level, BlockPos pos, @Nullable Direction side) {
        IEnergyStorage storage = capability(level, Capabilities.EnergyStorage.BLOCK, pos, side);
        if (storage == null) {
            return null;
        }
        if (storage instanceof IEnergyManager manager) {
            return LongResourceAdapters.energy(manager);
        }
        return LongResourceAdapters.energy(storage);
    }

    @Nullable
    private static <T> T capability(Level level, BlockCapability<T, Direction> capability,
                                    BlockPos pos, @Nullable Direction side) {
        if (side != null) {
            return level.getCapability(capability, pos, side);
        }
        T unspecified = level.getCapability(capability, pos, null);
        if (unspecified != null) {
            return unspecified;
        }
        for (Direction direction : DIRECTIONS) {
            T found = level.getCapability(capability, pos, direction);
            if (found != null) {
                return found;
            }
        }
        return null;
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
     * 把资源从 {@code source} 搬到 {@code target}。
     *
     * <p>先模拟后提交：模拟阶段算出目标能接多少，提交阶段只搬那个数量；万一提交时目标
     * 仍然吐回余量（同 tick 内不该发生），余量会退回源，源也塞不回时掉落到源脚边，
     * 保证不凭空消失。</p>
     *
     * <p><b>数量全程 long。</b> {@code limit} 多大就请求多大，不存在「先截断成 int、剩下的
     * 交给外层循环」这一步。</p>
     *
     * @return 实际搬运量（0 表示没搬）
     */
    public static long transfer(ServerLevel sourceLevel, StaffLinkRoute source,
                                ServerLevel targetLevel, StaffLinkRoute target, long limit) {
        // 配对按 family 而不是 medium：ITEM 与 AE_ITEM 都是「物品」，箱子接 AE 网络才配得起来。
        if (limit <= 0L || source.medium().family() != target.medium().family()) {
            return 0L;
        }
        Object from = resolve(sourceLevel, source.anchor().pos(), source.side(), source.medium());
        if (from == null) {
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
        List<ItemStack> sourceFilter = source.medium().isAe() ? List.of() : source.activeFilters();
        List<ItemStack> targetFilter = target.activeFilters();
        return switch (source.medium()) {
            case ITEM, AE_ITEM -> moveItems(sourceLevel, source.anchor().pos(),
                    (LongItemHandler) from, (LongItemHandler) to, limit, sourceFilter, targetFilter);
            case FLUID, AE_FLUID -> moveFluid((LongFluidHandler) from, (LongFluidHandler) to, limit,
                    sourceFilter, targetFilter);
            case ENERGY -> moveEnergy((LongEnergyHandler) from, (LongEnergyHandler) to, limit);
            case CHEMICAL, AE_CHEMICAL -> moveChemical((ChemicalHandlerView) from,
                    (ChemicalHandlerView) to, limit, sourceFilter, targetFilter);
            // 魔源那边的接口是 int，超出的部分只能夹掉——魔源本身也到不了那么大的量。
            case SOURCE, AE_SOURCE -> moveSource((SourceHandlerView) from, (SourceHandlerView) to, limit);
        };
    }

    private static long moveItems(ServerLevel sourceLevel, BlockPos sourcePos,
                                  LongItemHandler from, LongItemHandler to, long limit,
                                  List<ItemStack> sourceFilter, List<ItemStack> targetFilter) {
        long moved = 0L;
        // 扫描预算：源端的「槽位数」对它自己可能是廉价的（普通箱子就几十个），但对 AE 端点
        // 而言 getSlots() 是整个 ME 网络的资源种类数——成熟网络上千种很常见，而每一次
        // 命中都要走一遍网络做模拟插入。不设上限就等于每 tick 全网络遍历一遍，主线程会被
        // 拖垮。这里截断只影响单 tick 的进度：没轮到的条目下一 tick 自然顺延。
        int budget = Math.min(from.getSlots(), MAX_SCAN_SLOTS);
        for (int slot = 0; slot < budget && moved < limit; slot++) {
            ItemStack template = from.getStackInSlot(slot);
            if (template.isEmpty()) {
                continue;
            }
            long available = from.amountIn(slot);
            if (available <= 0L || !matches(sourceFilter, template) || !matches(targetFilter, template)) {
                continue;
            }

            long want = Math.min(limit - moved, available);
            // 模拟：目标最多能接多少（long，跨槽累加精确）。
            long accepted = to.insert(template, want, true);
            if (accepted <= 0L) {
                continue;
            }

            long extracted = from.extract(slot, accepted, false);
            if (extracted <= 0L) {
                continue;
            }
            long leftover = extracted - to.insert(template, extracted, false);
            long actuallyMoved = extracted;
            if (leftover > 0L) {
                long unreturned = leftover - from.insert(template, leftover, false);
                if (unreturned > 0L) {
                    dropItems(sourceLevel, sourcePos, template, unreturned);
                    actuallyMoved -= unreturned;
                }
            }
            moved += Math.max(0L, actuallyMoved);
        }
        return moved;
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
                                  List<ItemStack> sourceFilter, List<ItemStack> targetFilter) {
        int budget = Math.min(from.getTanks(), MAX_SCAN_SLOTS);
        for (int tank = 0; tank < budget; tank++) {
            FluidStack type = from.getFluidInTank(tank);
            if (type.isEmpty()) {
                continue;
            }
            long available = from.amountIn(tank);
            if (available <= 0L || !matchesFluid(sourceFilter, type) || !matchesFluid(targetFilter, type)) {
                continue;
            }

            long drainable = from.drain(tank, Math.min(limit, available), true);
            if (drainable <= 0L) {
                continue;
            }
            long fillable = to.fill(type, drainable, true);
            long target = Math.min(drainable, fillable);
            if (target <= 0L) {
                continue;
            }

            long drained = from.drain(tank, target, false);
            if (drained <= 0L) {
                continue;
            }
            long filled = to.fill(type, drained, false);
            if (filled < drained) {
                // 目标在执行阶段吐回了余量。模拟与执行走的是同一组调用，正常情况下不该发生；
                // 一旦发生就必须把余量退回源，退不回也不能让它凭空消失。
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
        return 0L;
    }

    /**
     * 能量搬运：本模组 {@link IEnergyManager} 走原生直通，一次到位。
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
                                     List<ItemStack> sourceFilter, List<ItemStack> targetFilter) {
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

    private static boolean matches(List<ItemStack> filter, ItemStack stack) {
        if (filter.isEmpty()) {
            return true;
        }
        for (ItemStack candidate : filter) {
            if (ItemStack.isSameItemSameComponents(candidate, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 流体过滤：标记物是「装有该流体的容器」（水桶、流体罐…），比对的是容器里装的流体种类。
     *
     * <p>过滤非空但没有任何标记物装着可识别的流体时返回 {@code false}——即「设了过滤器就一定会
     * 生效」，不会因为标记物认不出来而退化成「什么都搬」。</p>
     */
    private static boolean matchesFluid(List<ItemStack> filter, FluidStack fluid) {
        if (filter.isEmpty()) {
            return true;
        }
        for (ItemStack marker : filter) {
            IFluidHandlerItem handler = marker.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) {
                continue;
            }
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack contained = handler.getFluidInTank(tank);
                if (!contained.isEmpty() && FluidStack.isSameFluidSameComponents(contained, fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 化学品过滤：标记物是「装有该化学品的容器」，比对的是容器里装的化学品种类。 */
    private static boolean matchesChemical(List<ItemStack> filter, ChemicalStackView chemical) {
        if (filter.isEmpty()) {
            return true;
        }
        ChemicalCompatProvider provider = ChemicalCompatProviders.get();
        if (!provider.isAvailable()) {
            return true;
        }
        for (ItemStack marker : filter) {
            ChemicalStackView contained = provider.chemicalInItem(marker);
            if (contained != null && chemical.isSameType(contained)) {
                return true;
            }
        }
        return false;
    }
}
