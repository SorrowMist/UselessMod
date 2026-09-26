package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 无线物流的搬运引擎。
 *
 * <p>每个服务端 tick 遍历所有网络：按线路号分组，把「释放端」的资源搬给同线路同类型的
 * 「吸收端」。搬运完全在服务端进行，与玩家是否手持造化杖无关——网络存在存档里，
 * 所以杖放在箱子里、所在区块卸载都不影响。</p>
 *
 * <p>三条自我保护：</p>
 * <ul>
 *   <li><b>不强制加载</b>：锚点所在区块未加载就跳过本轮；</li>
 *   <li><b>预算</b>：单 tick 最多处理 {@link #NETWORK_BUDGET} 张网络；</li>
 *   <li><b>退避</b>：解析失败或空转的网络按 {@link #BACKOFF_TICKS} 推迟，避免每 tick 空跑。</li>
 * </ul>
 */
public final class StaffLinkEngine {
    private static final int NETWORK_BUDGET = 32;
    /** 锚点解析失败（区块没加载 / 方块没了）时的退避。 */
    private static final int BACKOFF_TICKS = 20;
    /** 自愈周期：清理锚点方块已消失的线路配置。 */
    private static final int SELF_HEAL_INTERVAL = 100;
    /** 刷新「本局活跃网络」集合的间隔。 */
    private static final int LIVE_REFRESH_INTERVAL = 20;
    /** 每次自愈最多检查多少张网络，避免一次扫全部。 */
    private static final int PRUNE_BUDGET = 16;

    /**
     * 每个「线路 × 容器」节点各自的下一次可运行 tick。
     *
     * <p>计时粒度必须细到单个容器：同一条线路上两个输入端的「周期」可以完全不同（1 和 1200），
     * 若只按线路记一个时间、取最小值，周期 1200 的那个也会跟着周期 1 的每 tick 跑，
     * 「周期」就形同虚设了。</p>
     *
     * <p>运行时状态，服务器停止时清空；解绑的节点会在自愈周期里清掉。</p>
     */
    private record NodeKey(int route, GlobalPos anchor) {
    }

    private static final Map<UUID, Map<NodeKey, Long>> NODE_NEXT_RUN = new ConcurrentHashMap<>();

    private static long nextRunAt(UUID networkId, int route, GlobalPos anchor) {
        Map<NodeKey, Long> schedule = NODE_NEXT_RUN.get(networkId);
        return schedule == null ? 0L : schedule.getOrDefault(new NodeKey(route, anchor), 0L);
    }

    private static void setNextRun(UUID networkId, int route, GlobalPos anchor, long tick) {
        NODE_NEXT_RUN.computeIfAbsent(networkId, id -> new ConcurrentHashMap<>())
                .put(new NodeKey(route, anchor), tick);
    }

    /** 这张网络有没有哪个容器到了该跑的时候。还没排过（首次）时算到点。 */
    private static boolean anyNodeDue(UUID networkId, long now) {
        Map<NodeKey, Long> schedule = NODE_NEXT_RUN.get(networkId);
        if (schedule == null || schedule.isEmpty()) {
            return true;
        }
        for (long nextRun : schedule.values()) {
            if (now >= nextRun) {
                return true;
            }
        }
        return false;
    }

    /**
     * 最近一次搬运的结果。
     *
     * <p>纯粹给界面看的：玩家能直接看到「这次请求搬 N 个、实际搬走 M 个、分给了 K 个输出」，
     * 搬不动时还带上卡在哪一步。不用靠猜。</p>
     */
    public record TransferStats(long tick, long requested, long moved, int targets,
                                StaffLinkTargets.TransferBlocker blocker) {
        public static final TransferStats NONE =
                new TransferStats(-1L, 0L, 0L, 0, StaffLinkTargets.TransferBlocker.NONE);
    }

    private static final Map<UUID, TransferStats> LAST_TRANSFER = new ConcurrentHashMap<>();

    /** 某张网络最近一次搬运的结果；还没搬过时返回 {@link TransferStats#NONE}。 */
    public static TransferStats lastTransfer(UUID networkId) {
        return networkId == null ? TransferStats.NONE
                : LAST_TRANSFER.getOrDefault(networkId, TransferStats.NONE);
    }

    private StaffLinkEngine() {
    }

    public static void tick(MinecraftServer server) {
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        List<StaffLinkNetwork> networks = List.copyOf(data.all());
        if (networks.isEmpty()) {
            return;
        }

        long now = server.getTickCount();
        if (now % LIVE_REFRESH_INTERVAL == 0) {
            // 刷新「本局被杖引用过的网络」；没被引用过的孤儿网络一律不跑。
            StaffLinkManager.refreshLiveNetworks(server);
        }

        int visited = 0;
        for (StaffLinkNetwork network : networks) {
            if (!StaffLinkManager.isLive(network.id())) {
                continue;
            }
            if (!anyNodeDue(network.id(), now)) {
                continue;
            }
            // 预算按「真正跑过的网络」计：跑过的会写下一次可运行时间，因此本 tick 没轮到的
            // 会在下一 tick 补上，不会饿死。
            if (visited >= NETWORK_BUDGET) {
                break;
            }
            visited++;
            runNetwork(server, network, now);
        }

        if (now % SELF_HEAL_INTERVAL == 0) {
            pruneStaleRoutes(server, data, networks);
            // 网络被解散/自愈删除、或节点被解绑后，调度表里的条目也要跟着走。
            NODE_NEXT_RUN.keySet().removeIf(id -> data.get(id) == null);
            for (StaffLinkNetwork network : networks) {
                Map<NodeKey, Long> schedule = NODE_NEXT_RUN.get(network.id());
                if (schedule != null) {
                    schedule.keySet().removeIf(key -> network.routeAt(key.anchor(), key.route()) == null);
                }
            }
        }
    }

    /** 服务器停止时清掉运行时状态，别把上一局的 tick 数带进下一局。 */
    public static void clearRuntimeState() {
        NODE_NEXT_RUN.clear();
        LAST_TRANSFER.clear();
        StaffLinkManager.clearLive();
    }

    /**
     * 网络刚被改动（绑定 / 解绑 / 改线路配置）时调用。
     *
     * <p>把它的「下一次可运行时间」清掉，让引擎在本 tick 就重跑一遍——否则新加进来的配对要等
     * 上一次排定的退避走完才被发现，表现就是「刚绑定没反应，过一会儿才动」。</p>
     */
    public static void wake(UUID networkId) {
        if (networkId != null) {
            NODE_NEXT_RUN.remove(networkId);
            StaffLinkManager.markLive(networkId);
        }
    }

    // ------------------------------------------------------------------ 单张网络

    private static void runNetwork(MinecraftServer server, StaffLinkNetwork network, long now) {
        List<List<StaffLinkRoute>> byRoute = new ArrayList<>(StaffLinkNetwork.ROUTE_COUNT);
        for (int route = 0; route < StaffLinkNetwork.ROUTE_COUNT; route++) {
            byRoute.add(new ArrayList<>());
        }
        for (StaffLinkRoute route : network.routes()) {
            byRoute.get(route.route()).add(route);
        }

        long requestedTotal = 0;
        long movedTotal = 0;
        int targetTotal = 0;
        StaffLinkTargets.TransferBlocker blocker = StaffLinkTargets.TransferBlocker.NONE;

        for (int routeIndex = 0; routeIndex < StaffLinkNetwork.ROUTE_COUNT; routeIndex++) {
            List<StaffLinkRoute> onRoute = byRoute.get(routeIndex);
            if (onRoute.isEmpty()) {
                continue;
            }

            // 只有「自己这个容器」到点的输入端才搬：同一条线路上两个输入的周期可以完全不同，
            // 不能因为其中一个到点了就把另一个也捎上。
            List<StaffLinkRoute> dueReleases = new ArrayList<>();
            List<StaffLinkRoute> absorbs = new ArrayList<>();
            for (StaffLinkRoute candidate : onRoute) {
                if (!candidate.enabled() || !candidate.medium().isSupported()) {
                    continue;
                }
                ServerLevel level = levelOf(server, candidate.anchor());
                if (level == null) {
                    continue;
                }
                if (!candidate.trigger().allows(level.getBestNeighborSignal(candidate.anchor().pos()))) {
                    continue;
                }
                if (candidate.flow() == LinkFlow.RELEASE) {
                    if (now >= nextRunAt(network.id(), routeIndex, candidate.anchor())) {
                        dueReleases.add(candidate);
                    }
                } else {
                    absorbs.add(candidate);
                }
            }
            if (dueReleases.isEmpty()) {
                continue;
            }

            Comparator<StaffLinkRoute> byWeight =
                    Comparator.comparingInt(StaffLinkRoute::weight).reversed();
            dueReleases.sort(byWeight);
            absorbs.sort(byWeight);

            for (StaffLinkRoute release : dueReleases) {
                // 不管这次搬没搬动，都按它自己的周期排下一轮——搬不动只是空转一次，
                // 不会变成每 tick 重试。
                setNextRun(network.id(), routeIndex, release.anchor(),
                        now + Math.max(1, release.interval()));

                ServerLevel releaseLevel = levelOf(server, release.anchor());
                if (releaseLevel == null) {
                    if (blocker == StaffLinkTargets.TransferBlocker.NONE) {
                        blocker = StaffLinkTargets.TransferBlocker.SOURCE_UNREACHABLE;
                    }
                    continue;
                }

                List<StaffLinkRoute> targets = new ArrayList<>(absorbs.size());
                for (StaffLinkRoute absorb : absorbs) {
                    if (absorb.medium() == release.medium()) {
                        targets.add(absorb);
                    }
                }
                if (targets.isEmpty()) {
                    continue;
                }

                Distribution result = distribute(releaseLevel, release, targets, server);                requestedTotal += release.amount();
                movedTotal += result.moved();
                targetTotal += targets.size();
                if (result.blocker() != StaffLinkTargets.TransferBlocker.NONE) {
                    blocker = result.blocker();
                }
            }
        }

        LAST_TRANSFER.put(network.id(), new TransferStats(now, requestedTotal, movedTotal, targetTotal, blocker));
    }

    // ------------------------------------------------------------------ 分配

    /** 一次分配的结果：搬走多少，以及没搬动时卡在哪一步。 */
    private record Distribution(long moved, StaffLinkTargets.TransferBlocker blocker) {
    }

    /**
     * 把一次搬运的总量分给同线路的多个输出。
     *
     * <p>「数量」是<b>这一次搬运的总量</b>，不是「每个输出各搬多少」——按后者写，一个输入接
     * 多个输出时总搬运量会变成 N 倍，而且先被遍历到的那个输出会把源抽干，后面的一个也拿不到
     * （表现就是「全送到了一个里面」）。</p>
     *
     * <p>平分时余数留给靠前的（也就是权重高的）输出：总量 5、两个输出 → 3 + 2；
     * 总量 2、五个输出 → 前两个各 1。某个输出装不下时，它少拿的部分会留给后面的。</p>
     */
    private static Distribution distribute(ServerLevel releaseLevel, StaffLinkRoute release,
                                           List<StaffLinkRoute> targets, MinecraftServer server) {
        long remaining = release.amount();
        long moved = 0;
        StaffLinkTargets.TransferBlocker blocker = StaffLinkTargets.TransferBlocker.NONE;

        for (int index = 0; index < targets.size() && remaining > 0; index++) {
            StaffLinkRoute target = targets.get(index);
            ServerLevel targetLevel = levelOf(server, target.anchor());
            if (targetLevel == null) {
                if (blocker == StaffLinkTargets.TransferBlocker.NONE) {
                    blocker = StaffLinkTargets.TransferBlocker.TARGET_UNREACHABLE;
                }
                continue;
            }
            long slotsLeft = targets.size() - index;
            // 向上取整的整数除法：余数留给靠前的（权重高的）。用整数算避免 double 在大数值上丢精度。
            long share = remaining / slotsLeft + (remaining % slotsLeft == 0 ? 0 : 1);
            share = Math.min(remaining, share);
            if (share <= 0) {
                break;
            }
            long transferred = StaffLinkTargets.transfer(releaseLevel, release, targetLevel, target, share);
            moved += transferred;
            remaining -= Math.min(transferred, share);
            if (transferred <= 0 && blocker == StaffLinkTargets.TransferBlocker.NONE) {
                // 没搬动：探一下卡在哪，界面上直接显示原因。
                blocker = StaffLinkTargets.diagnose(releaseLevel, release, targetLevel, target);
            }
        }
        return new Distribution(moved, blocker);
    }

    // ------------------------------------------------------------------ 自愈

    /**
     * 清理「锚点方块已经不在」的线路配置。
     *
     * <p>只在区块已加载时下结论：未加载的坐标一律保留，否则玩家跑远一点就会把整张网清空。
     * 判定刻意用「不指定面」探测——线路配了某个面并不代表方块本身失效，若按线路的面去探，
     * 一个只在侧面暴露库存的机器会被误删。资源类型当前不受支持时也跳过（不能因为没装
     * Mekanism 就把化学品线路删掉）。</p>
     */
    private static void pruneStaleRoutes(MinecraftServer server, StaffLinkSavedData data,
                                         List<StaffLinkNetwork> networks) {
        int visited = 0;
        boolean changed = false;
        for (StaffLinkNetwork network : networks) {
            if (visited++ >= PRUNE_BUDGET) {
                break;
            }
            List<StaffLinkRoute> stale = new ArrayList<>();
            for (StaffLinkRoute route : network.routes()) {
                if (!route.medium().isSupported()) {
                    continue;
                }
                ServerLevel level = server.getLevel(route.anchor().dimension());
                if (level == null || !level.isLoaded(route.anchor().pos())) {
                    continue;
                }
                if (StaffLinkTargets.resolve(level, route.anchor().pos(), null, route.medium()) == null) {
                    stale.add(route);
                }
            }
            if (stale.isEmpty()) {
                continue;
            }
            for (StaffLinkRoute route : stale) {
                network.detach(route.anchor());
            }
            changed = true;
            if (network.isEmpty()) {
                data.remove(network.id());
                NODE_NEXT_RUN.remove(network.id());
            }
        }
        if (changed) {
            data.markDirty();
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 锚点所在的服务端世界；未加载或维度不存在时返回 {@code null}。 */
    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, GlobalPos anchor) {
        ServerLevel level = server.getLevel(anchor.dimension());
        return level != null && level.isLoaded(anchor.pos()) ? level : null;
    }
}
