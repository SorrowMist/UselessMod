package com.sorrowmist.useless.content.stafflink;

import com.sorrowmist.useless.world.stafflink.StaffLinkManager;
import com.sorrowmist.useless.world.stafflink.StaffLinkNetwork;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 「被无线物流绑定的气压位置」集合。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>气动工艺的方块在「某个面没接空气处理器」时会持续向环境漏气
 * （{@code MachineAirHandler#handleAirLeak} 每 tick 销毁 {@code 压力 × 40 + 20} mL）。
 * 典型受害者是压力室气阀：它只在朝外那一面真的挂着 {@code AIR_HANDLER_MACHINE} 能力时才认为自己是密封的。
 * 无线物流的注入只是 {@code addAir(...)}，不会建立这样一个邻居，所以方块永远认为自己漏气。</p>
 *
 * <p>本集合登记「哪些坐标被无线物流的气压线路绑着」，由
 * {@code mixin/pneumaticcraft/MachineAirHandlerMixin} 在空气处理器 tick 的开头查询，
 * 命中就把漏气状态清掉 —— 即<b>被无线连接的方块视同"已经接上了空气处理器"</b>。</p>
 *
 * <h2>线程与生命周期</h2>
 *
 * <p>只在<b>服务端线程</b>读写：重建由 {@link StaffLinkEngine#tick} 驱动，查询来自服务端方块实体 tick。
 * mixin 侧先判 {@code level.isClientSide} 再碰本类，保证客户端线程（单人存档下与服务端不同线程）
 * 永远不会读到正在被重建的集合。</p>
 */
public final class PressureSealRegistry {

    /** 重建间隔；绑定 / 解绑会通过 {@link #invalidate()} 立即触发一次。 */
    private static final int REBUILD_INTERVAL = 20;

    /** 按维度分组的位置集合；用 long 打包坐标避免装箱。 */
    private static final Map<ResourceKey<Level>, LongOpenHashSet> SEALED = new HashMap<>();

    /** 是否有待生效的改动（绑定 / 解绑 / 改配置）。 */
    private static boolean dirty = true;

    private static long lastRebuildTick = Long.MIN_VALUE;

    private PressureSealRegistry() {
    }

    /**
     * 当前有没有任何位置被封堵。
     *
     * <p>这是给 mixin 的<b>快路径</b>：绝大多数存档里一条气压线路都没有，此时每个空气处理器的
     * tick 只需付一次 {@code Map#isEmpty()}。</p>
     */
    public static boolean isEmpty() {
        return SEALED.isEmpty();
    }

    /** 该坐标是否被无线物流的气压线路绑着。 */
    public static boolean isSealed(Level level, BlockPos pos) {
        LongOpenHashSet positions = SEALED.get(level.dimension());
        return positions != null && positions.contains(pos.asLong());
    }

    /**
     * 重建封堵集合，必要时才真做。
     *
     * <p><b>全量重建</b>而不是增量维护：增量要和绑定 / 解绑 / 启停 / 换方块四条路径保持一致，
     * 漏一条就会留下"幽灵封堵"；而全量重建只有 O(总路由数)，代价可以忽略。</p>
     *
     * <p><b>判据跟随引擎</b>：归属者本局没人在线的孤儿网络不跑搬运，因此也不封
     * （见 {@link StaffLinkManager#isLive})。</p>
     */
    public static void rebuild(List<StaffLinkNetwork> networks, long now) {
        if (!dirty && now - lastRebuildTick < REBUILD_INTERVAL) {
            return;
        }
        dirty = false;
        lastRebuildTick = now;
        SEALED.clear();
        for (StaffLinkNetwork network : networks) {
            if (!StaffLinkManager.isLive(network.id())) {
                continue;
            }
            for (StaffLinkRoute route : network.routes()) {
                if (!route.enabled()) {
                    continue;
                }
                // 只要方块形态的 PRESSURE：AE_PRESSURE 的锚点必然是 AE 方块 / 访问点，
                // 不可能是气动方块，收进来只会多几个永远命不中的位置。
                if (route.medium() != LinkMedium.PRESSURE) {
                    continue;
                }
                if (!route.medium().isSupported()) {
                    continue;
                }
                // 释放端与吸收端都算「被无线连接」。
                SEALED.computeIfAbsent(route.anchor().dimension(), key -> new LongOpenHashSet())
                        .add(route.anchor().pos().asLong());
            }
        }
    }

    /** 绑定 / 解绑 / 改配置后置脏，让下一次 tick 立刻重建（而不是等满一个重建周期）。 */
    public static void invalidate() {
        dirty = true;
    }

    /** 服务器停止时清空，别把上一局的封堵带进下一局。 */
    public static void clear() {
        SEALED.clear();
        dirty = true;
        lastRebuildTick = Long.MIN_VALUE;
    }
}
