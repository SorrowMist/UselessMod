package com.sorrowmist.useless.compat.create;

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

        if (inputs.isEmpty() || outputs.isEmpty()) {
            releaseRoute(server, networkId, routeIndex);
            for (OutputEndpoint output : outputs) {
                outputStates.put(output.route.anchor(), STATE_MISSING_IO);
            }
            writeStatus(networkId, routeIndex, releases, absorbs, inputStates, outputStates, 0.0F, 0.0F);
            return;
        }

        // ---- 源网络里有没有「无限动力源」 ----
        Set<Long> infiniteNetworks = new HashSet<>();
        for (InputEndpoint input : inputs) {
            KineticNetwork network = input.entity.getOrCreateNetwork();
            if (hasInfiniteSource(network)) {
                infiniteNetworks.add(networkIdOf(network, input.entity));
                infiniteSupply.add(prefix + input.route.anchor());
            }
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
            drives.add(new Drive(key, network, networkId2, output.route, output.signedSpeed));
            demands.add(new StressAllocationMath.OutputDemand(
                    key,
                    StressAllocationMath.power(impact, output.signedSpeed),
                    output.route.weight(),
                    nearestInputDistance(inputs, output.route)));
        }

        if (drives.isEmpty()) {
            writeStatus(networkId, routeIndex, releases, absorbs, inputStates, outputStates, 0.0F, 0.0F);
            return;
        }

        // ---- 源网络还有多少<b>富余</b>应力可以借出去 ----
        //
        // 两个细节都不能省：
        //  1. 用「容量 − 应力」而不是「容量」：源网络自己带着的机器也要吃应力，只看容量
        //     等于把已经被占用的部分又借了一遍。
        //  2. 把自己上一轮回馈进去的那一份加回来：那一份本来就是我们自己造成的，
        //     不减掉它的话，第二 tick 算出来的富余会凭空少一块，两边来回跳。
        float spare = 0.0F;
        boolean infinite = false;
        Set<Long> counted = new HashSet<>();
        for (InputEndpoint input : inputs) {
            KineticNetwork network = input.entity.getOrCreateNetwork();
            long id = networkIdOf(network, input.entity);
            if (!counted.add(id)) {
                continue;
            }
            if (infiniteNetworks.contains(id)) {
                infinite = true;
                continue;
            }
            float reflected = inputStress.getOrDefault(id, 0.0F);
            spare += Math.max(0.0F, network.calculateCapacity() - network.calculateStress() + reflected);
        }
        if (infinite) {
            spare = INFINITE_POWER;
        }

        float requested = 0.0F;
        for (StressAllocationMath.OutputDemand demand : demands) {
            requested += demand.requestedPower();
        }

        // ---- 按权重分配，并登记虚拟容量 ----
        Map<String, Float> allocated = new HashMap<>();
        for (StressAllocationMath.Allocation allocation : StressAllocationMath.allocate(demands, spare)) {
            allocated.put(allocation.key(), allocation.power());
        }
        float granted = 0.0F;
        for (Drive drive : drives) {
            float power = allocated.getOrDefault(drive.key, 0.0F);
            granted += power;
            grants.computeIfAbsent(drive.networkId, id -> new ArrayList<>())
                    .add(new StressGrant(power, drive.signedSpeed));
            touched.put(drive.networkId, drive.network);
            outputStates.put(drive.route.anchor(), power > 0.0F ? STATE_OK : STATE_NO_POWER);
        }

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
            inputStates.put(input.route.anchor(), STATE_OK);
        }

        // ---- 让动力学按新的贡献重算容量与应力 ----
        for (Drive drive : drives) {
            drive.network.updateNetwork();
        }
        for (InputEndpoint input : inputs) {
            input.entity.getOrCreateNetwork().updateNetwork();
        }

        writeStatus(networkId, routeIndex, releases, absorbs, inputStates, outputStates, spare, requested);
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
        if (claim == null) {
            claims.put(key, new Claim(networkId, routeIndex, anchor,
                    level.dimension().location(), entity.getBlockPos(), signedSpeed, tick));
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

    private void writeStatus(UUID networkId, int routeIndex,
                             List<StaffLinkRoute> releases, List<StaffLinkRoute> absorbs,
                             Map<GlobalPos, String> inputStates, Map<GlobalPos, String> outputStates,
                             float available, float requested) {
        for (StaffLinkRoute release : releases) {
            String state = inputStates.get(release.anchor());
            status.put(new StatusKey(networkId, release.anchor(), routeIndex),
                    new StaffLinkStressStatusPacket.Entry(networkId, release.anchor(), routeIndex,
                            true, available, requested, state == null ? STATE_OK : state));
        }
        for (StaffLinkRoute absorb : absorbs) {
            String state = outputStates.get(absorb.anchor());
            status.put(new StatusKey(networkId, absorb.anchor(), routeIndex),
                    new StaffLinkStressStatusPacket.Entry(networkId, absorb.anchor(), routeIndex,
                            false, available, requested, state == null ? STATE_OK : state));
        }
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

    /** 这张网络里有没有（还在世界里的）动力源。没有源就根本不会转，也就没东西可借。 */
    private static boolean hasAnySource(KineticNetwork network) {
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (isPresent(source)) {
                return true;
            }
        }
        return false;
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

        private Claim(UUID networkId, int routeIndex, GlobalPos anchor, ResourceLocation dimension,
                      BlockPos pos, float speed, long lastSeen) {
            this.networkId = networkId;
            this.routeIndex = routeIndex;
            this.anchor = anchor;
            this.dimension = dimension;
            this.pos = pos;
            this.speed = speed;
            this.lastSeen = lastSeen;
        }

        private boolean ownsRoute(UUID networkId, int routeIndex) {
            return this.networkId.equals(networkId) && this.routeIndex == routeIndex;
        }

        private boolean owns(UUID networkId, int routeIndex, GlobalPos anchor) {
            return ownsRoute(networkId, routeIndex) && this.anchor.equals(anchor);
        }

        private boolean stale(long now) {
            return released || now - lastSeen >= CLAIM_EXPIRY_TICKS;
        }
    }
}
