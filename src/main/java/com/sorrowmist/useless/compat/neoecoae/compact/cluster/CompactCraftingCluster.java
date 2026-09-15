package com.sorrowmist.useless.compat.neoecoae.compact.cluster;

import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingNetworkCluster;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * 紧凑 F9 的合成集群（8 台满长高能主机中的一台）。
 *
 * <p>ECO 的合成集群登记逻辑只做 instanceof 分类，不读世界，因此无需改写；
 * 影子工作核心、影子并行核心、影子样板总线直接走 ECO 原生分支。</p>
 *
 * <p><b>为什么要重写 {@code setNetworkCluster}</b>：同 {@link CompactComputationCluster}——
 * ECO 的 {@code NELogicalNetworkManager} 会把不进它管理的集群
 * 一律 {@code setNetworkCluster(null)}，虚拟合成判定（8 台满长高能主机）就会失效。
 * 这里只接受本类自己发起的注入。</p>
 */
public class CompactCraftingCluster extends NECraftingCluster {

    private boolean compactInjection;

    public CompactCraftingCluster(BlockPos boundMin, BlockPos boundMax) {
        super(boundMin, boundMax);
    }

    /** 由紧凑 F9 主机注入它自己装配出来的逻辑网络（唯一允许的赋值来源）。 */
    public void injectCompactNetworkCluster(@Nullable NECraftingNetworkCluster networkCluster) {
        compactInjection = true;
        try {
            super.setNetworkCluster(networkCluster);
        } finally {
            compactInjection = false;
        }
    }

    @Override
    public void setNetworkCluster(@Nullable NECraftingNetworkCluster networkCluster) {
        if (!compactInjection) {
            // 忽略 ECO 逻辑网络管理器的赋值 / 置空。
            return;
        }
        super.setNetworkCluster(networkCluster);
    }
}
