package com.sorrowmist.useless.compat.neoecoae.compact.cluster;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationNetworkCluster;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 紧凑 C9 的计算集群（8 台满配主机中的一台）。
 *
 * <p>影子驱动器不存在于世界，没有可靠的「上/下列」位置信息，因此这里直接按登记顺序交替
 * 归入上下两列——ECO 的极限模式只要求「上下驱动器全部装满有效闪存晶阵」，
 * 交替归列即可满足，并且仍然复用 ECO 自己的驱动器列表语义。</p>
 *
 * <p><b>为什么要重写 {@code setNetworkCluster}</b>：ECO 的
 * {@code NELogicalNetworkManager} 会在网格变化时把「它自己没见过的集群」的
 * {@code networkCluster} 一律置空（{@code rebuildComputation} 里对集合内每个集群
 * 先 {@code setNetworkCluster(null)}，只有 ≥2 台同网同频的真主机才会被重新赋值）。
 * 紧凑方块的 8 台逻辑主机是<b>我们自己装配</b>的、不进这个管理器，于是刚注入就被抹掉，
 * 极限模式（线程/并行/存储上限）随之失效。这里只接受本类自己发起的注入。</p>
 */
public class CompactComputationCluster extends NEComputationCluster {

    private boolean compactInjection;

    private int installedDrives;

    public CompactComputationCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    /** 由紧凑 C9 主机注入它自己装配出来的逻辑网络（唯一允许的赋值来源）。 */
    public void injectCompactNetworkCluster(@Nullable NEComputationNetworkCluster networkCluster) {
        compactInjection = true;
        try {
            super.setNetworkCluster(networkCluster);
        } finally {
            compactInjection = false;
        }
    }

    @Override
    public void setNetworkCluster(@Nullable NEComputationNetworkCluster networkCluster) {
        if (!compactInjection) {
            // 忽略 ECO 逻辑网络管理器的赋值 / 置空，防止它把我们自己的 8 台网络抹掉。
            return;
        }
        super.setNetworkCluster(networkCluster);
    }

    @Override
    public void addBlockEntity(NEBlockEntity<NEComputationCluster, ?> blockEntity) {
        if (blockEntity instanceof ECOComputationDriveBlockEntity drive) {
            installedDrives++;
            if ((installedDrives & 1) == 1) {
                getUpperDrives().add(drive);
            } else {
                drive.setLowerDrive(true);
                getLowerDrives().add(drive);
            }
            blockEntities.add(drive);
            return;
        }
        super.addBlockEntity(blockEntity);
    }
}
