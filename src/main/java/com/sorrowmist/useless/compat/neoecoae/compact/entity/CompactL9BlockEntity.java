package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IStorageProvider;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactTicker;
import com.sorrowmist.useless.compat.neoecoae.compact.calculator.CompactHost;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactStorageCluster;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.CompactInterfaceAttachment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 无用型紧凑 L9 —— 等价于一台「无限存储模式已迁移完成」的 L9 存储子系统。
 *
 * <p>自己不实现任何存储逻辑：容量、编解码、注入抽取全部来自 ECO 的
 * {@code ECOInfiniteStorageEngine} / {@code ECOInfiniteStorage}。</p>
 *
 * <p><b>成形方式</b>：ECO 的主机基类在构造函数里把 {@code calculator} 硬编码成了多方块计算器
 * （字段 final，无法替换），所以这里不依赖计算器，而是自己接管成形生命周期：
 * {@link #onReady()} 之后调用 {@link #formCompactStructure()} 建出只含自己的集群；
 * 同时掐掉 {@code updateMultiBlock} / {@code rebuildMultiBlock}，
 * 否则邻居一变 ECO 的多方块计算器就会判定结构不合法并把集群拆掉。</p>
 */
public class CompactL9BlockEntity extends ECOStorageSystemBlockEntity
        implements CompactHost<NEStorageCluster>, CompactTicker, IGridTickable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompactL9BlockEntity.class);

    /** 与 ECO 控制器掉落物里使用的同一个 NBT 键。 */
    private static final String DOMAIN_TAG = "neoecoae_infinite_controller_domain";
    private static final String MODE_TAG = "neoecoae_infinite_controller_mode";
    /** ECO 的 INFINITE_COMPONENT_REQUIRED。 */
    private static final int INFINITE_COMPONENTS = 64;
    /** 自愈检查周期（tick）。 */
    private static final int MAINTAIN_INTERVAL = 20;
    /** 等待网格就绪的最大重试次数。 */
    private static final int ANNOUNCE_MAX_ATTEMPTS = 100;

    private boolean pendingStorageAnnounce = true;
    private int storageAnnounceAttempts;
    private int maintainTicker;
    private long lastCompactTick = Long.MIN_VALUE;

    /** 通讯接口界面（影子接口）的全部接线；普通右键仍然走主机自己的面板。 */
    private final CompactInterfaceAttachment<NEStorageCluster> compactInterface =
            new CompactInterfaceAttachment<>(this, NEStorageClusterCalculator::new);


    public CompactL9BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, ECOTier.L9);
        // 双保险：世界 ticker 与 AE2 网格 ticker 都会驱动本机逻辑（同一 tick 去重）。
        getMainNode().addService(IGridTickable.class, this);
    }

    // ------------------------------------------------------------- 成形生命周期

    @Override
    public void onReady() {
        super.onReady();
        formCompactStructure();
        onGridConnectableSidesChanged();
    }

    @Override
    public void updateMultiBlock(BlockPos changedPos) {
        // 紧凑方块的结构与邻居无关：ECO 的多方块计算器在这里只会误判并拆掉集群。
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
        CompactStorageCluster compact = new CompactStorageCluster(worldPosition, worldPosition);
        compact.addBlockEntity(this);
        installCompactComponents(compact, serverLevel);
        linkAll(compact);

        compact.updateFormed(true);
        compact.updateStatus(true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void linkAll(NEStorageCluster cluster) {
        cluster.getBlockEntities().forEachRemaining(blockEntity -> {
            if (blockEntity instanceof NEBlockEntity neBlockEntity) {
                neBlockEntity.updateCluster(cluster);
            }
        });
    }

    // ------------------------------------------------------------------ tick

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

    /** AE2 网格 ticker 通道：世界 ticker 万一没跑，存储重挂与自愈在这里兜底。 */
    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (level instanceof ServerLevel serverLevel) {
            compactServerTick(serverLevel);
        }
        return TickRateModulation.IDLE;
    }

    /** 两个 tick 来源共用；同一个游戏 tick 只处理一次。 */
    private void compactServerTick(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        if (now == lastCompactTick) {
            return;
        }
        lastCompactTick = now;
        compactInterface.tick();
        // 主机自己的 IStorageProvider 是在节点入网那一刻被 AE2 读取的，那时 formed 还是 false，
        // 无限存储会被跳过；所以入网后必须补一次「重挂存储」。
        if (pendingStorageAnnounce && storageAnnounceAttempts++ < ANNOUNCE_MAX_ATTEMPTS) {
            pendingStorageAnnounce = !requestStorageUpdate();
        }
        if (++maintainTicker >= MAINTAIN_INTERVAL) {
            maintainTicker = 0;
            maintainInfiniteStorage(serverLevel);
        }
    }

    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        // 换了网格就要重新挂存储，并重新确认无限模式。
        pendingStorageAnnounce = true;
        storageAnnounceAttempts = 0;
        maintainInfiniteStorage(getLevel() instanceof ServerLevel serverLevel ? serverLevel : null);
    }

    private boolean requestStorageUpdate() {
        IGridNode node = getActionableNode();
        if (node == null || node.getGrid() == null) {
            // requestUpdate 内部不做空判断，网格还没就绪时必须等。
            return false;
        }
        IStorageProvider.requestUpdate(getMainNode());
        return true;
    }

    /**
     * 自愈：只要方块里还留着内建的 64 个无限组件（取出通道已被 {@link #canExtractInfiniteComponents()} 封掉），
     * 这台方块就<b>定义上</b>是无限存储主机；ECO 的状态机若被外部因素推离
     * {@code formed_infinite}，这里把它拉回来。
     */
    private void maintainInfiniteStorage(ServerLevel level) {
        if (level == null || isFormedInfiniteMode()) {
            return;
        }
        primeInfiniteComponents();
        restoreOrCreateInfiniteDomain(level);
        requestStorageUpdate();
    }

    /**
     * 内建的 64 个无限组件不允许被玩家取出。
     *
     * <p>否则「取出 64 个 → 下一 tick 自动补满」就成了无限复制；
     * 而且紧凑 L9 的定位就是「永远处于无限存储」，本来也不该能退回普通模式。</p>
     */


    @Override
    public boolean canExtractInfiniteComponents() {
        return false;
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return formed ? EnumSet.allOf(Direction.class) : EnumSet.noneOf(Direction.class);
    }

    @Override
    public int getSelectedBuildLength() {
        return getMaxBuildLength();
    }

    @Override
    public void setSelectedBuildLength(int length) {
        // 紧凑方块没有「建造长度」概念，固定为结构上限。
    }

    /**
     * 内建的 64 个无限组件属于方块本体，不能作为掉落物被复制出去。
     *
     * <p>域本身通过 ECO 原生的 {@code applyInfiniteDomainToControllerDrop} 写进方块物品，
     * 放回来时由 {@code ECOStorageSystemBlock#setPlacedBy} 恢复，所以这里不产生额外掉落是安全的。</p>
     */
    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
    }

    // ------------------------------------------------------------- 集群装配

    @Override
    public void installCompactComponents(NEStorageCluster cluster, ServerLevel level) {
        compactInterface.release();
        primeInfiniteComponents();
        restoreOrCreateInfiniteDomain(level);
        compactInterface.attach(level, cluster, getMainNode());
    }

    /**
     * 把 64 个无限存储组件放进主机自己那唯一的组件槽。
     *
     * <p>该槽位在 ECO 里是私有的，这里通过反射写入，语义与玩家在主机界面里手动放入 64 个组件一致；
     * 写入走 {@code setItemDirect}，它会回调 {@code onChangeInventory} 把 {@code infiniteComponentsDirty} 置位。</p>
     */
    private void primeInfiniteComponents() {
        ItemStack current = readComponentSlot();
        if (current != null && !current.isEmpty() && current.getCount() >= INFINITE_COMPONENTS) {
            return;
        }
        ItemStack stack = NeoEcoCompactRegistry.neoEcoStack("eco_infinite_cell_component");
        stack.setCount(INFINITE_COMPONENTS);
        if (!writeComponentSlot(stack)) {
            LOGGER.warn("Compact L9 at {} could not preload ECO infinite components", worldPosition);
        }
    }

    private ItemStack readComponentSlot() {
        AppEngInternalInventory inventory = componentInventory();
        return inventory == null ? null : inventory.getStackInSlot(0);
    }

    private boolean writeComponentSlot(ItemStack stack) {
        AppEngInternalInventory inventory = componentInventory();
        if (inventory == null) {
            return false;
        }
        inventory.setItemDirect(0, stack);
        return true;
    }

    private AppEngInternalInventory componentInventory() {
        try {
            Field field = ECOStorageSystemBlockEntity.class.getDeclaredField("infiniteComponentInventory");
            field.setAccessible(true);
            Object value = field.get(this);
            if (value instanceof AppEngInternalInventory inventory) {
                return inventory;
            }
        } catch (ReflectiveOperationException ignored) {
            // 落到下面返回 null，由调用方给出警告。
        }
        return null;
    }

    /**
     * 若方块掉落物里已经带着一个无限域（ECO 的 {@code applyInfiniteDomainToControllerDrop} 写的），就沿用它；
     * 否则用「维度 + 坐标」推导出一个稳定 UUID 作为新域。
     */
    private void restoreOrCreateInfiniteDomain(ServerLevel level) {
        ItemStack carrier = new ItemStack(NeoEcoCompactRegistry.COMPACT_L9_ITEM.get());
        applyInfiniteDomainToControllerDrop(carrier);
        CompoundTag tag = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(DOMAIN_TAG)) {
            tag.putUUID(DOMAIN_TAG, stableDomainId(level));
        }
        tag.putString(MODE_TAG, ECOStorageHostMode.FORMED_INFINITE.id());
        carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        restoreInfiniteDomainFromItem(carrier);
    }

    private UUID stableDomainId(ServerLevel level) {
        String seed = "useless_mod:compact_l9:" + level.dimension().location() + ':' + worldPosition.asLong();
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
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
    public void onChunkUnloaded() {
        compactInterface.dispose();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        compactInterface.dispose();
        super.setRemoved();
    }
}
