package com.sorrowmist.useless.compat.neoecoae.compact.shadow;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 把一个「不落世界的影子节点」接到宿主节点上。
 *
 * <p>影子节点不进世界，永远不会被 AE2 的邻居扫描发现，所以必须显式
 * {@link GridHelper#createConnection}。而 AE2 的节点只能创建一次、同一条连接也只能建一次
 * （重复创建会抛 {@code IllegalStateException}），所以这里把「等宿主节点就绪 → 建节点 →
 * 建连」收敛成一个每 tick 幂等的入口。</p>
 *
 * <p>销毁节点会连带销毁它自己的连接，因此 {@link #detach()} 不需要单独断连。</p>
 */
public final class ShadowNodeLink {

    private final BlockEntity host;
    private IManagedGridNode target;
    private IManagedGridNode hostNode;
    private ServerLevel level;
    private boolean connected;

    public ShadowNodeLink(BlockEntity host) {
        this.host = host;
    }

    /** 记录要接上来的目标节点；换目标即重来一次。宿主节点要等 AE2 的网格 tick 才就绪，先收下即可。 */
    public void attach(IManagedGridNode target, ServerLevel level, IManagedGridNode hostNode) {
        this.target = target;
        this.level = level;
        this.hostNode = hostNode;
        this.connected = false;
    }

    /** 拆方块 / 卸载区块 / 重新装配时调用：连节点一起摘掉，避免留下幽灵机器。 */
    public void detach() {
        if (target != null && target.isReady()) {
            target.destroy();
        }
        target = null;
        hostNode = null;
        level = null;
        connected = false;
    }

    /** 宿主每 tick 调一次，直到真的连上；之后是空操作。 */
    public void tick() {
        if (connected || target == null || hostNode == null || level == null) {
            return;
        }
        IGridNode anchor = hostNode.getNode();
        if (anchor == null) {
            return;
        }
        if (!target.isReady()) {
            target.create(level, host.getBlockPos());
        }
        IGridNode attached = target.getNode();
        if (attached == null) {
            return;
        }
        try {
            GridHelper.createConnection(anchor, attached);
        } catch (IllegalStateException e) {
            // 已经连上了：对新节点而言这和刚连上没有区别。
        }
        connected = true;
    }
}
