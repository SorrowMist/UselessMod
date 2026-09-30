package com.sorrowmist.useless.compat.ae;

import appeng.api.AECapabilities;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.api.storage.MEStorage;
import appeng.util.Platform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * AE 网络解析的共用部分。
 *
 * <p>物品 / 流体 / 化学品 / 魔源四个端点都要「从方块拿到它所属的 ME 存储」这一件事，
 * 逻辑完全一样，所以抽到这里。本类只被已经确定加载了 AE2 的类引用。</p>
 *
 * <h2>端点是「任何挂网方块」，不是「无线访问点」</h2>
 *
 * <p>早先这里只认 {@code WirelessAccessPointBlockEntity}，于是玩家把 ME 接口、终端、
 * 总线这类方块绑进无线物流、并把资源类型选成 {@code AE_*} 时，端点永远解析成 {@code null}：
 * 方块本身明明就在、也明明挂在网上，却像不存在一样。现在判定改为
 * <b>注册了 AE2 网格节点宿主能力（{@code AECapabilities.IN_WORLD_GRID_NODE_HOST}）即可</b>，
 * 与 {@link AeDeviceLinker#isLinkTarget} 同一把尺子，无线访问点只是其中一种。</p>
 *
 * <h2>为什么判定不看「在线」</h2>
 *
 * <p>{@link #isEndpoint} 只看方块<b>是不是</b>网格成员，不看它此刻有没有电、有没有挂上网。
 * 掉电、掉线、节点尚未 create 都是暂时的：若据此认定方块没了，无线物流的自愈逻辑会把玩家
 * 配好的线整条删掉。取不到网络时端点表现为「空容器」（抽不出、塞不进），搬运自然失败，
 * 等网络回来又自动恢复。</p>
 */
final class AeNetworks {

    private AeNetworks() {
    }

    /**
     * 该坐标是不是一个 AE 网络端点。
     *
     * <p>只看方块是否注册了网格节点宿主能力，不看在线状态，理由见类注释。</p>
     */
    static boolean isEndpoint(Level level, BlockPos pos) {
        return host(level, pos) != null;
    }

    /** 该坐标的网格节点宿主能力；方块不是 AE 设备时为 {@code null}。 */
    @Nullable
    static IInWorldGridNodeHost host(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        try {
            return level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        } catch (Throwable notReady) {
            // 能力查询本身不该抛，但方块实体可能正处于替换/卸载的中间态。当作「暂时没有」。
            return null;
        }
    }

    /** 该坐标的网格节点；方块不是 AE 设备、或节点尚未 create 时为 {@code null}。 */
    @Nullable
    static IGridNode node(Level level, BlockPos pos) {
        IInWorldGridNodeHost host = host(level, pos);
        return host == null ? null : resolveNode(host);
    }

    /**
     * 该坐标所属网络的 ME 存储。
     *
     * <p>每次都现取而不是缓存：网络、存储服务、库存都会在运行期被 AE2 替换，缓存下来的
     * 引用迟早指向一个已经脱离网络的旧对象。</p>
     */
    @Nullable
    static MEStorage storage(Level level, BlockPos pos) {
        IGridNode node = node(level, pos);
        if (node == null) {
            return null;
        }
        try {
            IGrid grid = node.getGrid();
            return grid == null ? null : grid.getStorageService().getInventory();
        } catch (Throwable notReady) {
            // 节点尚未 create 时 getGrid() 会抛异常。这是「暂时用不了」，不是错误。
            return null;
        }
    }

    /**
     * 取宿主的网格节点。
     *
     * <p>三条路径按优先级依次尝试，前一条拿到就返回：</p>
     *
     * <ol>
     *   <li><b>契约路径</b>：{@code getGridNode(null)} —— 方块实体自身的主节点，
     *       以及挂在 CableBus <b>中心位</b>的线缆（{@code CableBusContainer.getGridNode(null)}
     *       会回退到 {@code storage.getCenter()}）。</li>
     *   <li><b>对外朝向节点</b>：逐个方向问 {@code getGridNode(side)}。只有重写了
     *       {@code IPart.getExternalFacingNode()} 的部件才在这条路上有值 ——
     *       目前是 P2P 隧道、石英纤维、开关总线三类。</li>
     *   <li><b>侧挂部件自身节点</b>：终端、各类总线、接口这些普通部件
     *       <b>没有</b>重写 {@code getExternalFacingNode()}（接口默认返回 null），
     *       它们的真实节点只在 {@code IPart.getGridNode()} 上。这类部件通常挂在 CableBus
     *       <b>侧面</b>，若该方块又没有中心线缆，前两条路径会一路 fallthrough 返回 null，
     *       表现为「孤立终端 / 孤立总线连不上」。这里直接遍历部件补回。</li>
     * </ol>
     */
    @Nullable
    static IGridNode resolveNode(IInWorldGridNodeHost host) {
        // ① 契约路径：方块实体主节点 / 中心线缆。
        try {
            IGridNode node = host.getGridNode(null);
            if (node != null) {
                return node;
            }
        } catch (Throwable ignored) {
        }

        // ② 对外朝向节点：P2P 隧道 / 石英纤维 / 开关总线。
        for (Direction direction : Direction.values()) {
            try {
                IGridNode node = host.getGridNode(direction);
                if (node != null) {
                    return node;
                }
            } catch (Throwable ignored) {
            }
        }

        // ③ 侧挂部件自身节点：终端 / 各类总线 / 接口。
        //    这些部件没重写 getExternalFacingNode()，CableBus 的 getGridNode(side)
        //    会在 ① 里返回 null，只能从部件本身取。
        if (host instanceof IPartHost partHost) {
            IGridNode firstAny = null;
            for (Direction side : Platform.DIRECTIONS_WITH_NULL) {
                try {
                    IPart part = partHost.getPart(side);
                    if (part == null) {
                        continue;
                    }
                    IGridNode node = part.getGridNode();
                    if (node == null) {
                        continue;
                    }
                    // 优先返回已就绪（已挂上网格）的节点；未就绪的暂存，
                    // 若整个方块尚未完成初始化，则由调用方在下一轮重试。
                    if (isNodeReady(node)) {
                        return node;
                    }
                    if (firstAny == null) {
                        firstAny = node;
                    }
                } catch (Throwable ignored) {
                }
            }
            return firstAny;
        }

        return null;
    }

    /** 节点是否已经挂上网格：未 create 的节点 {@code getGrid()} 会抛异常。 */
    static boolean isNodeReady(IGridNode node) {
        try {
            return node.getGrid() != null;
        } catch (Throwable notReady) {
            return false;
        }
    }
}