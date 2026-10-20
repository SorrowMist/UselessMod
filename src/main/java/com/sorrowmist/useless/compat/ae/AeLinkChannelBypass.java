package com.sorrowmist.useless.compat.ae;

import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * 造化杖「AE 连接模式」那条连接的**通道豁免**索引。
 *
 * <p>背景（读 AE2 19.2.17 源码得出，别再被那个 32 误导）：通道上限与 {@code GridConnection} 无关 ——
 * {@code GridConnection#getMaxChannels()}（{@code 32 × cableCapacityFactor}）在通道计算里
 * <b>没有任何调用点</b>；真正的闸门是沿途节点的 {@code GridNode#getMaxChannels()}
 * （{@code 8 × factor}，带 {@code DENSE_CAPACITY} 才 {@code 32 × factor}，只有 {@code INFINITE}
 * 模式返回 {@code MAX_VALUE}），外加「无控制器网格」那条
 * {@code ChannelMode#getAdHocNetworkChannels()}（DEFAULT = 8）的总量限制。
 * 所以想让连接无视频道，只能改这两处计算 —— 也就是这里的两个 mixin。</p>
 *
 * <p>本类只回答两个问题：</p>
 * <ul>
 *   <li>{@link #crossesLink} —— 某个需要通道的节点，它的通道去路是否跨过了我们建的连接
 *       （跨过 ⇒ 直接放行、且完全不进瓶颈记账 ⇒ 那条连接等价于「不占通道也不封顶的线缆」）</li>
 *   <li>{@link #carriesLinkTraffic} —— 某个节点是否处在「链路」上（用于精准静音 AE2 的超容自检日志）</li>
 * </ul>
 *
 * <p><b>全部按身份比较</b>：网格重建时节点/连接都会被换成新对象，{@code equals} 在这套体系里不可靠。</p>
 */
public final class AeLinkChannelBypass {
    /** 路由遍历的最大步数，仅用于防御控制器节点自身也持有 route 指针所形成的环。 */
    private static final int MAX_ROUTE_STEPS = 512;

    /** 访问点侧节点 → 它连着的机器侧节点们（一个访问点通常连多台机器）。 */
    private static final Map<IGridNode, Set<IGridNode>> MACHINES_BY_ACCESS = new IdentityHashMap<>();

    /** 一次通道重算期间缓存的「链路节点」集合。 */
    private static Set<GridNode> laneCache;
    private static boolean laneValid;

    private AeLinkChannelBypass() {
    }

    // ------------------------------------------------------------------ 登记

    public static void register(IGridNode accessNode, IGridNode machineNode) {
        MACHINES_BY_ACCESS
                .computeIfAbsent(accessNode, ignored -> Collections.newSetFromMap(new IdentityHashMap<>()))
                .add(machineNode);
        invalidate();
    }

    public static void unregister(IGridNode accessNode, IGridNode machineNode) {
        Set<IGridNode> machines = MACHINES_BY_ACCESS.get(accessNode);
        if (machines != null && machines.remove(machineNode) && machines.isEmpty()) {
            MACHINES_BY_ACCESS.remove(accessNode);
        }
        invalidate();
    }

    public static boolean hasLinks() {
        return !MACHINES_BY_ACCESS.isEmpty();
    }

    /**
     * 清掉已经失效的登记。
     *
     * <p>AE2 的节点与连接在区块卸载、方块实体重建时会被整体换掉（连接更是连存档都不写），
     * 而我们这张表是按<b>身份</b>查的：旧条目再也匹配不上任何新连接（所以不会误判），
     * 但会一直把旧节点强引用住，并且 {@link #farSideNodes()} 每次通道重算都要遍历一遍 ——
     * 连接反复重建时表会一直长。低频清一次即可。</p>
     *
     * <p>判据只看「那条连接还在不在」：连接被拆掉就说明这条链路已经不成立，
     * 对应登记本来也再也匹配不上任何东西。重建连接时 {@code AeDeviceLinker#createLink} 会重新登记，
     * 所以清掉是幂等且自愈的。</p>
     */
    public static void pruneStaleLinks() {
        if (MACHINES_BY_ACCESS.isEmpty()) {
            return;
        }
        boolean changed = false;
        Iterator<Map.Entry<IGridNode, Set<IGridNode>>> entries = MACHINES_BY_ACCESS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<IGridNode, Set<IGridNode>> entry = entries.next();
            if (entry.getValue().removeIf(machine -> !isStillLinked(entry.getKey(), machine))) {
                changed = true;
            }
            if (entry.getValue().isEmpty()) {
                entries.remove();
                changed = true;
            }
        }
        if (changed) {
            invalidate();
        }
    }

    /** 访问点与机器之间是否还有一条「我们建的」非空间连接（节点已销毁按失效处理）。 */
    private static boolean isStillLinked(IGridNode accessNode, IGridNode machineNode) {
        try {
            for (IGridConnection connection : accessNode.getConnections()) {
                if (!connection.isInWorld() && connection.getOtherSide(accessNode) == machineNode) {
                    return true;
                }
            }
        } catch (Throwable broken) {
            // 节点可能在这次清理前被销毁，按失效处理。
        }
        return false;
    }

    /** 每次通道重算开始时把「链路节点」缓存作废。 */
    public static void invalidate() {
        laneCache = null;
        laneValid = false;
    }

    /** 服务器停止时清空，别把上一局的节点引用留到下一局。 */
    public static void clear() {
        MACHINES_BY_ACCESS.clear();
        invalidate();
    }

    /** 这条连接是不是我们建的。 */
    public static boolean isOurLink(IGridConnection connection) {
        if (MACHINES_BY_ACCESS.isEmpty()) {
            return false;
        }
        IGridNode a = connection.a();
        IGridNode b = connection.b();
        Set<IGridNode> machines = MACHINES_BY_ACCESS.get(a);
        if (machines != null && machines.contains(b)) {
            return true;
        }
        machines = MACHINES_BY_ACCESS.get(b);
        return machines != null && machines.contains(a);
    }

    // ------------------------------------------------------------------ 通道判定

    /**
     * 从 {@code start} 沿「去控制器」的路走，路上是否跨过我们建的连接。
     *
     * <p>{@code GridNode#getControllerRoute()} 给出去控制器的下一条连接，
     * {@code GridConnection#getControllerRoute()} 给出更靠控制器那一侧的节点，
     * 于是「节点 → 连接 → 节点 → …」交替前进即可；到控制器后指针会打转，用 seen 集合收住。</p>
     */
    public static boolean crossesLink(@Nullable GridNode start) {
        if (start == null || MACHINES_BY_ACCESS.isEmpty()) {
            return false;
        }
        Set<IPathItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IPathItem item = start;
        seen.add(item);
        for (int step = 0; step < MAX_ROUTE_STEPS; step++) {
            if (item instanceof IGridConnection connection && isOurLink(connection)) {
                return true;
            }
            IPathItem next;
            try {
                next = item.getControllerRoute();
            } catch (Throwable noRoute) {
                return false;
            }
            if (next == null || !seen.add(next)) {
                return false;
            }
            item = next;
        }
        return false;
    }

    /**
     * 该节点是否在「链路」上：机器侧整片子网，以及从机器节点一路到控制器的祖先链。
     * 这些节点因造化杖连接而超容，静音其自检日志可避免掩盖 AE2 的真实问题。
     */
    public static boolean carriesLinkTraffic(GridNode node) {
        if (MACHINES_BY_ACCESS.isEmpty()) {
            return false;
        }
        Set<GridNode> lane = lane();
        return lane != null && lane.contains(node);
    }

    /**
     * 机器侧那片子网的全部节点（跨过我们的连接就到不了的那些）。
     * 无控制器（ad-hoc）网格的通道配额计数里要把它排除掉。
     */
    public static Set<IGridNode> farSideNodes() {
        Set<IGridNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        if (MACHINES_BY_ACCESS.isEmpty()) {
            return result;
        }
        for (Set<IGridNode> machines : MACHINES_BY_ACCESS.values()) {
            for (IGridNode machine : machines) {
                collectFarSide(machine, result);
            }
        }
        return result;
    }

    @Nullable
    private static synchronized Set<GridNode> lane() {
        if (laneValid) {
            return laneCache;
        }
        laneValid = true;
        Set<IGridNode> nodes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Set<IGridNode> machines : MACHINES_BY_ACCESS.values()) {
            for (IGridNode machine : machines) {
                collectFarSide(machine, nodes);
                collectAncestors(machine, nodes);
            }
        }
        Set<GridNode> result = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IGridNode node : nodes) {
            if (node instanceof GridNode gridNode) {
                result.add(gridNode);
            }
        }
        laneCache = result;
        return laneCache;
    }

    /** 从机器侧节点做一次「不跨过我们的连接」的遍历，收集它那一侧的整片子网。 */
    private static void collectFarSide(IGridNode start, Set<IGridNode> out) {
        Deque<IGridNode> queue = new ArrayDeque<>();
        Set<IGridNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            IGridNode current = queue.poll();
            out.add(current);
            try {
                for (IGridConnection connection : current.getConnections()) {
                    if (isOurLink(connection)) {
                        continue;
                    }
                    IGridNode other = connection.getOtherSide(current);
                    if (other != null && seen.add(other)) {
                        queue.add(other);
                    }
                }
            } catch (Throwable broken) {
                // 节点可能在这次重算里被销毁，跳过即可。
            }
        }
    }

    /** 从机器侧节点沿路由一路收到控制器（含访问点及其上游）。 */
    private static void collectAncestors(IGridNode machine, Set<IGridNode> out) {
        // IPathItem 是 appeng.me 的实现接口（GridNode / GridConnection 各自实现），IGridNode 本身不是。
        if (!(machine instanceof GridNode start)) {
            return;
        }
        Set<IPathItem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        IPathItem item = start;
        seen.add(item);
        for (int step = 0; step < MAX_ROUTE_STEPS; step++) {
            IPathItem next;
            try {
                next = item.getControllerRoute();
            } catch (Throwable noRoute) {
                return;
            }
            if (next == null || !seen.add(next)) {
                return;
            }
            if (next instanceof GridNode node) {
                out.add(node);
            }
            item = next;
        }
    }
}
