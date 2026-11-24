package com.sorrowmist.useless.compat.neoecoae.compact.cluster;

import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import net.minecraft.core.BlockPos;

/**
 * 紧凑 L9 的存储集群：主机、通讯接口和内置成员盘。
 *
 * <p>ECO 的存储主机本身就是 {@code IStorageProvider}，无限存储模式下容量来自
 * {@code ECOInfiniteStorageEngine}，内置盘只参与成员校验，不承载库存。</p>
 */
public class CompactStorageCluster extends NEStorageCluster {

    public CompactStorageCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }
}
