package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.orientation.BlockOrientation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * 「影子通讯接口」：不落世界、但复用宿主身份的 ECO 通讯接口方块实体。
 *
 * <p><b>为什么要复用宿主身份</b>：LDLib2 的 RPC 包与字段同步包都按
 * {@code (BlockPos, BlockEntityType 实例)} 寻址，接收端还要做
 * {@code be.getType() == packet.blockEntityType} 的引用比较。所以这个影子对象必须用
 * <b>宿主自己的 {@link BlockEntityType} 与宿主坐标</b>构造，包才会先投到宿主，
 * 再由宿主 {@code CompactInterfaceAttachment} 转发进来。</p>
 *
 * <p><b>为什么用 calculator 而不是 cluster 判定界面类型</b>：{@code supportsXxxInterfaceUi()}
 * 是「cluster instanceof 或 calculator instanceof」，而 cluster 只存在于服务端，
 * calculator 是构造器注入、两端都有 ⇒ 两端界面分派必然一致（这正是第 6 轮崩溃的教训）。</p>
 */
public class ShadowInterfaceBlockEntity<C extends NECluster<C>> extends ECOMachineInterfaceBlockEntity<C> {

    /** 影子同步包的身份标记：宿主靠它在自己的同步包里认出「这一包是影子的」。 */
    public static final String SYNC_MARKER = "useless_compact_interface_sync";

    public ShadowInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
                                      NEClusterCalculator.Factory<C> calculator) {
        super(type, pos, blockState, calculator);
        // 一个 flag 都不要：影子节点额外吃通道时，通道不足会让 AE2 的 isActive() 为假，
        // grid.getActiveMachines(...) 就会漏掉它（界面样板列表与模糊过滤全空）。
        getMainNode().setFlags();
        // 影子节点不进世界也不做邻居扫描：它靠宿主节点显式连接入网，
        // 免得每个影子都去 load 邻居区块、并把自己算成"占用该区块"的网络节点。
        getMainNode().setInWorldNode(false);
    }

    /** 供接线层判断缝合是否还有效：{@code NECluster.destroy()} 会把成员的 cluster 置空。 */
    public C compactCluster() {
        return cluster;
    }

    @Override
    public synchronized void writeCustomSyncData(HolderLookup.Provider provider, CompoundTag tag) {
        super.writeCustomSyncData(provider, tag);
        tag.putBoolean(SYNC_MARKER, true);
    }

    // ------------------------------------------------ 影子卫生：绝不回写世界
    // 这些入口在 ECO 里都会按 worldPosition 改宿主坐标上的真实方块，影子必须掐掉。

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
