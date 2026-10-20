package com.sorrowmist.useless.compat.neoecoae.compact.cluster;

import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import net.minecraft.core.BlockPos;

/**
 * 紧凑 L9 的存储集群：只有主机自己。
 *
 * <p>ECO 的存储主机本身就是 {@code IStorageProvider}，无限存储模式下容量来自
 * {@code ECOInfiniteStorageEngine} 而不是驱动器，所以这个集群不需要任何影子组件。</p>
 */
public class CompactStorageCluster extends NEStorageCluster {

    public CompactStorageCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }
}
