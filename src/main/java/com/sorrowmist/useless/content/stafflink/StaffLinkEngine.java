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
     * 空转退避的倍率上限：下一轮间隔最多放大到配置周期的这个倍数。
     *
     * <p>空转（这一轮搬运量为 0）时把间隔翻倍，直到 {@code interval × BACKOFF_CAP} 封顶。
     * 一旦搬动就立刻恢复成配置的 {@code interval}。</p>
     *
     * <p><b>为什么需要它。</b>此前「搬不动」也按配置周期原样重排下一轮，而周期最小是 1 ——
     * 于是一个空的、卡住的通道会<b>每 tick 白跑一次完整流程</b>（解析端点 + 判断 + 读容器）。
     * 在有大量空通道的装置上，这部分开销实测独占 {@code onStaffLinkTick} 的 90.9%，
     * 真正的搬运反而微不足道。参考实现（P2P Channel Network）用指数退避解决同一问题，
     * 这里对齐它的做法。</p>
     *
     * <p><b>为什么封顶到 32。</b>间隔本身已经被 {@link StaffLinkRoute#MAX_INTERVAL} 夹在
     * 1200 tick 以内，再乘 32 就是 38400 tick（约 32 分钟）——足以让「长期没货」的通道
     * 基本不产生开销，又不会让「刚补货」的通道等太久才恢复。搬动一次即重置，所以恢复是
     * 即时的：只要容器里重新有东西，最坏情况下等一个退避周期就会再次尝试并命中。</p>
     */
    private static final int BACKOFF_CAP = 32;

    /**
     * 退避的指数上限 = {@code log2(BACKOFF_CAP)}。
     *
     * <p>间隔按 {@code interval << 退避} 增长，所以「翻几次」到的倍数上限就是 log2。
     * 到顶之后不再增加，避免长期空转的通道把间隔推到无限。</p>
     */
    private static final int BACKOFF_CAP_EXPONENT = Integer.numberOfTrailingZeros(BACKOFF_CAP);

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
        /**
         * 每条「锚点 × 线路」当前的退避次数（空转了几轮）。
         *
         * <p>刻意与 {@link #byAnchor} 平行存放，而不是塞进同一个数组：下一个可运行 tick 是
         * {@code long}（绝对值），退避次数是 {@code int}（相对值），语义不同，混在一个
         * {@code long[]} 里要靠位运算拆包，不值得。两者共用同一把锚点键、同一套 prune。</p>
         *
         * <p>只对「非 0 退避」的条目建行：稳态下绝大多数线路都是 0（正常搬运），
         * 为它们各留一个数组纯属浪费。</p>
         */
        private final Map<GlobalPos, int[]> backoffByAnchor = new HashMap<>();
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

        /** 记录这一轮的退避次数；0 表示恢复正常节奏，顺手把行删掉不留垃圾。 */
        void setBackoff(int route, GlobalPos anchor, int backoff) {
            if (backoff <= 0) {
                int[] slots = backoffByAnchor.get(anchor);
                if (slots != null) {
                    slots[route] = 0;
                }
                return;
            }
            int[] slots = backoffByAnchor.get(anchor);
            if (slots == null) {
                slots = new int[StaffLinkNetwork.ROUTE_COUNT];
                backoffByAnchor.put(anchor, slots);
            }
            slots[route] = backoff;
        }

        /** 这条「锚点 × 线路」当前空转了几轮；没有记录时是 0。 */
        int backoffOf(int route, GlobalPos anchor) {
            int[] slots = backoffByAnchor.get(anchor);
            return slots == null ? 0 : slots[route];
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
            // 退避表跟着一起清：行只在「有退避次数」时存在，线路没了就该丢。
            // 判据用 stillConfigured 而不是 byAnchor —— 有的线路还在配置里、只是这一轮
            // 恰好没排上队（byAnchor 里是 UNSCHEDULED），它的退避状态必须留着。
            backoffByAnchor.entrySet().removeIf(entry -> {
                for (int route = 0; route < entry.getValue().length; route++) {
                    if (entry.getValue()[route] > 0
                            && stillConfigured.test(entry.getKey(), route)) {
                        return false;
                    }
                }
                return true;
            });
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

    /**
     * 按「本轮搬动了没有」排下一轮，并维护退避次数。这是释放端的唯一排程入口。
     *
     * <p>搬动了 → 退避清零，下一轮按配置的 {@code interval}；空转 → 退避 +1，
     * 下一轮间隔为 {@code interval << min(退避, log2(BACKOFF_CAP))}（即每次翻倍，
     * 到 {@code interval × BACKOFF_CAP} 封顶）。见 {@link #BACKOFF_CAP}。</p>
     *
     * <p><b>为什么不是「一空转就跳过一个周期」。</b>那样对「周期 1」的通道等于没退避
     * （跳过 1 tick 还是每 tick 跑）；而「周期 1200」的通道本来就难得空转，翻倍也无所谓。
     * 指数退避对两种都成立：短周期通道迅速被推远，长周期通道几乎不受影响。</p>
     *
     * <p><b>它还承担着第二个职责：把「扫了一整遍但一个也没搬动」的代价压掉。</b>
     * 第九轮删掉了扫描端的 {@code MAX_SCAN_SLOTS} 预算（那是个语义 bug，会让排在后面的资源
     * 永远轮不到，见 {@code StaffLinkTargets#sweepItems}），改由「循环退出条件 = 搬够了」
     * 与这里的退避共同兜底。所以 {@code moved == 0} 必须记为一次空转
     * ——<b>这是删掉那个预算的前置条件</b>。调用点的判据就是 {@code result.moved() > 0L}：
     * 搬运完全没进展（含「扫完一遍但目标全满」）时 {@code moved} 为 0，
     * 计入空转，大网络上就不会反复白扫。</p>
     */
    private static void scheduleNextRun(UUID networkId, int route, GlobalPos anchor,
                                        long now, int interval, boolean moved) {
        NetworkSchedule schedule = NODE_NEXT_RUN.computeIfAbsent(networkId, id -> new NetworkSchedule());
        int backoff;
        if (moved) {
            backoff = 0;
        } else {
            // 上限按「翻几倍」算，避免 32 次翻倍之后 long 溢出（实际 interval 最大 1200）。
            backoff = Math.min(schedule.backoffOf(route, anchor) + 1, BACKOFF_CAP_EXPONENT);
        }
        schedule.setBackoff(route, anchor, backoff);
        long wait = backoff == 0L
                ? Math.max(1, interval)
                : Math.max(1, (long) interval << backoff);
        schedule.setNextRun(route, anchor, now + wait);
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
            // 「世界是否加载」到这里才判：没有释放端要跑时，吸收端的状态读了也没用。
            // 没通过的释放端不进 dueReleases，因此不会排程 —— 保持原语义：条件恢复后
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
                ServerLevel releaseLevel = levelOf(server, release.anchor());
                if (releaseLevel == null) {
                    // 世界没加载：保持原语义，下一 tick 立刻重试（不排程）。
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
                    // 配不上接收端：也算空转，走退避，免得这条线路每 tick 白跑。
                    scheduleNextRun(network.id(), routeIndex, release.anchor(),
                            now, release.interval(), false);
                    continue;
                }

                Distribution result = distribute(releaseLevel, release, targets, server);
                // 按「这一轮到底搬动了没有」排下一轮：空转走指数退避，见 scheduleNextRun。
                scheduleNextRun(network.id(), routeIndex, release.anchor(),
                        now, release.interval(), result.moved() > 0L);
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
     * 该线路这一 tick 是否放行：只要求锚点所在世界已加载。
     *
     * <p><b>红石触发已整体移除。</b>早先这里还会按 {@code LinkTrigger} 读一次
     * {@code getBestNeighborSignal}，实测在「大量通道空转」的装置里这一项独占
     * {@code onStaffLinkTick} 的 90.9%（1672 ms）——因为每个锚点每 tick 都要走 6 个方向的
     * 方块查询，而绝大多数通道当轮根本没有东西可搬。无线物流本身就是「持续搬运」语义，
     * 红石闸门在这个场景里既边缘又昂贵，因此连同 {@code LinkTrigger} 枚举一起删除。
     * 需要「什么时候搬」的玩家改用 {@code interval}（周期）表达。</p>
     */
    private static boolean passesGate(MinecraftServer server, StaffLinkRoute route) {
        return levelOf(server, route.anchor()) != null;
    }

    /** 锚点所在的服务端世界；未加载或维度不存在时返回 {@code null}。 */
    @Nullable
    public static ServerLevel levelOf(MinecraftServer server, GlobalPos anchor) {
        ServerLevel level = server.getLevel(anchor.dimension());
        return level != null && level.isLoaded(anchor.pos()) ? level : null;
    }
}
