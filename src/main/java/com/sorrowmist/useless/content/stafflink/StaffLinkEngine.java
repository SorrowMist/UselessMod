package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import com.sorrowmist.useless.world.stafflink.StaffLinkSavedData;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

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
     * 每张网络的调度表：锚点 → 各线路号的下一次可运行 tick。
     *
     * <p>计时粒度必须细到单个容器：同一条线路上两个输入端的「周期」可以完全不同（1 和 1200），
     * 若只按线路记一个时间、取最小值，周期 1200 的那个也会跟着周期 1 的每 tick 跑，
     * 「周期」就形同虚设了。</p>
     *
     * <p>用「锚点 → {@code long[]}」而不是「线路号 + 锚点」当 map 的键：锚点本来就是调用方手里
     * 现成的 {@link GlobalPos}，线路号只是数组下标，于是查询与写入都不必再构造临时对象。</p>
     *
     * <p>运行时状态，服务器停止时清空；解绑的节点会在自愈周期里清掉。</p>
     */
    private static final Map<UUID, NetworkSchedule> NODE_NEXT_RUN = new HashMap<>();

    private static final class NetworkSchedule {
        /** 该线路号还没排过队。 */
        private static final long UNSCHEDULED = Long.MIN_VALUE;

        private final Map<GlobalPos, long[]> byAnchor = new HashMap<>();
        /** 表内最小的「已排定」tick；{@code null} 表示需要重算。 */
        private Long earliest;

        long nextRun(int route, GlobalPos anchor) {
            long[] slots = byAnchor.get(anchor);
            if (slots == null) {
                return 0L;
            }
            long value = slots[route];
            return value == UNSCHEDULED ? 0L : value;
        }

        void setNextRun(int route, GlobalPos anchor, long tick) {
            byAnchor.computeIfAbsent(anchor, key -> unscheduledSlots())[route] = tick;
            // 排进去的值只会把「最快到点」往后推，缓存必须作废。
            earliest = null;
        }

        /** 清掉已经没有对应线路配置的条目；整条锚点都空了就把锚点也删掉。 */
        void pruneAgainst(BiPredicate<GlobalPos, Integer> stillConfigured) {
            boolean changed = false;
            Iterator<Map.Entry<GlobalPos, long[]>> iterator = byAnchor.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<GlobalPos, long[]> entry = iterator.next();
                long[] slots = entry.getValue();
                boolean empty = true;
                for (int route = 0; route < slots.length; route++) {
                    if (slots[route] != UNSCHEDULED && !stillConfigured.test(entry.getKey(), route)) {
                        slots[route] = UNSCHEDULED;
                        changed = true;
                    }
                    empty &= slots[route] == UNSCHEDULED;
                }
                if (empty) {
                    iterator.remove();
                    changed = true;
                }
            }
            if (changed) {
                earliest = null;
            }
        }

        /**
         * 这张表里有没有已经到点的条目；还没有任何条目时算到点（首次）。
         *
         * <p>「没有条目就算到点」是原语义，别改：刚绑定、还没跑过的网络必须能立刻跑一次。</p>
         */
        boolean anyDue(long now) {
            if (byAnchor.isEmpty()) {
                return true;
            }
            Long cached = earliest;
            if (cached != null && now < cached) {
                // 快路径：连最快的那个都还没到点，其余的更不用看。稳态下走的就是这一条。
                return false;
            }
            long earliestSeen = Long.MAX_VALUE;
            boolean due = false;
            for (long[] slots : byAnchor.values()) {
                for (long value : slots) {
                    if (value == UNSCHEDULED) {
                        continue;
                    }
                    if (now >= value) {
                        due = true;
                    } else if (value < earliestSeen) {
                        earliestSeen = value;
                    }
                }
            }
            if (earliestSeen == Long.MAX_VALUE) {
                // 一个已排定的条目都没有（pruneAgainst 之后理论上不会出现）。当作首次，别让网络饿死。
                earliest = null;
                return true;
            }
            // 有到点的条目时缓存作废：它下一刻就可能被重排，不能拿旧的最小值去挡。
            earliest = due ? null : earliestSeen;
            return due;
        }

        private static long[] unscheduledSlots() {
            long[] slots = new long[StaffLinkNetwork.ROUTE_COUNT];
            Arrays.fill(slots, UNSCHEDULED);
            return slots;
        }
    }

    private static long nextRunAt(UUID networkId, int route, GlobalPos anchor) {
        NetworkSchedule schedule = NODE_NEXT_RUN.get(networkId);
        return schedule == null ? 0L : schedule.nextRun(route, anchor);
    }

    private static void setNextRun(UUID networkId, int route, GlobalPos anchor, long tick) {
        NODE_NEXT_RUN.computeIfAbsent(networkId, id -> new NetworkSchedule())
                .setNextRun(route, anchor, tick);
    }

    /** 这张网络有没有哪个容器到了该跑的时候。还没排过（首次）时算到点。 */
    private static boolean anyNodeDue(UUID networkId, long now) {
        NetworkSchedule schedule = NODE_NEXT_RUN.get(networkId);
        return schedule == null || schedule.anyDue(now);
    }

    /** 输出端的优先级：权重降序；同权重保持列表次序（{@code sort} 是稳定的）。 */
    private static final Comparator<StaffLinkRoute> BY_WEIGHT =
            Comparator.comparingInt(StaffLinkRoute::weight).reversed();

    // ------------------------------------------------------------------ 主循环

    public static void tick(MinecraftServer server) {
        StaffLinkSavedData data = StaffLinkSavedData.get(server);
        // all() 本身就返回一份快照副本，不必再复制一次。
        List<StaffLinkNetwork> networks = data.all();
        if (networks.isEmpty()) {
            return;
        }

        long now = server.getTickCount();
        if (now % LIVE_REFRESH_INTERVAL == 0) {
            // 刷新「归属者本局有成员在线的网络」；没有归属者认领的孤儿网络一律不跑。
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
            // 网络被解散 / 自愈删除、或节点被解绑后，调度表里的条目也要跟着走。
            NODE_NEXT_RUN.keySet().removeIf(id -> data.get(id) == null);
            for (StaffLinkNetwork network : networks) {
                NetworkSchedule schedule = NODE_NEXT_RUN.get(network.id());
                if (schedule != null) {
                    schedule.pruneAgainst((anchor, route) -> network.routeAt(anchor, route) != null);
                }
            }
            // 「上次命中的面」提示失手只会退回完整扫描、不会读错，清它纯粹是防止无限增长。
            StaffLinkTargets.clearCapabilityHints();
        }
    }

    /** 服务器停止时清掉运行时状态，别把上一局的 tick 数带进下一局。 */
    public static void clearRuntimeState() {
        NODE_NEXT_RUN.clear();
        StaffLinkManager.clearLive();
        StaffLinkTargets.clearCapabilityHints();
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
        // 按线路号分桶。用数组 + 惰性建桶：ROUTE_COUNT 个列表里绝大多数是空的，
        // 每 tick 每个网络白建 9 个对象不划算。
        @SuppressWarnings("unchecked")
        List<StaffLinkRoute>[] byRoute = new List[StaffLinkNetwork.ROUTE_COUNT];
        for (StaffLinkRoute route : network.routes()) {
            int index = route.route();
            List<StaffLinkRoute> bucket = byRoute[index];
            if (bucket == null) {
                bucket = new ArrayList<>();
                byRoute[index] = bucket;
            }
            bucket.add(route);
        }

        for (int routeIndex = 0; routeIndex < StaffLinkNetwork.ROUTE_COUNT; routeIndex++) {
            List<StaffLinkRoute> onRoute = byRoute[routeIndex];
            if (onRoute == null) {
                continue;
            }

            // ---- 第一遍：只挑「这一 tick 真的到点」的释放端 ----
            //
            // 顺序很要紧：周期判定必须排在解析世界与读红石之前。绝大多数 tick 里一条线路都
            // 没到点，先判周期就能整段跳过；反过来写等于每 tick 对每条线路都做一次
            // levelOf + 6 个方向的方块查询，白耗。吸收端的桶也是惰性建的：走到这里就返回的
            // 情况（稳态下的多数 tick）连那个列表都不必建。
            //
            // 只有「自己这个容器」到点的输入端才搬：同一条线路上两个输入的周期可以完全不同，
            // 不能因为其中一个到点了就把另一个也捎上。
            List<StaffLinkRoute> dueReleases = null;
            for (StaffLinkRoute candidate : onRoute) {
                if (!candidate.enabled() || !candidate.medium().isSupported()
                        || candidate.flow() != LinkFlow.RELEASE) {
                    continue;
                }
                if (now < nextRunAt(network.id(), routeIndex, candidate.anchor())) {
                    continue;
                }
                if (dueReleases == null) {
                    dueReleases = new ArrayList<>(2);
                }
                dueReleases.add(candidate);
            }
            if (dueReleases == null) {
                continue;
            }

            // ---- 确认有活要干，才去碰方块 ----
            //
            // 红石闸门与「世界是否加载」到这里才判：没有释放端要跑时，吸收端的红石状态读了也没用。
            // 没通过的释放端不进 dueReleases，因此不会 setNextRun —— 保持原语义：条件恢复后
            // 下一 tick 立刻再试，而不是等一个周期。
            dueReleases.removeIf(release -> !passesGate(server, release));
            if (dueReleases.isEmpty()) {
                continue;
            }
            List<StaffLinkRoute> absorbs = new ArrayList<>(2);
            for (StaffLinkRoute candidate : onRoute) {
                if (candidate.enabled() && candidate.medium().isSupported()
                        && candidate.flow() != LinkFlow.RELEASE
                        && passesGate(server, candidate)) {
                    absorbs.add(candidate);
                }
            }

            dueReleases.sort(BY_WEIGHT);
            absorbs.sort(BY_WEIGHT);

            for (StaffLinkRoute release : dueReleases) {
                // 不管这次搬没搬动，都按它自己的周期排下一轮——搬不动只是空转一次，
                // 不会变成每 tick 重试。
                setNextRun(network.id(), routeIndex, release.anchor(),
                        now + Math.max(1, release.interval()));

                ServerLevel releaseLevel = levelOf(server, release.anchor());
                if (releaseLevel == null) {
                    continue;
                }

                List<StaffLinkRoute> targets = new ArrayList<>(absorbs.size());
                for (StaffLinkRoute absorb : absorbs) {
                    // 按 family 配对而不是按 medium：箱子（ITEM）与 AE 网络（AE_ITEM）
                    // 都是「物品」，能互相搬运；同一种资源类型自己配自己也照常成立。
                    if (absorb.medium().family() == release.medium().family()) {
                        targets.add(absorb);
                    }
                }
                if (targets.isEmpty()) {
                    continue;
                }

                distribute(releaseLevel, release, targets, server);
            }
        }
    }

    // ------------------------------------------------------------------ 分配

    /** 一次分配的结果：搬走多少。 */
    private record Distribution(long moved) {
    }

    /**
     * 把这次搬运分给同线路的多个输出。
     *
     * <p><b>「数量」是每个输出各搬多少</b>，不是这次搬运的总量：每个输出最多搬 {@code amount}，
     * 相互之间不瓜分。所以一个输入接 N 个输出时，这一轮的搬运上限是 {@code N × 数量}。</p>
     *
     * <p>这样改是因为<b>接收端各自设了不同的过滤时，它们本来就不争抢同一批货</b>：
     * 一台只收铁、一台只收金，按总量平分等于让它们互相「占额度」，各自只能拿到 1/N。</p>
     *
     * <p>代价要讲清楚：<b>源端的库存仍然是共享的</b>，前面的输出先取、取完为止，后面的只能拿到
     * 剩下的。源不够分时表现就是「东西全进了靠前的那个」（也就是权重高的那个）。要避免它，
     * 要么把数量调小，要么给接收端设过滤让它们各取所需。</p>
     */
    private static Distribution distribute(ServerLevel releaseLevel, StaffLinkRoute release,
                                           List<StaffLinkRoute> targets, MinecraftServer server) {
        // 源端点解析一次，所有接收端共用。AE 端点每次解析都要「取方块实体 → 取能力」并新建
        // 一份端点实例，按接收端个数重复付这笔钱没有意义；顺带也让端点内部的整网快照
        // （只在无过滤器的扫描路径上才会用到）在多个接收端之间保持一致。
        Object from = StaffLinkTargets.resolveSource(releaseLevel, release);
        if (from == null) {
            return new Distribution(0L);
        }
        long amount = release.amount();
        long moved = 0;

        for (StaffLinkRoute target : targets) {
            ServerLevel targetLevel = levelOf(server, target.anchor());
            if (targetLevel == null) {
                continue;
            }
            moved += StaffLinkTargets.transfer(from, releaseLevel, release, targetLevel, target, amount);
        }
        return new Distribution(moved);
    }

    // ------------------------------------------------------------------ 自愈

    /**
     * 清理「锚点方块已经不在」的线路配置。
     *
     * <p>只在区块已加载时下结论：未加载的坐标一律保留，否则玩家跑远一点就会把整张网清空。</p>
     *
     * <p><b>判据是「方块本身没了」，不是「当前介质解析不出」。</b> 这一点必须说清楚，因为早先
     * 正是拿 {@code resolve(...) == null} 当判据，才出现了「过几秒容器被自动删掉」：解析失败
     * 的原因太多了——方块确实被挖掉只是其中之一，此外还有「这一格本来就不是该介质的容器」
     * 「ME 接口选了 AE 化学品而附属没装」「AE 网络暂时离线」等等。这些情况下方块都还立在那里，
     * 玩家配的线不该被抹掉。搬运失败会自己失败并在界面显示卡在哪一步，条件恢复又自动继续，
     * 不需要自愈来「帮忙」。资源类型当前不受支持时也跳过（不能因为没装 Mekanism 就删化学品线路）。</p>
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
                if (!StaffLinkTargets.anchorPresent(level, route.anchor().pos())) {
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

    /**
     * 该线路这一 tick 是否放行：锚点所在世界已加载，且红石触发条件满足。
     *
     * <p>触发条件是 {@link LinkTrigger#ALWAYS} 时<b>不读红石信号</b>——它本来就不用这个值
     * （见 {@code LinkTrigger#allows}），而读一次要走 6 个方向的方块查询。默认触发条件正是
     * ALWAYS，所以这一条能把绝大部分红石开销省掉。</p>
     */
    private static boolean passesGate(MinecraftServer server, StaffLinkRoute route) {
        ServerLevel level = levelOf(server, route.anchor());
        if (level == null) {
            return false;
        }
        LinkTrigger trigger = route.trigger();
        return trigger == LinkTrigger.ALWAYS
                || trigger.allows(level.getBestNeighborSignal(route.anchor().pos()));
    }

    /** 锚点所在的服务端世界；未加载或维度不存在时返回 {@code null}。 */
    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, GlobalPos anchor) {
        ServerLevel level = server.getLevel(anchor.dimension());
        return level != null && level.isLoaded(anchor.pos()) ? level : null;
    }
}
