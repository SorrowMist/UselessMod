package com.sorrowmist.useless.compat.neoecoae.compact.entity;

import appeng.api.networking.IGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IStorageProvider;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOConfigurableCellDrive;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.storage.infinite.ECOInfiniteStorageMember;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry;
import com.sorrowmist.useless.compat.neoecoae.compact.block.CompactTicker;
import com.sorrowmist.useless.compat.neoecoae.compact.calculator.CompactHost;
import com.sorrowmist.useless.compat.neoecoae.compact.cluster.CompactStorageCluster;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.CompactInterfaceAttachment;
import com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowStorageDriveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.RangedWrapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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
 * 同时停用 {@code updateMultiBlock} / {@code rebuildMultiBlock}，
 * 否则邻居变化时 ECO 的多方块计算器会判定结构不合法并拆除集群。</p>
 */
public class CompactL9BlockEntity extends ECOStorageSystemBlockEntity
        implements CompactHost<NEStorageCluster>, CompactTicker, IGridTickable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompactL9BlockEntity.class);

    /** 与 ECO 控制器掉落物里使用的同一个 NBT 键。 */
    private static final String DOMAIN_TAG = "neoecoae_infinite_controller_domain";
    private static final String MODE_TAG = "neoecoae_infinite_controller_mode";
    private static final int DRIVE_COLUMNS = 11;
    private static final int DRIVE_ROWS = 3;
    /** ECO 的 INFINITE_COMPONENT_REQUIRED。 */
    private static final int INFINITE_COMPONENTS = 64;
    /** 等待网格就绪的最大重试次数。 */
    private static final int ANNOUNCE_MAX_ATTEMPTS = 100;

    private boolean pendingStorageAnnounce = true;
    private int storageAnnounceAttempts;
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
        if (!(level instanceof ServerLevel serverLevel) || !getMainNode().isReady()) {
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

    /** AE2 网格 ticker 通道负责补充驱动影子接口和存储挂载。 */
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
    }

    @Override
    protected void onMainNodeGridChanged() {
        super.onMainNodeGridChanged();
        pendingStorageAnnounce = true;
        storageAnnounceAttempts = 0;
    }

    private boolean requestStorageUpdate() {
        IGridNode node = getActionableNode();
        if (node == null || node.getGrid() == null) {
            // requestUpdate 内部不做空值判断，网格未就绪时必须等待。
            return false;
        }
        IStorageProvider.requestUpdate(getMainNode());
        return true;
    }

    /**
     * 内建的 64 个无限组件不允许被玩家取出。
     *
     * <p>否则「取出 64 个 → 下一 tick 自动补满」将构成无限复制；
     * 且紧凑 L9 的设计定位是「始终处于无限存储」，不应支持退回普通模式。</p>
     */


    @Override
    public boolean canExtractInfiniteComponents() {
        return false;
    }

    public static IItemHandlerModifiable lockBuiltInComponents(IItemHandlerModifiable handler) {
        return new RangedWrapper(handler, 0, handler.getSlots()) {
            @Override
            public void setStackInSlot(int slot, ItemStack stack) {
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
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

    @Override
    public List<ECODriveBlockEntity> getStorageUiDrives() {
        return formed && cluster != null ? List.copyOf(cluster.getDrives()) : List.of();
    }

    @Override
    public List<? extends IECOConfigurableCellDrive> getConfigurableCellDrives() {
        return List.of();
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
        installBuiltInDrives(cluster, initializeInfiniteDomain());
        compactInterface.attach(level, cluster, getMainNode());
    }

    private void installBuiltInDrives(NEStorageCluster cluster, UUID domain) {
        var block = (ECODriveBlock) BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("neoecoae", "eco_drive"));
        ItemStack cell = NeoEcoCompactRegistry.neoEcoStack("eco_item_storage_cell_256m");
        for (int row = 0; row < DRIVE_ROWS; row++) {
            for (int column = 0; column < DRIVE_COLUMNS; column++) {
                ItemStack member = cell.copy();
                ECOInfiniteStorageMember.markMember(member, domain);
                cluster.addBlockEntity(new ShadowStorageDriveBlockEntity(block.getBlockEntityType(),
                        worldPosition.offset(column + 2, 1 - row, 0), block.defaultBlockState(), member));
            }
        }
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

    /** 只给新主机分配域。已有域由 ECO 按原状态恢复，不能重置未完成的认领。 */
    private UUID initializeInfiniteDomain() {
        ItemStack carrier = new ItemStack(NeoEcoCompactRegistry.COMPACT_L9_ITEM.get());
        applyInfiniteDomainToControllerDrop(carrier);
        CompoundTag tag = carrier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.hasUUID(DOMAIN_TAG)) return tag.getUUID(DOMAIN_TAG);
        UUID domain = UUID.randomUUID();
        tag.putUUID(DOMAIN_TAG, domain);
        tag.putString(MODE_TAG, "formed_infinite");
        carrier.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        restoreInfiniteDomainFromItem(carrier);
        return domain;
    }

    public static @Nullable UUID readDroppedDomain(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(DOMAIN_TAG) ? tag.getUUID(DOMAIN_TAG) : null;
    }

    // ------------------------------------------------ 通讯接口界面（影子接口）入口

    @Override
    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        ModularUI interfaceUI = compactInterface.createUI(holder);
        // 普通右键沿用 ECO L9 主机面板；只有潜行右键才切到影子通讯接口页面。
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
