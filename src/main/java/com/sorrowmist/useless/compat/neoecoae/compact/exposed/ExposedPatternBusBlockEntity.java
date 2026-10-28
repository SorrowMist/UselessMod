package com.sorrowmist.useless.compat.neoecoae.compact.exposed;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.util.PatternSearchKeywords;
import com.sorrowmist.useless.compat.neoecoae.compact.NeoEcoCompactRegistry;
import com.sorrowmist.useless.compat.neoecoae.compact.provider.CombinedPatternInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 紧凑 F9 对外暴露的「聚合样板总线」：把紧凑机内部全部影子总线的样板合成一条总线。
 *
 * <p>ECO 的样板总线和 AE2 的样板访问终端都只认「挂在网格节点上、且登记在
 * {@code ECOCraftingPatternBusBlockEntity} 名下」的方块实体，而紧凑机里的
 * {@link com.sorrowmist.useless.compat.neoecoae.compact.shadow.ShadowPatternBusBlockEntity}
 * 不落世界也没有节点。所以这里额外实例化一条<b>有真实网格节点</b>的总线，把内部总线的
 * 样板视图整体转出去，让界面看到的是「一台八联满配 F9 的其中一个分组」。</p>
 *
 * <p><b>为什么它不会抢派单</b>：本类覆写两个提供者入口，让
 * {@code isBusy()} 恒为 true、{@code getAvailablePatterns()} 始终返回空列表，
 * 于是 AE2 的样板提供者列表里它始终是个「忙且没样板」的条目；真正派单的依旧是宿主
 * {@code CompactF9BlockEntity} 上的 {@code CompactPatternProvider}。它登记在集群里只为了让
 * 新版 ECO 的通讯接口与样板目录识别本机的样板域。</p>
 */
public class ExposedPatternBusBlockEntity extends ECOCraftingPatternBusBlockEntity {

    private final CombinedPatternInventory mergedPatternInventory = new CombinedPatternInventory();
    private final List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();
    private int[] busRevisions = new int[0];
    private int[] busSlotCounts = new int[0];
    private long[] busAuxiliaryRevisions = new long[0];
    @Nullable
    private InternalInventory combinedTerminalInventory;
    @Nullable
    private IManagedGridNode hostNode;

    public ExposedPatternBusBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
        // 与影子一样：不要通道、不落世界，靠宿主节点显式连接入网。
        getMainNode().setFlags();
        getMainNode().setInWorldNode(false);
    }

    @Override
    @Nullable
    public IGrid getGrid() {
        IGridNode node = getGridNode();
        return node == null ? null : node.getGrid();
    }

    /** 绑定紧凑机内部全部影子总线，并记下宿主节点（样板集变化时要刷新它的提供者缓存）。 */
    public void bind(List<ECOCraftingPatternBusBlockEntity> source, IManagedGridNode hostNode) {
        buses.clear();
        buses.addAll(source);
        mergedPatternInventory.bind(buses);
        busRevisions = new int[buses.size()];
        busSlotCounts = new int[buses.size()];
        busAuxiliaryRevisions = new long[buses.size()];
        for (int index = 0; index < buses.size(); index++) {
            busRevisions[index] = buses.get(index).getPatternContentRevision();
            busSlotCounts[index] = buses.get(index).getPatternSlotCount();
            busAuxiliaryRevisions[index] = buses.get(index).getAuxiliaryRevision();
        }
        combinedTerminalInventory = null;
        this.hostNode = hostNode;
    }

    /** 让域过滤和样板目录认出聚合总线的归属；本类的提供者入口仍保持禁用。 */
    public void joinPatternDomain(NECraftingCluster owner) {
        owner.addBlockEntity(this);
        updateCluster(owner);
    }

    @Override
    public void onReady() {
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
    }

    /**
     * 宿主每 tick 驱动一次：逐条比对内部总线的内容版本。
     *
     * <p>内部总线没有网格节点，父类那条「内容变了就通知通讯接口」的链路在它们身上是静默的，
     * 而写入口不止一处（玩家点击、一键整理、迁移、还有主机自己的右键插样板），
     * 所以这里用轮询兜底，任何一条写入都不会漏掉。</p>
     */
    public void tick() {
        tick((firstSlot, count) -> {});
    }

    /** Reports only changed bus ranges to the native preview, including auxiliary changes. */
    public void tick(BiConsumer<Integer, Integer> changedRange) {
        boolean changed = false;
        int offset = 0;
        for (int index = 0; index < buses.size(); index++) {
            ECOCraftingPatternBusBlockEntity bus = buses.get(index);
            int revision = bus.getPatternContentRevision();
            int slotCount = bus.getPatternSlotCount();
            long auxiliaryRevision = bus.getAuxiliaryRevision();
            boolean resized = busSlotCounts[index] != slotCount;
            int firstSlot = offset;
            offset += slotCount;
            if (busRevisions[index] == revision && !resized
                    && busAuxiliaryRevisions[index] == auxiliaryRevision) {
                continue;
            }
            busRevisions[index] = revision;
            busSlotCounts[index] = slotCount;
            busAuxiliaryRevisions[index] = auxiliaryRevision;
            changedRange.accept(firstSlot, slotCount);
            changed = true;
        }
        if (changed) {
            combinedTerminalInventory = null;
            // 派单由宿主节点上的样板提供者负责，样板集一变就要让它重读合并结果。
            if (hostNode != null && hostNode.getGrid() != null) {
                ICraftingProvider.requestUpdate(hostNode);
            }
        }
    }

    // ---------------------------------------------------------------- 合并视图

    public InternalInventory getPatternSlotInventory() {
        return mergedPatternInventory;
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        if (combinedTerminalInventory == null) {
            combinedTerminalInventory = mergedPatternInventory.createTerminalView();
        }
        return combinedTerminalInventory;
    }

    @Override
    public int getPatternSlotCount() {
        return mergedPatternInventory.size();
    }

    /** 对外代表整台紧凑 F9，分组与主机过去在终端里的那条完全一致（槽位与插拔等价）。 */
    @Override
    public PatternContainerGroup getTerminalGroup() {
        var block = NeoEcoCompactRegistry.COMPACT_F9.get();
        return new PatternContainerGroup(AEItemKey.of(block.asItem()), block.getName(), List.of());
    }

    @Override
    public void setPatternDirect(int slot, ItemStack stack) {
        mergedPatternInventory.setItemDirect(slot, stack);
    }

    @Override
    public void onChangeInventory(InternalInventory inventory, int slot) {
        // The native preview also notifies after changing a live stack. The inherited catalog
        // covers only one bus; aggregate indexes must reach the owning bus's own catalog.
        if (slot < 0) {
            for (ECOCraftingPatternBusBlockEntity bus : buses) {
                bus.onChangeInventory(bus.getPatternSlotInventory(), -1);
            }
            return;
        }
        ECOCraftingPatternBusBlockEntity bus = mergedPatternInventory.busOf(slot);
        if (bus != null) {
            bus.onChangeInventory(bus.getPatternSlotInventory(), mergedPatternInventory.localSlotOf(slot));
        }
    }

    @Override
    public void beginPatternBatch() {
        buses.forEach(ECOCraftingPatternBusBlockEntity::beginPatternBatch);
    }

    @Override
    public void endPatternBatch() {
        buses.forEach(ECOCraftingPatternBusBlockEntity::endPatternBatch);
    }

    // ---------------------------------------------------------------- 样板写入

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        return insertPatternWithResult(itemStack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        return insertIntoBuses(bus -> bus.insertPatternWithResult(itemStack));
    }

    @Override
    public ECOPatternInsertionResult insertPatternKnownUnique(ItemStack itemStack) {
        return insertIntoBuses(bus -> bus.insertPatternKnownUnique(itemStack));
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return insertIntoBuses(bus -> bus.insertPreparedPattern(prepared));
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPatternKnownUnique(ECOPreparedPattern prepared) {
        return insertIntoBuses(bus -> bus.insertPreparedPatternKnownUnique(prepared));
    }

    @Override
    public boolean canAcceptIntoAuxiliary(ItemStack stack) {
        return buses.stream().anyMatch(bus -> bus.canAcceptIntoAuxiliary(stack));
    }

    @Override
    public ECOPatternInsertionResult insertIntoAuxiliary(ItemStack stack, @Nullable ECOPreparedPattern prepared) {
        return insertIntoBuses(bus -> bus.canAcceptIntoAuxiliary(stack)
                ? bus.insertIntoAuxiliary(stack, prepared) : ECOPatternInsertionResult.NO_TARGET);
    }

    /** 紧凑机的样板仍然存在真实总线里，所以这里只是把插入转给第一条腾得出位置的总线。 */
    private ECOPatternInsertionResult insertIntoBuses(
            Function<ECOCraftingPatternBusBlockEntity, ECOPatternInsertionResult> action) {
        ECOPatternInsertionResult last = ECOPatternInsertionResult.NO_TARGET;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ECOPatternInsertionResult result = action.apply(bus);
            if (result == ECOPatternInsertionResult.INSERTED
                    || result == ECOPatternInsertionResult.ALREADY_PRESENT) {
                return result;
            }
            if (result == ECOPatternInsertionResult.NO_SPACE || last != ECOPatternInsertionResult.NO_SPACE) {
                last = result;
            }
        }
        return last;
    }

    // ---------------------------------------------------------------- 目录 / 终端读路径

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of();
    }

    @Override
    public boolean isBusy() {
        // It belongs to the host's pattern domain for management, but dispatch stays on the host.
        return true;
    }

    public void refreshAdvertisedPatterns() {
        if (hostNode != null && hostNode.getGrid() != null) {
            ICraftingProvider.requestUpdate(hostNode);
        }
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) {
        return false;
    }

    @Override
    public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs, @Nullable UUID jobId) {
        return false;
    }

    @Override
    public void refreshPatternDetailsForCatalog() {
        buses.forEach(ECOCraftingPatternBusBlockEntity::refreshPatternDetailsForCatalog);
    }

    @Override
    @Nullable
    public IPatternDetails getDecodedPatternDetails(int slot) {
        ECOCraftingPatternBusBlockEntity bus = mergedPatternInventory.busOf(slot);
        return bus == null ? null : bus.getDecodedPatternDetails(mergedPatternInventory.localSlotOf(slot));
    }

    @Override
    public String getPatternSearchKeywords(int slot) {
        ECOCraftingPatternBusBlockEntity bus = mergedPatternInventory.busOf(slot);
        return bus == null ? "" : bus.getPatternSearchKeywords(mergedPatternInventory.localSlotOf(slot));
    }

    @Override
    public boolean ownsAuxiliary(ItemStack stack) {
        return buses.stream().anyMatch(bus -> bus.ownsAuxiliary(stack));
    }

    @Override
    public boolean hasAuxiliaryRoom() {
        return buses.stream().anyMatch(ECOCraftingPatternBusBlockEntity::hasAuxiliaryRoom);
    }

    @Override
    public List<ItemStack> getAuxiliaryEncodedPatterns() {
        return buses.stream().flatMap(bus -> bus.getAuxiliaryEncodedPatterns().stream()).toList();
    }

    @Override
    public List<String> getAuxiliarySearchKeywords() {
        return buses.stream().flatMap(bus -> bus.getAuxiliarySearchKeywords().stream()).toList();
    }

    @Override
    public List<PatternSearchKeywords.Directions> getAuxiliaryPatternSearchDirections() {
        return buses.stream().flatMap(bus -> bus.getAuxiliaryPatternSearchDirections().stream()).toList();
    }

    @Override
    public long getAuxiliaryRevision() {
        long revision = 1;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            revision = revision * 31 + bus.getAuxiliaryRevision();
        }
        return revision;
    }

    /** Native revision tokens keep polling proportional to the number of buses. */
    @Override
    public int getPatternContentRevision() {
        int revision = 1;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            revision = revision * 31 + bus.getPatternContentRevision();
            revision = revision * 31 + bus.getPatternSlotCount();
        }
        return revision;
    }

    // ---------------------------------------------------------------- 影子卫生：绝不回写世界

    @Override
    public void updateState(boolean updateExposed) {
    }

    @Override
    public void markForUpdate() {
    }

    @Override
    public void saveChanges() {
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.noneOf(Direction.class);
    }
}
