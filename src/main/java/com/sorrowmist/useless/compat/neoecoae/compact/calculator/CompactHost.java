package com.sorrowmist.useless.compat.neoecoae.compact.calculator;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECluster;
import net.minecraft.server.level.ServerLevel;

/**
 * 由紧凑方块实体实现：在集群刚刚成型时，往集群里安装「影子组件」。
 *
 * <p>安装完成后集群对 ECO 而言与一整套真实满配多方块没有区别。</p>
 */
public interface CompactHost<C extends NECluster<C>> {

    void installCompactComponents(C cluster, ServerLevel level);
}
