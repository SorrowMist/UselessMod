package com.sorrowmist.useless.compat.create;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.content.stafflink.StaffLinkRoute;
import com.sorrowmist.useless.network.StaffLinkStressStatusPacket;
import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 应力线路的运行时状态。
 *
 * <p><b>全部是运行时状态，不存档。</b>转速与方向这两样配置存在 {@link StaffLinkRoute} 里，
 * 已经随无线物流的存档一起持久化了；这里保存的只是「本 tick 谁被驱动成了什么转速」
 * 「本 tick 给哪个网络补了多少容量」「源网络要承担多少负载」这类可以随时重算的东西。
 * 服务器重启后第一个 tick 就会重建，因此没必要多占一个存档键。</p>
 *
 * <p><b>两个方向的数据流。</b></p>
 * <ul>
 *   <li><b>向外</b>：给输出端所在网络补上虚拟容量。动力学那边每次重算网络容量时
 *       都会来问 {@link #additionalCapacity(long)}。</li>
 *   <li><b>向内</b>：把目标网络的实际需求回馈成源网络的负载，见
 *       {@link #additionalStress(long)}。这是「不凭空产生动力」的关键——
 *       源网络该扛多少就扛多少，扛不住就和目标一起停。</li>
 * </ul>
 *
 * <p>所有跨 tick 的集合都在 {@link #beginTick} 里按 tick 号整体换新，
 * 因此「某一轮的结果」不会残留到下一轮。</p>
 */
public final class StaffLinkStressState {

    public static final StaffLinkStressState INSTANCE = new StaffLinkStressState();

    /** 桥停手多久之后，输出端方块身上的虚拟转速就该被摘掉。 */
    private static final int CLAIM_EXPIRY_TICKS = 40;
    /** 同一张网络的同类冲突提示最多多久提醒一次。 */
    private static final int CONFLICT_NOTICE_INTERVAL = 40;
    /**
     * 「源网络里有无限动力源」时使用的代用容量。
     *
     * <p>不需要精确：只要大到任何真实负载都压不过它，效果就是「无限」。</p>
     */
    private static final float INFINITE_POWER = 4.2535293E37F;
    /** 判定两个转速是否相等的容差。 */
    private static final float SPEED_EPSILON = 0.001F;
    /** 视为「无限动力源」的方块。 */
    private static final ResourceLocation INFINITE_SOURCE_BLOCK =
            ResourceLocation.fromNamespaceAndPath("create", "creative_motor");

    // ------------------------------------------------------------------ 状态码
    // 空串表示一切正常；其余都是给界面取翻译键用的固定标识。

    private static final String STATE_OK = "";
    /** 输入端所在的网络根本没在转。 */
    private static final String STATE_IDLE = "idle";
    /** 输入端方块不在任何动力学网络里。 */
    private static final String STATE_NO_NETWORK = "no_network";
    /** 锚点上不是动力学方块（被换掉了 / 绑错了）。 */
    private static final String STATE_NOT_KINETIC = "not_kinetic";
    /** 这条线路上缺输入或缺输出。 */
    private static final String STATE_MISSING_IO = "missing_io";
    /** 源网络一点容量都给不出来。 */
    private static final String STATE_NO_POWER = "no_power";
    /** 源网络有富余，但按优先级轮到它时已经被别的输出分完了。 */
    private static final String STATE_NO_SHARE = "no_share";
    /** 目标网络已经有容量了（同一条线路上别的输出补进去的），这一个输出无需再注入。 */
    private static final String STATE_FED_ELSEWHERE = "fed_elsewhere";
    /** 目标网络自己就带着动力源，压根不需要外部补应力。 */
    private static final String STATE_SELF_FED = "self_fed";
    /** 目标网络已经有真实动力源。 */
    private static final String STATE_REAL_SOURCE = "real_source";
    /** 同一张目标网络已被另一条输出用别的转速驱动。 */
    private static final String STATE_SPEED_CONFLICT = "speed_conflict";
    /** 这个方块（或它所在网络）已经被另一条线路占用。 */
    private static final String STATE_SHARED = "shared";

    // ------------------------------------------------------------------ 字段

    /** 被桥驱动过的方块。 */
    private final Map<ClaimKey, Claim> claims = new HashMap<>();
    /** 本 tick 生效的虚拟容量：目标网络 → 贡献列表。 */
    private final Map<Long, List<StressGrant>> grants = new HashMap<>();
    /** 本 tick 回馈到源网络的负载：源网络 → 额外应力。 */
    private final Map<Long, Float> inputStress = new HashMap<>();
    /** 本 tick 各目标网络被驱动的转速；用来发现「同一网络两个转速」的冲突。 */
    private final Map<Long, Float> outputSpeed = new HashMap<>();
    /** 本 tick 碰过的网络对象（已经用本 tick 的贡献重算过）。 */
    private Map<Long, KineticNetwork> touched = new HashMap<>();
    /**
     * 上一 tick 碰过的网络。
     *
     * <p>这一 tick 若不再有虚拟贡献，就要在引擎跑完之后把它们重算一次，把多出来的容量退掉。</p>
     *
     * <p><b>退容量不能在 tick 开头做。</b>那时贡献是空的，网络会被判成过载（容量掉到真实值），
     * 而动力学把「过载状态翻转」也算作一次转速变化：{@code getSpeed()} 从 0 变回来 →
     * {@code onSpeedChanged} 里 {@code fromOrToZero} 成立 → {@code flickerTally += 5}。
     * 紧接着本 tick 又把贡献写回去、再翻转一次，于是<b>每 tick +10、衰减只 -1</b>，
     * 十几 tick 后 flickerScore 越过阈值，传播逻辑就会 {@code destroyBlock} 把方块打掉。
     * （实测症状：成排的动力合成器变成掉落物 —— 它们的应力占用是 2.0/RPM，网络一定会过载。）</p>
     */
    private Map<Long, KineticNetwork> pendingRelease = new HashMap<>();
    /** 界面用：暂停原因（键 = 网络 + 线路 + 锚点）。 */
    private final Map<String, String> pauseReasons = new HashMap<>();
    /** 界面用：源网络里有无限动力源的线路。 */
    private final Set<String> infiniteSupply = new HashSet<>();
    /** 界面用：每个端点的状态。 */
    private final Map<StatusKey, StaffLinkStressStatusPacket.Entry> status = new HashMap<>();
    /** 冲突提示节流。 */
    private final Map<String, Long> conflictNotices = new HashMap<>();
    private long lastTick = Long.MIN_VALUE;

    private StaffLinkStressState() {
    }

    // ------------------------------------------------------------------ 端点解析

    /**
     * 该坐标能不能当应力端点。
     *
     * <p>判据只有一条：那一格是动力学方块。转速与应力容量都是它的固有属性，
     * 不需要额外的能力注册。</p>
     *
     * @return 不是动力学方块（或区块未加载）时返回 {@code null}
     */
    @Nullable
    public Object resolveEndpoint(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof KineticBlockEntity entity ? entity : null;
    }

    // ------------------------------------------------------------------ 每 tick 的主流程

    /**
     * 跑一整条线路。
     *
     * <p>顺序是刻意的：<b>先判冲突、再驱动、最后才算容量与回馈</b>。因为「需要多少应力」
     * 取决于目标网络里都有哪些机器，而那些机器只有在网络被驱动起来之后才会挂上去；
     * 反过来先算容量就没得可算。</p>
     */
    public void applyRoute(MinecraftServer server, UUID networkId, int routeIndex,
                           List<StaffLinkRoute> releases, List<StaffLinkRoute> absorbs) {
        long tick = server.getTickCount();
        beginTick(tick);

        String prefix = networkId + ":" + routeIndex + ":";
        pauseReasons.keySet().removeIf(key -> key.startsWith(prefix));
        infiniteSupply.removeIf(key -> key.startsWith(prefix));
        status.keySet().removeIf(key -> key.networkId().equals(networkId) && key.route() == routeIndex);

        Map<GlobalPos, String> inputStates = new HashMap<>();
        Map<GlobalPos, String> outputStates = new HashMap<>();

        List<InputEndpoint> inputs = new ArrayList<>(releases.size());
        for (StaffLinkRoute release : releases) {
            KineticBlockEntity entity = kinetic(server, release.anchor());
            if (entity == null) {
                inputStates.put(release.anchor(), STATE_NOT_KINETIC);
            } else if (!entity.hasNetwork()) {
                inputStates.put(release.anchor(), STATE_NO_NETWORK);
            } else if (!hasAnySource(entity.getOrCreateNetwork())) {
                // 判据是「网络里有没有动力源」，<b>不是</b>「现在转不转」。
                //
                // 源网络自己过载时转速是 0，但它仍然是个合法的输入端：算出来的富余就是 0，
                // 目标端自然拿不到容量而停 —— 这正是「源不够时两边一起停」。
                // 若按「转不转」判，源一过载输入端就被判成闲置 → 回馈被撤 → 源恢复 →
                // 再被推过载，两边以 tick 为周期来回抖；而每次过载翻转都会给网络成员记一次
                // flicker，抖久了就会触发动力学那边的「方块变更太频繁」保护，把方块打掉。
                inputStates.put(release.anchor(), STATE_IDLE);
            } else {
                inputs.add(new InputEndpoint(entity, release));
            }
        }

        List<OutputEndpoint> outputs = new ArrayList<>(absorbs.size());
        for (StaffLinkRoute absorb : absorbs) {
            KineticBlockEntity entity = kinetic(server, absorb.anchor());
            if (entity == null) {
                outputStates.put(absorb.anchor(), STATE_NOT_KINETIC);
                continue;
            }
            float rpm = clampRpm(absorb.amount());
            boolean reverse = absorb.interval() == StaffLinkRoute.STRESS_COUNTER_CLOCKWISE;
            outputs.add(new OutputEndpoint(entity, absorb, reverse ? -rpm : rpm));
        }

        // ---- 源网络各自的「提供 / 消耗 / 可用」 ----
        //
        // 放在「有没有输出」的判定<b>之前</b>：这一步与输出无关，而只绑了源、还没绑目标时，
        // 界面也应该看得到源网络的真实状况 —— 那正是玩家判断「怎么只有这么点」的依据。
        //
        // 三个细节不能省：
        //  1. 容量要减掉自己上一轮加进去的虚拟容量（那部分不是源网络自己产的）；
        //  2. 负载要减掉自己上一轮回馈的那一份（否则「可用」会被自己压小，看起来凭空少一块）；
        //  3. 可用量用「容量 − 负载」而不是「容量」：源网络自己带的机器也要吃应力，
        //     只看容量等于把已经被占用的部分又借了一遍。
        Set<Long> infiniteNetworks = new HashSet<>();
        Map<Long, float[]> numbersByNetwork = new HashMap<>();
        Map<GlobalPos, float[]> inputLocal = new HashMap<>();
        float capacitySum = 0.0F;
        float consumedSum = 0.0F;
        float spare = 0.0F;
        for (InputEndpoint input : inputs) {
            KineticNetwork network = input.entity.getOrCreateNetwork();
            long id = networkIdOf(network, input.entity);
            if (hasInfiniteSource(network)) {
                infiniteNetworks.add(id);
                infiniteSupply.add(prefix + input.route.anchor());
            }
            float[] numbers = numbersByNetwork.get(id);
            if (numbers == null) {
                if (infiniteNetworks.contains(id)) {
                    numbers = new float[]{INFINITE_POWER, 0.0F, INFINITE_POWER};
                } else {
                    float capacity = Math.max(0.0F, network.calculateCapacity() - additionalCapacity(id));
                    float reflected = inputStress.getOrDefault(id, 0.0F);
                    float consumed = Math.max(0.0F, network.calculateStress() - reflected);
                    numbers = new float[]{capacity, consumed, Math.max(0.0F, capacity - consumed)};
                }
                numbersByNetwork.put(id, numbers);
                // 同一张网络只累加一次：四个锚点接在同一根轴上的话，它是「一张 32768 的网络」，
                // 不是「四张 8192 的网络」。
                capacitySum += numbers[0];
                consumedSum += numbers[1];
                spare += numbers[2];
            }
            // 「自身」= <b>这一台所在那张动力网络</b>的量，不是单块自己的。
            //
            // 动力合成器这类机器是「一片共用一个网络」的：单块 2.0 × 256 = 512 没有意义，
            // 玩家要的是「我这片装置一共吃多少」。源端同理 —— 看的是它所在网络能提供多少。
            // （同一张网络上绑了多个锚点时，它们显示的就是同一个数，这是刻意的。）
            inputLocal.put(input.route.anchor(), new float[]{numbers[0], 0.0F, 0.0F});
        }
        if (!infiniteNetworks.isEmpty()) {
            spare = INFINITE_POWER;
            capacitySum = INFINITE_POWER;
        }

        // 诊断：把每个输入网络的真实构成打出来（每 20 tick 一行）。
        //
        // Create 6.0.x 的应力口径和直觉不一样，出问题时光看汇总数根本定位不到：
        //   · `calculateStress()` 遍历 `members` 时**不跳过动力源**，而 `add()` 会把所有方块
        //     （含源）都放进 `members`，取值是**加入时缓存的** `calculateStressApplied()`；
        //   · `calculateCapacity()` 用 `sources` 里缓存的容量 × **`getGeneratedSpeed()`**；
        //   · 两者都还要加上 `unloadedStress` / `unloadedCapacity`（区块未加载的成员残值）。
        // 所以「谁贡献了多少」必须逐块打出来才能对上账。
        logNetworkBreakdown(inputs, routeIndex, spare);

        // 配置区状态行用<b>整条线路</b>汇总的三个数。
        //
        // 玩家想知道的是「这条无线链路总共能借出多少」。四台风车轴承各自成网时，只看单个锚点
        // 会显示 8192，让人以为总量只有一台的量；汇总后才是 32768，与「每台 8192、共四台」对得上。
        // 汇总出来的「可用」也正是下面分配时用的那个数，于是界面和实际行为不会再互相矛盾。
        float[] routeNumbers = {capacitySum, consumedSum, spare};

        Map<GlobalPos, float[]> outputNumbers = new HashMap<>();

        if (inputs.isEmpty() || outputs.isEmpty()) {
            releaseRoute(server, networkId, routeIndex);
            for (OutputEndpoint output : outputs) {
                outputStates.put(output.route.anchor(), STATE_MISSING_IO);
            }
            writeStatus(networkId, routeIndex, releases, absorbs, inputStates, outputStates,
                    inputLocal, routeNumbers, outputNumbers);
            return;
        }

        // ---- 逐个输出：先查冲突，没冲突才驱动 ----
        List<Drive> drives = new ArrayList<>(outputs.size());
        List<StressAllocationMath.OutputDemand> demands = new ArrayList<>(outputs.size());
        for (OutputEndpoint output : outputs) {
            String reason = conflictAt(output.entity, networkId, routeIndex,
                    output.route.anchor(), output.signedSpeed);
            if (!reason.isEmpty()) {
                // 有冲突就<b>不驱动</b>，并把这个方块从认领表里摘掉。
                // 硬驱动会让动力学去「抢源」，而抢不过的一方会被直接破坏掉。
                releaseClaim(server, networkId, routeIndex, output.route.anchor());
                pauseReasons.put(prefix + output.route.anchor(), reason);
                notifyConflict(server, networkId, reason, tick);
                outputStates.put(output.route.anchor(), reason);
                continue;
            }
            drive(output.entity, networkId, routeIndex, output.route.anchor(), output.signedSpeed, tick);
            KineticNetwork network = output.entity.getOrCreateNetwork();
            long networkId2 = networkIdOf(network, output.entity);
            outputSpeed.putIfAbsent(networkId2, output.signedSpeed);

            float impact = 0.0F;
            for (Float value : network.members.values()) {
                impact += Math.max(0.0F, value);
            }
            String key = output.route.anchor().toString();
            // 目标网络<b>自己</b>已有的容量（含本 tick 里先前已经喂给它的那一部分）。
            // 扣掉它才是「还需要我们补多少」—— 目标网络本来就有动力的话，我们不该再注入一份，
            // 那等于白占源网络的额度（回馈会把它算成源网络的负载）。
            float ownCapacity = network.calculateCapacity();
            float totalPower = StressAllocationMath.power(impact, output.signedSpeed);
            drives.add(new Drive(key, network, networkId2, output.route, output.signedSpeed));
            demands.add(new StressAllocationMath.OutputDemand(
                    key,
                    Math.max(0.0F, totalPower - ownCapacity),
                    totalPower,
                    output.route.weight(),
                    nearestInputDistance(inputs, output.route)));
        }

        if (drives.isEmpty()) {
            writeStatus(networkId, routeIndex, releases, absorbs, inputStates, outputStates,
                    inputLocal, routeNumbers, outputNumbers);
            return;
        }

        // ---- 按权重分配，并登记虚拟容量 ----
        Map<String, Float> allocated = new HashMap<>();
        for (StressAllocationMath.Allocation allocation : StressAllocationMath.allocate(demands, spare)) {
            allocated.put(allocation.key(), allocation.power());
        }
        Map<String, StressAllocationMath.OutputDemand> demandByKey = new HashMap<>();
        for (StressAllocationMath.OutputDemand demand : demands) {
            demandByKey.put(demand.key(), demand);
        }
        float granted = 0.0F;
        for (Drive drive : drives) {
            float power = allocated.getOrDefault(drive.key, 0.0F);
            granted += power;
            grants.computeIfAbsent(drive.networkId, id -> new ArrayList<>())
                    .add(new StressGrant(power, drive.signedSpeed));
            touched.put(drive.networkId, drive.network);
            StressAllocationMath.OutputDemand demand = demandByKey.get(drive.key);
            float networkCapacity = drive.network.calculateCapacity();
            outputStates.put(drive.route.anchor(), power > 0.0F ? STATE_OK : idleOutputState(
                    spare,
                    demand == null ? 0.0F : demand.requestedPower(),
                    networkCapacity,
                    additionalCapacity(drive.networkId) > 0.0F));
            // 「自身」= 这一台<b>所在那张动力网络</b>总共需要多少应力（整片装置的耗力合计），
            // 不是单块自己的 —— 见上面 inputLocal 处的说明。
            // 「网络总计 / 剩余」是整条无线线路的量，由 routeNumbers 统一给出，两处分工不同。
            outputNumbers.put(drive.route.anchor(), new float[]{
                    demand == null ? 0.0F : demand.totalPower(), 0.0F, 0.0F});
        }

        // 「剩余」= 这条链路可借总额 − 已经借出去的。
        // 要等上面登记完才知道借出去多少，所以在这里回填（「网络总计」那一格不动）。
        routeNumbers[2] = Math.max(0.0F, spare - granted);

        // ---- 回馈：源网络承担它<b>实际给出的</b>那一份 ----
        //
        // 刻意不是「目标需要多少就回馈多少」。回馈需求的话，目标的需求一旦超过源网络的富余，
        // 源网络就会被推过载、转速归零，于是下一轮输入端被判成「没在转」、回馈又撤掉、
        // 源网络恢复、再被推过载……两边会以 tick 为周期来回抖。
        // 只回馈实际给出的量，源网络最多被推到「刚好满载」，状态是稳定的：
        // 源不够时表现为<b>目标端过载停转</b>，而源网络自己的机器照常运行。
        float shared = granted / Math.max(1, inputs.size());
        for (InputEndpoint input : inputs) {
            KineticNetwork network = input.entity.getOrCreateNetwork();
            long id = networkIdOf(network, input.entity);
            inputStress.merge(id, shared, Float::sum);
            touched.put(id, network);
            // 源网络自己就没有富余（容量被它自己带的机器吃满）时如实报出来 ——
            // 否则玩家只看到「已连接」和一个 0，不知道卡在哪一步。
            inputStates.put(input.route.anchor(), spare > 0.0F ? STATE_OK : STATE_NO_POWER);
        }

        // ---- 让动力学按新的贡献重算容量与应力 ----
        for (Drive drive : drives) {
            drive.network.updateNetwork();
        }
        for (InputEndpoint input : inputs) {
            input.entity.getOrCreateNetwork().updateNetwork();
        }

        writeStatus(networkId, routeIndex, releases, absorbs, inputStates, outputStates,
                inputLocal, routeNumbers, outputNumbers);
    }

    /**
     * 摘掉已经过期的驱动认领。
     *
     * <p><b>必须每 tick 调用。</b>线路被关掉、被解绑、或者整张网络没了之后，
     * {@link #applyRoute} 就不会再来续期，只能靠这里把方块身上的虚拟转速清掉。</p>
     */
    public void sweep(MinecraftServer server) {
        releaseStaleContributions(server);
        if (claims.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        List<Claim> expired = new ArrayList<>();
        Iterator<Map.Entry<ClaimKey, Claim>> iterator = claims.entrySet().iterator();
        while (iterator.hasNext()) {
            Claim claim = iterator.next().getValue();
            if (claim.stale(now)) {
                iterator.remove();
                expired.add(claim);
            }
        }
        for (Claim claim : expired) {
            stopDriving(server, claim);
        }
    }

    /**
     * 把「上一轮借出去、这一轮不再借」的虚拟容量退掉。
     *
     * <p>放在 sweep（引擎跑完之后）而不是 tick 开头，是为了让本 tick 的新贡献先写好：
     * 仍然被借用的网络不会被动到，只有真正不再有贡献的网络才翻转一次过载状态。</p>
     */
    private void releaseStaleContributions(MinecraftServer server) {
        long now = server.getTickCount();
        // 本 tick 引擎压根没跑（应力线路被删光 / 网络不再活跃）：上一轮的贡献还挂着，
        // 一并退掉，否则那些网络会一直留着借来的容量。
        if (lastTick != now && !grants.isEmpty()) {
            grants.clear();
            inputStress.clear();
            for (KineticNetwork network : touched.values()) {
                if (network != null) {
                    network.updateNetwork();
                }
            }
            touched.clear();
        }
        if (pendingRelease.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, KineticNetwork> entry : pendingRelease.entrySet()) {
            if (grants.containsKey(entry.getKey())) {
                // 这一轮还在借用：留给下一轮再判。
                continue;
            }
            KineticNetwork network = entry.getValue();
            if (network != null) {
                network.updateNetwork();
            }
        }
        pendingRelease.clear();
    }

    // ------------------------------------------------------------------ 动力学侧的两个钩子

    /**
     * 补一个「临时认领」：方块上次退出前是无线驱动的，而认领表是运行时状态、重启后就没了。
     *
     * <p><b>为什么必须补。</b>认领丢了之后，这些方块在这一 tick 里会出现一种矛盾状态：
     * 身上还留着上次同步过去的转速（方块实体自己的 {@code speed} 存在存档里），
     * 但 {@code getGeneratedSpeed()} 因为查不到认领而返回 0 —— 于是动力学既不当它是动力源，
     * 又看到它在转。随后 {@code validateKinetics()} 会把它的转速清零、依赖它的邻居会去
     * {@code propagateMissingSource}，而传播逻辑把「同一张网络里转速不一致」判成环路，
     * 直接 {@code destroyBlock} 把方块打掉。实测症状：**重进游戏后成排的动力合成器两侧变掉落物**。</p>
     *
     * <p>补上之后它从第一 tick 起就仍然表现为动力源，网络状态与存档前一致；引擎随后会把它
     * 换成正式的认领。若这条线路已经不在了，临时认领会在 40 tick 后自然过期并被停掉。</p>
     */
    public void reseedClaim(KineticBlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        ClaimKey key = new ClaimKey(level.dimension().location(), entity.getBlockPos().asLong());
        if (claims.containsKey(key)) {
            return;
        }
        MinecraftServer server = level.getServer();
        long now = server == null ? 0L : server.getTickCount();
        claims.put(key, new Claim(null, -1, null, level.dimension().location(),
                entity.getBlockPos(), entity.getTheoreticalSpeed(), now, true));
    }

    /** 目标网络在本 tick 额外获得的容量。 */
    public float additionalCapacity(long networkId) {
        List<StressGrant> list = grants.get(networkId);
        if (list == null) {
            return 0.0F;
        }
        float total = 0.0F;
        for (StressGrant grant : list) {
            total += grant.suppliedCapacity();
        }
        return total;
    }

    /** 源网络在本 tick 额外承担的负载。 */
    public float additionalStress(long networkId) {
        return inputStress.getOrDefault(networkId, 0.0F);
    }

    /**
     * 这个方块是不是被桥驱动着的（也就是「它的转速是无线来的」）。
     *
     * <p>动力学每次询问方块的自身转速时都会走这里；返回非空就说明该方块应该以这个转速
     * 作为动力源参与网络，而不是按它方块类里写死的那个转速。</p>
     */
    public Optional<Float> wirelessSpeed(@Nullable KineticBlockEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        Level level = entity.getLevel();
        if (level == null) {
            return Optional.empty();
        }
        Claim claim = claims.get(new ClaimKey(level.dimension().location(), entity.getBlockPos().asLong()));
        if (claim == null || claim.released) {
            return Optional.empty();
        }
        return Optional.of(claim.speed);
    }

    // ------------------------------------------------------------------ 界面状态

    /** 下发给客户端的运行状态快照。 */
    public List<StaffLinkStressStatusPacket.Entry> entries() {
        return List.copyOf(status.values());
    }

    /** 服务器停止时清掉全部运行时状态。 */
    public void clearRuntimeState() {
        claims.clear();
        grants.clear();
        inputStress.clear();
        outputSpeed.clear();
        touched = new HashMap<>();
        pendingRelease = new HashMap<>();
        pauseReasons.clear();
        infiniteSupply.clear();
        status.clear();
        conflictNotices.clear();
        lastTick = Long.MIN_VALUE;
    }

    // ------------------------------------------------------------------ 内部：tick 边界

    private void beginTick(long tick) {
        if (lastTick == tick) {
            return;
        }
        lastTick = tick;
        // 只把集合换新，<b>不在这里重算任何网络</b>。原因见 pendingRelease 的注释：
        // 此刻贡献是空的，重算会把网络判成过载，翻转出来的 flicker 会累积到把方块打掉。
        // 真正该退的容量交给 sweep()：那时本 tick 的新贡献已经写好，仍被借用的网络不会被动到。
        pendingRelease = touched;
        touched = new HashMap<>();
        grants.clear();
        inputStress.clear();
        outputSpeed.clear();
    }

    // ------------------------------------------------------------------ 内部：驱动与释放

    private void drive(KineticBlockEntity entity, UUID networkId, int routeIndex,
                       GlobalPos anchor, float signedSpeed, long tick) {
        Level level = entity.getLevel();
        if (level == null) {
            return;
        }
        ClaimKey key = new ClaimKey(level.dimension().location(), entity.getBlockPos().asLong());
        Claim claim = claims.get(key);
        if (claim == null || claim.restored) {
            // 临时认领（重启后补的）没有真正的归属，第一次被驱动时直接换成正式的。
            claims.put(key, new Claim(networkId, routeIndex, anchor, level.dimension().location(),
                    entity.getBlockPos(), signedSpeed, tick));
        } else {
            claim.speed = signedSpeed;
            claim.lastSeen = tick;
            claim.released = false;
        }

        if (level.isClientSide) {
            return;
        }
        float previous = entity.getTheoreticalSpeed();
        if (!entity.hasNetwork() || Math.abs(previous - signedSpeed) > SPEED_EPSILON) {
            // 转速变了（或原本不在网络里）：先脱离旧的传播关系，再按新转速重新接入。
            if (Math.abs(previous) > 1.0E-4F) {
                entity.detachKinetics();
            }
            entity.setSpeed(signedSpeed);
            if (!entity.hasNetwork()) {
                entity.setNetwork(entity.getBlockPos().asLong());
            }
            entity.attachKinetics();
            entity.onSpeedChanged(previous);
            entity.sendData();
        }
    }

    private void releaseClaim(MinecraftServer server, UUID networkId, int routeIndex, GlobalPos anchor) {
        Claim claim = null;
        Iterator<Map.Entry<ClaimKey, Claim>> iterator = claims.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ClaimKey, Claim> entry = iterator.next();
            if (entry.getValue().owns(networkId, routeIndex, anchor)) {
                claim = entry.getValue();
                iterator.remove();
                break;
            }
        }
        if (claim != null) {
            stopDriving(server, claim);
        }
    }

    private void releaseRoute(MinecraftServer server, UUID networkId, int routeIndex) {
        List<Claim> released = new ArrayList<>();
        Iterator<Map.Entry<ClaimKey, Claim>> iterator = claims.entrySet().iterator();
        while (iterator.hasNext()) {
            Claim claim = iterator.next().getValue();
            if (claim.ownsRoute(networkId, routeIndex)) {
                iterator.remove();
                released.add(claim);
            }
        }
        for (Claim claim : released) {
            stopDriving(server, claim);
        }
    }

    private void stopDriving(MinecraftServer server, Claim claim) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, claim.dimension));
        if (level == null || !level.isLoaded(claim.pos)) {
            return;
        }
        if (level.getBlockEntity(claim.pos) instanceof KineticBlockEntity entity) {
            stopDriving(level, entity);
        }
    }

    private void stopDriving(Level level, KineticBlockEntity entity) {
        if (level.isClientSide) {
            return;
        }
        if (entity.hasNetwork()) {
            // 这张网络里如果还有别的动力源（真实源，或别的线路驱动着的方块），
            // 就不该由我们把转速清零——那会把别人的动力一起掐掉。
            for (KineticBlockEntity source : entity.getOrCreateNetwork().sources.keySet()) {
                if (source != entity && isPresent(source)
                        && !claims.containsKey(new ClaimKey(
                                source.getLevel().dimension().location(), source.getBlockPos().asLong()))) {
                    return;
                }
            }
        }
        float previous = entity.getTheoreticalSpeed();
        if (previous != 0.0F || entity.hasNetwork()) {
            if (previous != 0.0F) {
                entity.detachKinetics();
            }
            entity.setSpeed(0.0F);
            entity.setNetwork(null);
            entity.onSpeedChanged(previous);
            entity.sendData();
        }
    }

    // ------------------------------------------------------------------ 内部：冲突判定

    /**
     * 这个输出端能不能驱动。
     *
     * <p>三种情况必须让位，否则动力学那边会去「抢源」，抢不过的一方会被直接破坏掉：</p>
     * <ul>
     *   <li>目标网络里已经有<b>真实</b>动力源（既不是无限动力源，也不是被桥驱动的方块）；</li>
     *   <li>同一张目标网络已经被另一条输出用<b>不同转速</b>驱动；</li>
     *   <li>这个方块或它所在网络已经被<b>别的线路</b>占用。</li>
     * </ul>
     *
     * @return 空串表示可以驱动，否则是状态码
     */
    private String conflictAt(KineticBlockEntity entity, UUID networkId, int routeIndex,
                              GlobalPos anchor, float signedSpeed) {
        Level level = entity.getLevel();
        if (level == null) {
            return STATE_NOT_KINETIC;
        }
        Claim own = claims.get(new ClaimKey(level.dimension().location(), entity.getBlockPos().asLong()));
        if (own != null && !own.owns(networkId, routeIndex, anchor)) {
            return STATE_SHARED;
        }
        if (!entity.hasNetwork()) {
            return STATE_OK;
        }
        KineticNetwork network = entity.getOrCreateNetwork();
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (!isPresent(source)) {
                continue;
            }
            Level sourceLevel = source.getLevel();
            if (sourceLevel == null) {
                continue;
            }
            Claim claim = claims.get(new ClaimKey(
                    sourceLevel.dimension().location(), source.getBlockPos().asLong()));
            if (claim == null) {
                if (!isInfiniteSource(source)) {
                    return STATE_REAL_SOURCE;
                }
            } else if (!claim.owns(networkId, routeIndex, anchor)) {
                return STATE_SHARED;
            }
        }

        Float claimed = outputSpeed.get(networkIdOf(network, entity));
        if (claimed == null) {
            return STATE_OK;
        }
        return Math.abs(claimed - signedSpeed) > SPEED_EPSILON ? STATE_SPEED_CONFLICT : STATE_SHARED;
    }

    private void notifyConflict(MinecraftServer server, UUID networkId, String reason, long tick) {
        String key = networkId + ":" + reason;
        Long previous = conflictNotices.get(key);
        if (previous != null && tick - previous < CONFLICT_NOTICE_INTERVAL) {
            return;
        }
        conflictNotices.put(key, tick);
        String messageKey = switch (reason) {
            case STATE_REAL_SOURCE -> "gui.useless_mod.wireless_logistics.stress.chat.real_source";
            case STATE_SPEED_CONFLICT -> "gui.useless_mod.wireless_logistics.stress.chat.speed_conflict";
            case STATE_SHARED -> "gui.useless_mod.wireless_logistics.stress.chat.shared";
            default -> "gui.useless_mod.wireless_logistics.stress.chat.conflict";
        };
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (StaffLinkManager.canAccess(server, player, networkId)) {
                player.displayClientMessage(Component.translatable(messageKey), true);
            }
        }
    }

    // ------------------------------------------------------------------ 内部：工具

    /**
     * 写界面用的状态条目。
     *
     * @param inputLocal    按锚点索引的「本锚点所在网络」三个数：提供 / 消耗 / 可用
     * @param routeNumbers  整条线路源侧汇总的三个数，同上
     * @param outputNumbers 按锚点索引的「注入 / 需求 / 容量」
     */
    private void writeStatus(UUID networkId, int routeIndex,
                             List<StaffLinkRoute> releases, List<StaffLinkRoute> absorbs,
                             Map<GlobalPos, String> inputStates, Map<GlobalPos, String> outputStates,
                             Map<GlobalPos, float[]> inputLocal, float[] routeNumbers,
                             Map<GlobalPos, float[]> outputNumbers) {
        StaffLinkStressStatusPacket.Numbers route = numbers(routeNumbers);
        for (StaffLinkRoute release : releases) {
            String state = inputStates.get(release.anchor());
            status.put(new StatusKey(networkId, release.anchor(), routeIndex),
                    new StaffLinkStressStatusPacket.Entry(networkId, release.anchor(), routeIndex, true,
                            numbers(inputLocal.get(release.anchor())), route,
                            state == null ? STATE_OK : state));
        }
        for (StaffLinkRoute absorb : absorbs) {
            String state = outputStates.get(absorb.anchor());
            // 「网络总计 / 剩余」是<b>整条线路</b>的量，吸收端同样要看 —— 它回答的是
            // 「这条链路能给我多少、还剩多少」，与本机自己需要多少（local）是两回事。
            status.put(new StatusKey(networkId, absorb.anchor(), routeIndex),
                    new StaffLinkStressStatusPacket.Entry(networkId, absorb.anchor(), routeIndex, false,
                            numbers(outputNumbers.get(absorb.anchor())), route,
                            state == null ? STATE_OK : state));
        }
    }

    private static StaffLinkStressStatusPacket.Numbers numbers(@Nullable float[] values) {
        return values == null
                ? StaffLinkStressStatusPacket.Numbers.ZERO
                : new StaffLinkStressStatusPacket.Numbers(values[0], values[1], values[2]);
    }

    /**
     * 某个输出端这一份「没注入」时，给出真正的原因。
     *
     * <p>判据是<b>目标网络够不够用</b>，而不是「我们这一份有没有给出去」。同一个网络上绑了多个
     * 输出时，只有第一个会拿到额度、后面的都是 0，但机器照样在转 —— 那时候报「故障」是错的。
     * 同理，额度也可能是同一条线路上别的输出补进去的，说成「别人供电」也不对。</p>
     *
     * @param spare        源侧汇总出来的可用量
     * @param shortfall    这个输出端还需要补多少（扣掉目标网络自有容量之后的缺口）
     * @param capacity     目标网络加总后的容量（含已经喂给它的那一部分）
     * @param wirelessFed  目标网络当前的容量里有没有我们（任意一条线路）补进去的那一份
     */
    private static String idleOutputState(float spare, float shortfall, float capacity, boolean wirelessFed) {
        if (shortfall <= 0.0F) {
            // 目标网络已经够了，这一份压根不需要注入。
            if (capacity <= 0.0F) {
                return STATE_OK;
            }
            return wirelessFed ? STATE_FED_ELSEWHERE : STATE_SELF_FED;
        }
        if (spare <= 0.0F) {
            // 源网络自己就满了：容量被它自己带的机器吃干净，没有富余可以借出去。
            return STATE_NO_POWER;
        }
        return STATE_NO_SHARE;
    }

    private static double nearestInputDistance(List<InputEndpoint> inputs, StaffLinkRoute output) {
        double best = Double.MAX_VALUE;
        for (InputEndpoint input : inputs) {
            if (!input.route.anchor().dimension().equals(output.anchor().dimension())) {
                continue;
            }
            best = Math.min(best, input.route.anchor().pos().distSqr(output.anchor().pos()));
        }
        return best == Double.MAX_VALUE ? 0.0 : best;
    }

    @Nullable
    private static KineticBlockEntity kinetic(MinecraftServer server, GlobalPos anchor) {
        ServerLevel level = server.getLevel(anchor.dimension());
        if (level == null || !level.isLoaded(anchor.pos())) {
            return null;
        }
        return level.getBlockEntity(anchor.pos()) instanceof KineticBlockEntity entity ? entity : null;
    }

    private static long networkIdOf(KineticNetwork network, KineticBlockEntity fallback) {
        return network.id == null ? fallback.getBlockPos().asLong() : network.id;
    }

    private static boolean isPresent(KineticBlockEntity entity) {
        Level level = entity.getLevel();
        return level != null && level.getBlockEntity(entity.getBlockPos()) == entity;
    }

    private static boolean isInfiniteSource(KineticBlockEntity entity) {
        return INFINITE_SOURCE_BLOCK.equals(BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()));
    }

    private static boolean hasInfiniteSource(KineticNetwork network) {
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (isPresent(source) && isInfiniteSource(source)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这张网络里有没有（还在世界里的）动力源。没有源就根本不会转，也就没东西可借。
     */
    private static boolean hasAnySource(KineticNetwork network) {
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (isPresent(source)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 诊断日志：逐块打印输入网络的构成，用来核对「提供 / 消耗」到底是谁贡献的。
     *
     * <p>每 20 tick 一次、每条线路一行，开销可以忽略；排查这类「数值对不上」的问题时，
     * 没有它就只能在代码里猜。</p>
     */
    private void logNetworkBreakdown(List<InputEndpoint> inputs, int routeIndex, float spare) {
        if (inputs.isEmpty()) {
            return;
        }
        KineticBlockEntity probe = inputs.get(0).entity;
        Level level = probe.getLevel();
        if (level == null || level.getServer() == null
                || level.getServer().getTickCount() % 20 != 0) {
            return;
        }
        Set<Long> seen = new HashSet<>();
        StringBuilder report = new StringBuilder();
        for (InputEndpoint input : inputs) {
            KineticNetwork network = input.entity.getOrCreateNetwork();
            long id = networkIdOf(network, input.entity);
            if (!seen.add(id)) {
                continue;
            }
            float presentCapacity = 0.0F;
            for (KineticBlockEntity source : network.sources.keySet()) {
                float contribution = source.calculateAddedStressCapacity()
                        * Math.abs(source.getGeneratedSpeed());
                presentCapacity += contribution;
                report.append("\n    SRC ").append(blockName(source))
                        .append(' ').append(source.getBlockPos().toShortString())
                        .append(" gen=").append(source.getGeneratedSpeed())
                        .append(" capNow=").append(source.calculateAddedStressCapacity())
                        .append(" -> ").append(contribution);
            }
            float presentStress = 0.0F;
            for (Map.Entry<KineticBlockEntity, Float> member : network.members.entrySet()) {
                KineticBlockEntity entity = member.getKey();
                float contribution = member.getValue() * Math.abs(entity.getTheoreticalSpeed());
                presentStress += contribution;
                report.append("\n    MEM ").append(blockName(entity))
                        .append(' ').append(entity.getBlockPos().toShortString())
                        .append(" theo=").append(entity.getTheoreticalSpeed())
                        .append(" cached=").append(member.getValue())
                        .append(" -> ").append(contribution);
            }
            float totalCapacity = network.calculateCapacity();
            float totalStress = network.calculateStress();
            report.append("\n  net ").append(id)
                    .append(" cap=").append(totalCapacity)
                    .append("(present=").append(presentCapacity)
                    .append(" orphan=").append(totalCapacity - presentCapacity).append(')')
                    .append(" stress=").append(totalStress)
                    .append("(present=").append(presentStress)
                    .append(" orphan=").append(totalStress - presentStress).append(')')
                    .append(" addCap=").append(additionalCapacity(id))
                    .append(" addStress=").append(additionalStress(id));
        }
        UselessMod.LOGGER.info("[stafflink-stress] route {} spare={}{}", routeIndex, spare, report);
    }

    private static String blockName(KineticBlockEntity entity) {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()));
    }

    /** 目标转速的夹取：至少 1 RPM，至多取动力学配置里的最高转速。 */
    private static float clampRpm(long amount) {
        float max = maxRotationSpeed();
        return Math.max(1.0F, Math.min(max, (float) Math.abs(amount)));
    }

    private static float maxRotationSpeed() {
        try {
            return Math.max(1.0F, AllConfigs.server().kinetics.maxRotationSpeed.get());
        } catch (RuntimeException | LinkageError ignored) {
            // 配置还没读起来（或读不到）时给一个保守值，宁可夹紧也不能放出去一个离谱转速。
            return 256.0F;
        }
    }

    // ------------------------------------------------------------------ 内部：数据结构

    /** 认领的键：维度 + 坐标。光用坐标会让不同维度的同一格互相顶掉。 */
    private record ClaimKey(ResourceLocation dimension, long pos) {
    }

    private record StatusKey(UUID networkId, GlobalPos anchor, int route) {
    }

    /**
     * 给某个目标网络补的一份虚拟容量。
     *
     * @param suppliedCapacity 这一份贡献多少应力容量
     * @param targetSpeed      该网络被驱动的转速（记录用，方便排查）
     */
    private record StressGrant(float suppliedCapacity, float targetSpeed) {
    }

    private record InputEndpoint(KineticBlockEntity entity, StaffLinkRoute route) {
    }

    private record OutputEndpoint(KineticBlockEntity entity, StaffLinkRoute route, float signedSpeed) {
    }

    private record Drive(String key, KineticNetwork network, long networkId,
                         StaffLinkRoute route, float signedSpeed) {
    }

    /** 一处被桥驱动着的方块。 */
    private static final class Claim {
        private final UUID networkId;
        private final int routeIndex;
        private final GlobalPos anchor;
        private final ResourceLocation dimension;
        private final BlockPos pos;
        private float speed;
        private long lastSeen;
        private boolean released;
        /**
         * 「临时认领」：重启后按存档里的转速补出来的，还没有真正的线路归属。
         *
         * <p>它不跟任何线路冲突（{@link #owns} 恒真），也不会被解绑/释放逻辑摘掉
         * （{@link #ownsRoute} 恒假）；引擎第一次驱动它时会被替换成正式认领，
         * 若一直没人认领就会像普通认领一样过期、被停掉。</p>
         */
        private boolean restored;

        private Claim(UUID networkId, int routeIndex, GlobalPos anchor, ResourceLocation dimension,
                      BlockPos pos, float speed, long lastSeen) {
            this(networkId, routeIndex, anchor, dimension, pos, speed, lastSeen, false);
        }

        private Claim(UUID networkId, int routeIndex, GlobalPos anchor, ResourceLocation dimension,
                      BlockPos pos, float speed, long lastSeen, boolean restored) {
            this.networkId = networkId;
            this.routeIndex = routeIndex;
            this.anchor = anchor;
            this.dimension = dimension;
            this.pos = pos;
            this.speed = speed;
            this.lastSeen = lastSeen;
            this.restored = restored;
        }

        private boolean ownsRoute(UUID networkId, int routeIndex) {
            // 临时认领不属于任何线路：解绑/释放逻辑摘不到它，只能等它自己过期。
            if (restored) {
                return false;
            }
            return this.networkId.equals(networkId) && this.routeIndex == routeIndex;
        }

        private boolean owns(UUID networkId, int routeIndex, GlobalPos anchor) {
            // 临时认领也不跟任何人冲突 —— 它只代表「这个方块上次是被无线驱动的」这个事实。
            if (restored) {
                return true;
            }
            return ownsRoute(networkId, routeIndex) && this.anchor.equals(anchor);
        }

        private boolean stale(long now) {
            return released || now - lastSeen >= CLAIM_EXPIRY_TICKS;
        }
    }
}
