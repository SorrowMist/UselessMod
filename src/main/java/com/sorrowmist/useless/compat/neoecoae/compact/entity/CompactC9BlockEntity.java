package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactTicker;
import com.sorrowmist.useless.compat.neoecoae.compact.calculator.CompactHost;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactComputationCluster;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.CompactInterfaceAttachment;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationDriveBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationHostBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowComputationParallelCoreBlockEntity;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowThreadingCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
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
 * 无用型紧凑 C9 —— 等价于「八联满配 C9」：8 台满长 C9 主机，每台装高能网络交换模块、
 * 上下晶阵驱动器全部装满 CE9 闪存晶阵。
 *
 * <p>本类不做任何合成逻辑。它只做一件事：把 8 个 {@link NEComputationCluster}（第 1 个是真实主机
 * 自己，另外 7 个是只有影子组件的「影子主机」）注册进同一个 ECO
 * {@link NEComputationNetworkCluster}。于是 ECO 自己的
 * {@code NEComputationNetworkCluster#isEndgameEligible()} 会自然判定为极限模式，
 * 线程 / 并行 / CPU 存储三项全部按 ECO 的规则拿到 INT32 / INT64 上限。</p>
 */
public class CompactC9BlockEntity extends ECOComputationSystemBlockEntity
        implements CompactHost<NEComputationCluster>, CompactTicker, IGridTickable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompactC9BlockEntity.class);

    /** 与 ECO 自己的极限模式判定完全一致：恰好 8 台。 */
    private static final int HOSTS = 8;
    /**
     * 满长建造的列数。ECO 的结构定义里 {@code expandMax = computationSystemMaxLength - 4}，
     * 改成配置值也不会让紧凑方块退化，因为这里每次都按当前上限重新装配。
     */
    private int buildLength() {
        return Math.max(1, getMaxBuildLength());
    }

    private final List<CompactComputationCluster> fleet = new ArrayList<>();
    @Nullable
    private NEComputationNetworkCluster compactNetworkCluster;
    private boolean pendingCpuAnnounce;
    private int cpuAnnounceAttempts;
    private long lastCompactTick = Long.MIN_VALUE;

    /** 通讯接口界面（影子接口）的全部接线；普通右键仍然走主机自己的面板。 */
    private final CompactInterfaceAttachment<NEComputationCluster> compactInterface =
            new CompactInterfaceAttachment<>(this, NEComputationClusterCalculator::new);

    public CompactC9BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, ECOTier.L9);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
        // 双保险：世界 ticker 与 AE2 网格 ticker 都会驱动本机逻辑（同一 tick 去重）。
        getMainNode().addService(IGridTickable.class, this);
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
        CompactComputationCluster compact = new CompactComputationCluster(worldPosition, worldPosition);
        compact.addBlockEntity(this);
        installCompactComponents(compact, serverLevel);
        linkAll(compact);
        compact.updateFormed(true);
        compact.updateStatus(true);
    }

    @Override
    public boolean hasNormalNetworkSwitch() {
        return false;
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

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 10, false);
    }

    /** AE2 网格 ticker 通道：世界 ticker 万一没跑，CPU 登记在这里兜底。 */
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level instanceof ServerLevel serverLevel) {
            compactServerTick(serverLevel);
        }
        return TickRateModulation.IDLE;
    }

    /** 两个 tick 来源共用；同一个游戏 tick 只处理一次。 */
    private void compactServerTick(ServerLevel level) {
        long now = level.getGameTime();
        if (now == lastCompactTick) {
            return;
        }
        lastCompactTick = now;
        compactInterface.tick();
        if (!pendingCpuAnnounce) {
            return;
        }
        // 成型时节点可能尚未挂载到网格（AE2 的节点入网需等待网格 tick），
        // 因此单次失败不可放弃，需重试至入网完成。
        if (cpuAnnounceAttempts++ > 100) {
            pendingCpuAnnounce = false;
            return;
        }
        pendingCpuAnnounce = !announceCpuToGrid();
    }

    /**
     * 主机节点入网/换网后重新向 CraftingService 登记 CPU。
     *
     * <p>AE2 只在收到 {@code GridCraftingCpuChange} 时才会刷新它的 CPU 集群集合
     * （{@code CraftingService.updateList}），而 ECO 是靠在那个刷新点里
     * {@code grid.getMachines(ECOComputationSystemBlockEntity.class)} 找到主机的。</p>
     */
    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        pendingCpuAnnounce = true;
        cpuAnnounceAttempts = 0;
    }

    // ------------------------------------------------------------- 集群装配

    @Override
    public void installCompactComponents(NEComputationCluster cluster, ServerLevel level) {
        disposeFleet();
        fleet.clear();

        // 成员 #1：真实主机自己（它的影子组件补满这台主机的硬件）。
        installHostHardware(cluster, level);
        // 集群一定是我们自己创建的 CompactComputationCluster（成形逻辑在 CompactL9/C9 的 formCompactStructure 里）。
        fleet.add((CompactComputationCluster) cluster);

        // 成员 #2..#8：影子主机。
        for (int index = 1; index < HOSTS; index++) {
            CompactComputationCluster shadow = buildShadowHost(level);
            installHostHardware(shadow, level);
            linkAll(shadow);
            shadow.updateFormed(true);
            fleet.add(shadow);
        }

        NEComputationNetworkCluster network = new NEComputationNetworkCluster();
        List<NEComputationCluster> members = new ArrayList<>(fleet);
        network.configure(members);
        for (CompactComputationCluster member : fleet) {
            // 走注入入口：ECO 的逻辑网络管理器会把它不认识的集群置空，普通 setter 会被我们忽略。
            member.injectCompactNetworkCluster(network);
        }

        this.compactNetworkCluster = network;
        this.pendingCpuAnnounce = true;
        compactInterface.attach(level, cluster, getMainNode());
        LOGGER.debug("Compact C9 at {} assembled {} pooled hosts", worldPosition, fleet.size());
    }

    private CompactComputationCluster buildShadowHost(ServerLevel level) {
        CompactComputationCluster shadow = new CompactComputationCluster(worldPosition, worldPosition);
        ShadowComputationHostBlockEntity host = new ShadowComputationHostBlockEntity(
                NeoEcoCompactRegistry.SHADOW_COMPUTATION_HOST.get(),
                worldPosition,
                NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_C9));
        host.setLevel(level);
        shadow.addBlockEntity(host);
        return shadow;
    }

    private void installHostHardware(NEComputationCluster cluster, ServerLevel level) {
        for (int index = 0; index < buildLength(); index++) {
            ShadowThreadingCoreBlockEntity core = new ShadowThreadingCoreBlockEntity(
                    NeoEcoCompactRegistry.SHADOW_THREADING_CORE.get(),
                    worldPosition,
                    NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_C9));
            core.setLevel(level);
            cluster.addBlockEntity(core);
        }

        ItemStack cell = NeoEcoCompactRegistry.neoEcoStack("eco_computation_cell_l9");
        for (int index = 0; index < buildLength() * 2; index++) {
            ShadowComputationDriveBlockEntity drive = new ShadowComputationDriveBlockEntity(
                    NeoEcoCompactRegistry.SHADOW_COMPUTATION_DRIVE.get(),
                    worldPosition,
                    NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_C9));
            drive.setLevel(level);
            drive.setCellStack(cell.copy());
            cluster.addBlockEntity(drive);
        }

        for (int index = 0; index < buildLength() * 2; index++) {
            ShadowComputationParallelCoreBlockEntity core = new ShadowComputationParallelCoreBlockEntity(
                    NeoEcoCompactRegistry.SHADOW_COMPUTATION_PARALLEL_CORE.get(),
                    worldPosition,
                    NeoEcoCompactRegistry.placeholderState(NeoEcoCompactRegistry.COMPACT_C9));
            core.setLevel(level);
            cluster.addBlockEntity(core);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void linkAll(NEComputationCluster cluster) {
        cluster.getBlockEntities().forEachRemaining(blockEntity -> {
            if (blockEntity instanceof NEBlockEntity neBlockEntity) {
                neBlockEntity.updateCluster(cluster);
            }
        });
    }

    private void disposeFleet() {
        compactInterface.release();
        for (CompactComputationCluster member : fleet) {
            if (member != cluster && !member.isDestroyed()) {
                member.injectCompactNetworkCluster(null);
                member.destroy();
            }
        }
    }

    /**
     * ECO 的 {@code CraftingService} 只在 {@code updateCPUClusters} 里扫描主机，
     * 所以成型后主动发一次网格事件，让 CPU 立刻出现在合成终端里。
     */
    private boolean announceCpuToGrid() {
        IGridNode node = getActionableNode();
        if (node == null) {
            return false;
        }
        IGrid grid = node.getGrid();
        if (grid == null) {
            return false;
        }
        grid.postEvent(new GridCraftingCpuChange(node));
        return true;
    }

    @Override
    public void onChunkUnloaded() {
        disposeFleet();
        fleet.clear();
        compactNetworkCluster = null;
        compactInterface.dispose();
        super.onChunkUnloaded();
    }

    /** 供调试/UI 读取当前装配出来的逻辑网络。 */
    @Nullable
    public NEComputationNetworkCluster compactNetworkCluster() {
        return compactNetworkCluster;
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
        compactInterface.save(registries, data);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        compactInterface.load(registries, data);
    }

    @Override
    public void setRemoved() {
        compactInterface.dispose();
        super.setRemoved();
    }
}
