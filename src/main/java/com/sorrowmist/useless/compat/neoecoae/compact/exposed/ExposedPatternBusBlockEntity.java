package com.sorrowmist.useless.compat.neoecoae.compact.exposed;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.api.ECOPatternInsertionResult;
import cn.dancingsnow.neoecoae.api.ECOPreparedPattern;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
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
 * <p><b>为什么它不会抢派单</b>：它不进任何集群，{@code cluster == null} 让父类的
 * {@code isBusy()} 恒为 true、{@code getAvailablePatterns()} 读父类自己那份空库存，
 * 于是 AE2 的样板提供者列表里它始终是个「忙且没样板」的条目；真正派单的依旧是宿主
 * {@code CompactF9BlockEntity} 上的 {@code CompactPatternProvider}。</p>
 */
public class ExposedPatternBusBlockEntity extends ECOCraftingPatternBusBlockEntity {

    private final CombinedPatternInventory mergedPatternInventory = new CombinedPatternInventory();
    private final List<ECOCraftingPatternBusBlockEntity> buses = new ArrayList<>();
    private int[] busRevisions = new int[0];
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
        for (int index = 0; index < buses.size(); index++) {
            busRevisions[index] = buses.get(index).getPatternContentRevision();
        }
        this.hostNode = hostNode;
    }

    @Override
    public void onReady() {
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (reason != IGridNodeListener.State.POWER && reason != IGridNodeListener.State.GRID_BOOT) {
            return;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }
        for (ECOMachineInterfaceBlockEntity<?> machineInterface : grid.getActiveMachines(ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusTopologyChanged(this);
        }
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
        boolean changed = false;
        for (int index = 0; index < buses.size(); index++) {
            ECOCraftingPatternBusBlockEntity bus = buses.get(index);
            bus.flushScheduledPatternDetails();
            int revision = bus.getPatternContentRevision();
            if (busRevisions[index] == revision) {
                continue;
            }
            busRevisions[index] = revision;
            changed = true;
            notifyInterfacesOf(bus);
        }
        if (changed) {
            // 派单由宿主节点上的样板提供者负责，样板集一变就要让它重读合并结果。
            if (hostNode != null) {
                ICraftingProvider.requestUpdate(hostNode);
            }
        }
    }

    // ---------------------------------------------------------------- 合并视图

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return mergedPatternInventory;
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
    public void beginPatternBatch() {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            bus.beginPatternBatch();
        }
    }

    @Override
    public void endPatternBatch() {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            bus.endPatternBatch();
        }
    }

    // ---------------------------------------------------------------- 样板写入

    @Override
    public boolean insertPattern(ItemStack itemStack) {
        return insertPatternWithResult(itemStack) == ECOPatternInsertionResult.INSERTED;
    }

    @Override
    public ECOPatternInsertionResult insertPatternWithResult(ItemStack itemStack) {
        ECOPreparedPattern prepared = preparePattern(itemStack);
        return prepared == null ? ECOPatternInsertionResult.INCOMPATIBLE : insertPreparedPattern(prepared);
    }

    @Override
    public ECOPatternInsertionResult insertPatternKnownUnique(ItemStack itemStack) {
        ECOPreparedPattern prepared = preparePattern(itemStack);
        return prepared == null ? ECOPatternInsertionResult.INCOMPATIBLE : insertPreparedPatternKnownUnique(prepared);
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPattern(ECOPreparedPattern prepared) {
        return insertIntoBuses(bus -> bus.insertPreparedPattern(prepared));
    }

    @Override
    public ECOPatternInsertionResult insertPreparedPatternKnownUnique(ECOPreparedPattern prepared) {
        return insertIntoBuses(bus -> bus.insertPreparedPatternKnownUnique(prepared));
    }

    /** 紧凑机的样板仍然存在真实总线里，所以这里只是把插入转给第一条腾得出位置的总线。 */
    private ECOPatternInsertionResult insertIntoBuses(
            Function<ECOCraftingPatternBusBlockEntity, ECOPatternInsertionResult> action) {
        ECOPatternInsertionResult last = ECOPatternInsertionResult.NO_TARGET;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            ECOPatternInsertionResult result = action.apply(bus);
            if (result == ECOPatternInsertionResult.INSERTED) {
                return result;
            }
            last = result;
        }
        return last;
    }

    /** 内部总线都各自校验自己的逻辑域，去重交给样板目录做全网检查。 */
    @Override
    public boolean checksLogicalDomainForDuplicates() {
        return false;
    }

    // ---------------------------------------------------------------- 目录 / 终端读路径

    /** 内容版本取各内部总线版本的滚动哈希：任何一条变了，样板目录就会重建本视图的索引。 */
    @Override
    public int getPatternContentRevision() {
        int revision = 1;
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            revision = revision * 31 + bus.getPatternContentRevision();
        }
        return revision;
    }

    @Override
    public void refreshPatternDetailsForCatalog() {
        for (ECOCraftingPatternBusBlockEntity bus : buses) {
            bus.refreshPatternDetailsForCatalog();
        }
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

    // ---------------------------------------------------------------- 通知

    /** 把某条内部总线的整段下标标脏（具体动了哪个槽只有那条总线自己知道）。 */
    private void notifyInterfacesOf(ECOCraftingPatternBusBlockEntity bus) {
        int offset = mergedPatternInventory.offsetOf(bus);
        IGrid grid = getMainNode().getGrid();
        if (offset < 0 || grid == null) {
            return;
        }
        int count = bus.getPatternSlotCount();
        int[] slots = new int[count];
        for (int index = 0; index < count; index++) {
            slots[index] = offset + index;
        }
        for (ECOMachineInterfaceBlockEntity<?> machineInterface : grid.getActiveMachines(ECOMachineInterfaceBlockEntity.class)) {
            machineInterface.onPatternBusInventoryChanged(this, slots);
        }
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
