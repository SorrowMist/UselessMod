package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingWorkerBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactTicker;
import com.sorrowmist.useless.compat.neoecoae.compact.calculator.CompactHost;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactCraftingCluster;
import com.sorrowmist.useless.compat.neoecoae.compact.exposed.ExposedPatternBusBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.provider.CompactPatternProvider;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.CompactInterfaceAttachment;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowCraftingHostBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowCraftingParallelCoreBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowCraftingWorkerBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowNodeLink;
import com.sorrowmist.useless.core.component.ExternalInventoryKind;
import com.sorrowmist.useless.core.component.ExternalInventoryReference;
import com.sorrowmist.useless.world.inventory.ExternalInventoryStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 无用型紧凑 F9 —— 等价于「八联满配 F9」：8 台满长 F9 主机，每台装高能网络交换模块，
 * 每台 11 条物理 FX 执行通道（共 88 条）。
 *
 * <p>本类不做任何合成逻辑。它只做一件事：装配出 8 个 {@link NECraftingCluster}
 * （第 1 个是真实主机自己，另外 7 个只带影子组件）并塞进同一个 ECO
 * {@link NECraftingNetworkCluster}。于是 ECO 自己的
 * {@code CraftingCapabilitySnapshot#isVirtualTopologyEligible()} 自然成立 →
 * 进入虚拟合成模式：无限批量、任务在首个 tick 完成。</p>
 *
 * <p>样板仍然由 ECO 原生的智能样板总线实例解析与派单：所有影子总线聚合成一个
 * {@link CompactPatternProvider} 挂在本机网格节点上，AE2 / ECO 看到的样板提供者
 * 依旧是 ECO 的实现，只是从 176 条总线合并成了一个入口。这 176 条总线对外的「容器身份」
 * 则由一条带真实网格节点的 {@link ExposedPatternBusBlockEntity} 承担，样板访问终端与
 * 通讯接口界面看到的都是它。</p>
 *
 * <p><b>样板数据不放在本机 NBT 里</b>：176 条影子总线的完整持久化数据是 MB 级的，
 * 而方块实体自身的 NBT 既要跟着区块存盘、又会被 {@code getUpdateTag} 整包发给客户端，
 * 装满样板后必然把区块包撑爆。所以本机只留一个 16 字节的 UUID 引用，真正的内容放在
 * {@link ExternalInventoryStore} 里那份 UUID 键控的外置存档中（见 {@link #PATTERN_BUS_KEY}）。</p>
 */
public class CompactF9BlockEntity extends ECOCraftingSystemBlockEntity
        implements CompactHost<NECraftingCluster>, CompactTicker, ICraftingProvider, ECOFastPathDispatchProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompactF9BlockEntity.class);

    /** 与 ECO 自己的虚拟合成判定一致：恰好 8 台。 */
    private static final int HOSTS = 8;
    /**
     * 外置存储载荷里那段样板总线的键名。
     *
     * <p>它在老存档里是方块实体自己的标签名（历史实现把 176 条总线的整份数据直接塞在主机 NBT 里），
     * 现在只作为「迁移来源」被读取一次，不再写回。</p>
     */
    static final String PATTERN_BUS_KEY = "useless_compact_pattern_buses";
    /**
     * 满长建造的列数。ECO 的结构定义里 {@code expandMax = craftingSystemMaxLength - 4}，
     * 而虚拟合成判定要求「实际 FX 通道数 == 该上限」，所以这里必须每次都按当前上限取，
     * 否则玩家改配置后虚拟模式会静默失效。
     */
    private int buildLength() {
        return Math.max(1, getMaxBuildLength());
    }

    private final CompactPatternProvider patternProvider = new CompactPatternProvider();
    private final List<CompactCraftingCluster> fleet = new ArrayList<>();
    private final List<ECOCraftingWorkerBlockEntity> shadowWorkers = new ArrayList<>();
    private final List<ShadowNodeLink> shadowWorkerLinks = new ArrayList<>();
    private final List<ECOCraftingPatternBusBlockEntity> shadowBuses = new ArrayList<>();
    @Nullable
    private NECraftingNetworkCluster compactNetworkCluster;
    private long lastCompactTick = Long.MIN_VALUE;
    private int lastPatternContentRevision = Integer.MIN_VALUE;

    /** 通讯接口界面（影子接口）的全部接线；普通右键仍然走主机自己的面板。 */
    private final CompactInterfaceAttachment<NECraftingCluster> compactInterface =
            new CompactInterfaceAttachment<>(this, NECraftingClusterCalculator::new);

    /** 对外代表 176 条影子总线的聚合总线；它的节点同样靠宿主节点显式接上网格。 */
    @Nullable
    private ExposedPatternBusBlockEntity exposedBus;
    private final ShadowNodeLink exposedBusLink = new ShadowNodeLink(this);
    /**
     * 从老存档 NBT 里读出来的样板数据，只在「装配 → 恢复」之间中转一次。
     * 恢复完就置空，之后一律走外置存储；这份数据不会再被写回方块实体 NBT。
     */
    @Nullable
    private ListTag pendingPatternBusData;
    /** 本机的样板数据落点：UUID 指向外置存档，本身只占 16 字节。 */
    @Nullable
    private ExternalInventoryReference patternStoreReference;
    /**
     * 样板内容自上次写入外置存储后有没有变过。
     *
     * <p>176 条总线的完整数据是 MB 级的，绝不能每次区块存档都重序列化、重写一遍；
     * 只有内容版本真的变了（玩家插样板、终端里改动）才写。见 {@link #compactServerTick}。</p>
     */
    private boolean patternStoreDirty;

    public CompactF9BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, ECOTier.L9);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
        // 关键：网格服务必须挂在「本机自己」身上，而不是一个独立对象。
        // ECO 的样板总线就是这么做的（总线 BE 既实现 ICraftingProvider 又实现 IECOPatternStorage），
        // AE2/ECO 的各种枚举都是「拿节点 → 取服务 → 和宿主比对」，独立对象会让它们认不出来。
        getMainNode()
                .addService(ICraftingProvider.class, this);
    }

    // ------------------------------------------------------------- 成形生命周期
    //
    // ECO 的主机基类在构造函数里把 calculator 硬编码成多方块计算器（字段 final，换不掉），
    // 所以紧凑方块不依赖计算器，而是自己接管成形：onReady 之后建出只含自己的集群，
    // 并掐掉 updateMultiBlock / rebuildMultiblock，否则邻居一变 ECO 的计算器就会拆掉集群。

    @Override
    public void onReady() {
        super.onReady();
        formCompactStructure();
        onGridConnectableSidesChanged();
    }

    @Override
    public void updateMultiBlock(BlockPos changedPos) {
        if (cluster == null || cluster.isDestroyed()) {
            formCompactStructure();
        }
    }

    @Override
    public void rebuildMultiblock() {
        if (cluster == null || cluster.isDestroyed()) {
            formCompactStructure();
        }
    }

    private void formCompactStructure() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (cluster != null && !cluster.isDestroyed() && formed) {
            return;
        }
        CompactCraftingCluster compact = new CompactCraftingCluster(worldPosition, worldPosition);
        compact.addBlockEntity(this);
        installCompactComponents(compact, serverLevel);
        linkAll(compact);
        compact.updateFormed(true);
        compact.updateStatus(true);
    }

    @Override
    public boolean hasHighEnergyNetworkSwitch() {
        return true;
    }

    @Override
    public int getSelectedBuildLength() {
        return getMaxBuildLength();
    }

    @Override
    public void setSelectedBuildLength(int length) {
        // 紧凑主机永远是满长，忽略外部改写。
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : EnumSet.noneOf(Direction.class);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state) {
        super.tick(level, pos, state);
        if (level instanceof ServerLevel serverLevel) {
            compactServerTick(serverLevel);
        }
    }

    /**
     * AE2 网格 ticker 通道（ECO 的合成主机本身就是 {@code IGridTickable}）。
     *
     * <p>先跑 ECO 自己的合成 tick，再驱动我们的影子工作核心；两个 tick 来源靠
     * {@link #lastCompactTick} 去重，保证每条 FX 通道每个游戏 tick 只推进一次。</p>
     */
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        TickRateModulation rate = super.tickingRequest(node, ticksSinceLastCall);
        if (level instanceof ServerLevel serverLevel) {
            compactServerTick(serverLevel);
        }
        return rate;
    }

    /** 两个 tick 来源共用；同一个游戏 tick 只处理一次。 */
    private void compactServerTick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == lastCompactTick) {
            return;
        }
        lastCompactTick = now;
        exposedBusLink.tick();
        if (exposedBus != null) {
            exposedBus.tick();
            int revision = exposedBus.getPatternContentRevision();
            if (revision != lastPatternContentRevision) {
                lastPatternContentRevision = revision;
                // 内容变了才需要回写外置存储；挂起一次写，等区块存盘/卸载时真正落盘。
                patternStoreDirty = true;
                setChanged();
            }
        }
        compactInterface.tick();
        tickShadowWorkers();
    }

    /** 主机节点入网/换网后重新把合并后的样板提供者登记给 AE2（它只在收到刷新通知时才会重读）。 */
    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        refreshPatternProvider();
    }

    /** AE2 的刷新入口不做空判断，节点不在网格上时不能调。 */
    private boolean refreshPatternProvider() {
        IGridNode node = getActionableNode();
        if (node == null || node.getGrid() == null || patternProvider.isEmpty()) {
            return false;
        }
        ICraftingProvider.requestUpdate(getMainNode());
        return true;
    }

    /**
     * 影子工作核心的节点接入宿主网格但不使用自身 ticker；这里手动驱动 ECO 原生的
     * {@code tickingRequest}，执行与回收全部走 ECO 自己的线程实现。
     */
    private void tickShadowWorkers() {
        for (ShadowNodeLink link : shadowWorkerLinks) {
            link.tick();
        }
        for (ECOCraftingWorkerBlockEntity worker : shadowWorkers) {
            worker.tickingRequest(null, 1);
        }
    }

    // ------------------------------------------------------------- 集群装配

    @Override
    public void installCompactComponents(NECraftingCluster cluster, ServerLevel level) {
        disposeFleet();
        resetCollections();
        // 先拿到外置存储的引用，后面的恢复才有地方读。
        ensurePatternStore(level);

        installHostHardware(cluster, level);
        // 集群一定是我们自己创建的 CompactCraftingCluster。
        fleet.add((CompactCraftingCluster) cluster);

        for (int index = 1; index < HOSTS; index++) {
            CompactCraftingCluster shadow = buildShadowHost(level);
            installHostHardware(shadow, level);
            linkAll(shadow);
            shadow.updateFormed(true);
            fleet.add(shadow);
        }

        NECraftingNetworkCluster network = new NECraftingNetworkCluster();
        List<NECraftingCluster> members = new ArrayList<>(fleet);
        network.configure(members);
        for (CompactCraftingCluster member : fleet) {
            // 走注入入口：ECO 的逻辑网络管理器会把它不认识的集群置空，普通 setter 会被我们忽略。
            member.injectCompactNetworkCluster(network);
        }

        this.compactNetworkCluster = network;

        patternProvider.bind(shadowBuses);
        restorePatternBusData(level);
        for (ECOCraftingPatternBusBlockEntity bus : shadowBuses) {
            // 影子总线不会走 onReady，这里用 ECO 自己的刷新入口把样板索引建起来。
            bus.flushScheduledPatternDetails();
        }
        installExposedBus(level);
        // 刚恢复出来的内容不算「变化」：把版本基准对齐到现在，避免存档一读进来就先白写一遍。
        // （是否真的需要写外置存储由 restorePatternBusData 判断，它认得「迁移」和「本来就在存储里」的区别。）
        if (exposedBus != null) {
            lastPatternContentRevision = exposedBus.getPatternContentRevision();
        }
        ICraftingProvider.requestUpdate(getMainNode());
        compactInterface.attach(level, cluster, getMainNode());

        LOGGER.debug("Compact F9 at {} assembled {} hosts / {} FX lanes / {} pattern buses",
                worldPosition, fleet.size(), shadowWorkers.size(), shadowBuses.size());
    }

    private CompactCraftingCluster buildShadowHost(ServerLevel level) {
        CompactCraftingCluster shadow = new CompactCraftingCluster(worldPosition, worldPosition);
        ShadowCraftingHostBlockEntity host = new ShadowCraftingHostBlockEntity(
                NeoEcoCompactRegistry.SHADOW_CRAFTING_HOST.get(),
                worldPosition,
                NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_F9));
        host.setLevel(level);
        shadow.addBlockEntity(host);
        return shadow;
    }

    private void installHostHardware(NECraftingCluster cluster, ServerLevel level) {
        for (int index = 0; index < buildLength(); index++) {
            ShadowCraftingWorkerBlockEntity worker = new ShadowCraftingWorkerBlockEntity(
                    NeoEcoCompactRegistry.SHADOW_CRAFTING_WORKER.get(),
                    worldPosition,
                    NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_F9));
            worker.setLevel(level);
            cluster.addBlockEntity(worker);
            shadowWorkers.add(worker);
            ShadowNodeLink workerLink = new ShadowNodeLink(this);
            workerLink.attach(worker.getMainNode(), level, getMainNode());
            shadowWorkerLinks.add(workerLink);
        }

        for (int index = 0; index < buildLength() * 2; index++) {
            ShadowCraftingParallelCoreBlockEntity core = new ShadowCraftingParallelCoreBlockEntity(
                    NeoEcoCompactRegistry.SHADOW_CRAFTING_PARALLEL_CORE.get(),
                    worldPosition,
                    NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_F9));
            core.setLevel(level);
            cluster.addBlockEntity(core);
        }

        for (int index = 0; index < buildLength() * 2; index++) {
            ShadowPatternBusBlockEntity bus = new ShadowPatternBusBlockEntity(
                    NeoEcoCompactRegistry.SHADOW_PATTERN_BUS.get(),
                    worldPosition,
                    NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_F9));
            bus.setLevel(level);
            cluster.addBlockEntity(bus);
            shadowBuses.add(bus);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void linkAll(NECraftingCluster cluster) {
        cluster.getBlockEntities().forEachRemaining(blockEntity -> {
            if (blockEntity instanceof NEBlockEntity neBlockEntity) {
                neBlockEntity.updateCluster(cluster);
            }
        });
    }

    /** 建出对外代表 176 条影子总线的聚合总线，并把它接上宿主节点。 */
    private void installExposedBus(ServerLevel level) {
        ExposedPatternBusBlockEntity bus = new ExposedPatternBusBlockEntity(
                NeoEcoCompactRegistry.COMPACT_EXPOSED_PATTERN_BUS.get(),
                worldPosition,
                NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_F9));
        bus.setLevel(level);
        bus.bind(shadowBuses, getMainNode());
        exposedBus = bus;
        exposedBusLink.attach(bus.getMainNode(), level, getMainNode());
    }

    /**
     * 把样板数据装回 176 条影子总线。
     *
     * <p>数据来源两选一：老存档里那份直接写在主机 NBT 里的 {@code useless_compact_pattern_buses}
     * （只在迁移时命中一次），否则读外置存储。</p>
     */
    private void restorePatternBusData(ServerLevel level) {
        ListTag saved = pendingPatternBusData;
        boolean fromLegacy = saved != null;
        if (saved == null) {
            saved = storedPatternBusData(level);
        }
        if (saved == null) {
            return;
        }
        int count = Math.min(saved.size(), shadowBuses.size());
        for (int index = 0; index < count; index++) {
            ECOCraftingPatternBusBlockEntity bus = shadowBuses.get(index);
            bus.getRootStorage().requireInit();
            bus.loadManagedPersistentData(level.registryAccess(), saved.getCompound(index));
            for (int slot = 0; slot < bus.getPatternSlotCount(); slot++) {
                ItemStack stack = bus.getTerminalPatternInventory().getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    bus.setPatternDirect(slot, ItemStack.EMPTY);
                    bus.setPatternDirect(slot, stack.copy());
                }
            }
        }
        pendingPatternBusData = null;
        // 从老 NBT 迁移来的内容还只活在内存里，必须写一次外置存储；本就读自外置存储的则不用写。
        patternStoreDirty = fromLegacy;
    }

    /** 从外置存储里取出上次写进去的样板数据；没有（新机器 / 从没写过）就返回 null。 */
    @Nullable
    private ListTag storedPatternBusData(ServerLevel level) {
        CompoundTag payload = ExternalInventoryStore.loadRawPayload(level, patternStoreReference);
        if (payload == null || !payload.contains(PATTERN_BUS_KEY, Tag.TAG_LIST)) {
            return null;
        }
        return payload.getList(PATTERN_BUS_KEY, Tag.TAG_COMPOUND);
    }

    /** 把 176 条影子总线当前的全部持久化数据序列化成一份 NBT 列表。 */
    private ListTag capturePatternBusData(HolderLookup.Provider registries) {
        ListTag saved = new ListTag();
        for (ECOCraftingPatternBusBlockEntity bus : shadowBuses) {
            bus.getRootStorage().requireInit();
            CompoundTag busData = new CompoundTag();
            bus.saveManagedPersistentData(registries, busData, false);
            saved.add(busData);
        }
        return saved;
    }

    /**
     * 把样板数据写进外置存储。
     *
     * <p>{@code force} 为 false 时只在内容真的变过之后才写一次——序列化 176 条总线是 MB 级的开销，
     * 而区块存盘非常频繁。拆除 / 卸载 / 重新成形这类「总线马上就要被销毁」的场合才用 true 兜底。</p>
     */
    private void flushPatternStore(boolean force) {
        if (patternStoreReference == null || shadowBuses.isEmpty()) {
            return;
        }
        if (!force && !patternStoreDirty) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CompoundTag payload = new CompoundTag();
        payload.put(PATTERN_BUS_KEY, capturePatternBusData(serverLevel.registryAccess()));
        ExternalInventoryStore.saveRawPayload(serverLevel, patternStoreReference, payload);
        patternStoreDirty = false;
    }

    /** 拿到外置存储引用：优先沿用老存档 / 掉落物带过来的 UUID，没有才新建一份。 */
    private void ensurePatternStore(ServerLevel level) {
        if (patternStoreReference != null) {
            return;
        }
        patternStoreReference = ExternalInventoryStore.bindRawAt(
                level, ExternalInventoryKind.COMPACT_PATTERN_BUS,
                ExternalInventoryStore.getReference(this), worldPosition);
        ExternalInventoryStore.setReference(this, patternStoreReference);
        // 新认领的存储还没被写过，标记成脏，第一次落盘时把当前（含迁移来的）内容写进去。
        patternStoreDirty = true;
    }

    /**
     * 让本机认领掉落物带过来的那份样板数据。
     *
     * <p>方块被拆时 UUID 会跟着掉落物走，放在新位置时把同一份数据接回来；
     * 如果那份数据已经被别的坐标占着（例如创造模式复制了一台），
     * {@code bindRawAt} 会分叉出一份拷贝，两台机器互不影响。</p>
     */
    public void adoptPatternStore(@Nullable ExternalInventoryReference requested) {
        if (patternStoreReference != null || requested == null
                || requested.kind() != ExternalInventoryKind.COMPACT_PATTERN_BUS) {
            return;
        }
        if (level == null || level.isClientSide() || level.getServer() == null) {
            return;
        }
        patternStoreReference = ExternalInventoryStore.bindRawAt(
                level, ExternalInventoryKind.COMPACT_PATTERN_BUS, requested, worldPosition);
        ExternalInventoryStore.setReference(this, patternStoreReference);
        patternStoreDirty = true;
    }

    /** 供方块掉落物携带：带上它，新位置就能把同一份外置样板数据接回来。 */
    @Nullable
    public ExternalInventoryReference patternStoreReference() {
        return patternStoreReference;
    }

    /**
     * 方块被拆掉时调用：先把最后一次改动写进外置存储，再解除坐标占用。
     *
     * <p>数据本身留在文件里不动，等掉落物把它带到新位置；没人认领就当孤儿文件留着，
     * 至少比直接烧掉玩家攒的样板好。</p>
     */
    public void releasePatternStore() {
        flushPatternStore(true);
        if (level != null && patternStoreReference != null) {
            ExternalInventoryStore.release(level, worldPosition, patternStoreReference);
        }
    }

    private void disposeFleet() {
        if (level instanceof ServerLevel) {
            flushPatternStore(true);
        }
        compactInterface.release();
        if (exposedBus != null) {
            exposedBus.onChunkUnloaded();
        }
        exposedBusLink.detach();
        for (ShadowNodeLink workerLink : shadowWorkerLinks) {
            workerLink.detach();
        }
        exposedBus = null;
        for (CompactCraftingCluster member : fleet) {
            if (member != cluster && !member.isDestroyed()) {
                member.injectCompactNetworkCluster(null);
                member.destroy();
            }
        }
    }

    private void resetCollections() {
        fleet.clear();
        shadowWorkers.clear();
        shadowWorkerLinks.clear();
        shadowBuses.clear();
        patternProvider.bind(List.of());
        lastPatternContentRevision = Integer.MIN_VALUE;
    }

    @Override
    public void onChunkUnloaded() {
        disposeFleet();
        resetCollections();
        compactNetworkCluster = null;
        compactInterface.dispose();
        super.onChunkUnloaded();
    }

    /** 供调试/UI 读取当前装配出来的逻辑网络。 */
    @Nullable
    public NECraftingNetworkCluster compactNetworkCluster() {
        return compactNetworkCluster;
    }

    // ------------------------------------------------ 网格服务：样板提供者 / 样板存储

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return patternProvider.getAvailablePatterns();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return patternProvider.pushPattern(patternDetails, inputHolder);
    }

    @Override
    public boolean isBusy() {
        return patternProvider.isBusy();
    }

    @Override
    public Preparation eco$prepareFastPath(ECOBatchDispatchContext context) {
        return patternProvider.eco$prepareFastPath(context);
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        return patternProvider.getEmitableItems();
    }

    /**
     * 玩家手持编码样板右键时，把样板交给 ECO 的样板总线实现去解析与登记。
     *
     * <p>样板内容、去重、容量判断全部由 ECO 的 {@code ECOCraftingPatternBusBlockEntity} 处理，
     * 这里只是把「单方块没有总线界面」这件事补上一个物品入口，并在成功后刷新网格上的样板缓存。</p>
     */
    public boolean insertPatternFromPlayer(ItemStack stack) {
        if (stack.isEmpty() || patternProvider.isEmpty()) {
            return false;
        }
        if (!patternProvider.insertPattern(stack)) {
            return false;
        }
        // 手插的这一下可能赶在本 tick 的版本轮询之前就被拆除/卸载，直接标脏兜底。
        patternStoreDirty = true;
        refreshPatternProvider();
        return true;
    }

    /** 合并后的样板提供者，暴露给外部集成查询样板数量。 */
    public CompactPatternProvider compactPatternProvider() {
        return patternProvider;
    }

    // ------------------------------------------------ 通讯接口界面（影子接口）入口

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        ModularUI interfaceUI = compactInterface.createUI(holder);
        return interfaceUI == null ? super.createUI(holder) : interfaceUI;
    }

    @Override
    public void handleRPCPacket(RPCSender sender, byte[] data) {
        if (!compactInterface.forwardRpc(sender, data)) {
            super.handleRPCPacket(sender, data);
        }
    }

    @Override
    public void handleSyncPacket(RegistryAccess access, BitSet changed, byte[] data, CompoundTag extra) {
        if (!compactInterface.forwardSync(access, changed, data, extra)) {
            super.handleSyncPacket(access, changed, data, extra);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        // 样板内容不再进方块实体自己的 NBT——那份 NBT 会跟着区块存盘，也会被 getUpdateTag
        // 整包同步给客户端。这里只在内容真的变过之后，把数据写进 UUID 键控的外置存储。
        flushPatternStore(false);
        compactInterface.save(registries, data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        // 老存档：整份样板数据曾经直接写在这个标签里，读出来交给装配流程搬进外置存储。
        // 迁移之后本机 NBT 里不会再出现这个键（saveAdditional 不再写它）。
        if (data.contains(PATTERN_BUS_KEY, Tag.TAG_LIST)) {
            pendingPatternBusData = data.getList(PATTERN_BUS_KEY, Tag.TAG_COMPOUND).copy();
            patternStoreDirty = true;
        }
        compactInterface.load(registries, data);
    }

    @Override
    public void setRemoved() {
        // 兜底：正常拆除走方块的 onRemove → releasePatternStore()，那时已经强制落盘过；
        // 这里只补「没走那条路就被摘掉」的情形，所以按脏标记判断，不做无谓的 MB 级重序列化。
        flushPatternStore(false);
        compactInterface.dispose();
        super.setRemoved();
    }
}
