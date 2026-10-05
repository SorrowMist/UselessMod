package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongPressureHandler;
import com.wintercogs.appliedpneumatics.common.me.keys.AirKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AePressureBridge} 的 Applied Pneumatics 实现。
 *
 * <p><b>只有本类（以及它引用的 AE2 / Applied Pneumatics 类）会在两个模组都装了时被加载</b>：
 * 常驻代码碰到的永远是 {@link AePressureBridge} 那个不含模组类型的接口。</p>
 *
 * <h2>为什么「AE 气压」要靠 Applied Pneumatics</h2>
 *
 * <p>AE2 自身没有「把空气当资源存进网络」这回事，Applied Pneumatics 把气动工艺的空气建模成
 * {@link AirKey}（{@code appliedpneumatics:air_key}），<b>键的数量就是空气量（mL）</b>，
 * 走的就是 ME 存储那套 long 契约的 {@code extract} / {@code insert}。结构与
 * {@link AeEnergyCompat}（AppliedFlux 的 {@code FluxKey}）完全同源。</p>
 *
 * <p>判定「是不是端点」复用 {@link AeNetworks#isEndpoint}：只要方块注册了 AE2 的网格节点宿主
 * 能力即可，无线访问点、ME 接口、Applied Pneumatics 的 ME 气压接口都能绑。</p>
 */
public final class AePressureCompat implements AePressureBridge {

    /** AE2 的动作源：无线物流没有玩家或机器作为发起者，用空源即可。 */
    private static volatile IActionSource cachedSource;

    private static IActionSource source() {
        IActionSource local = cachedSource;
        if (local == null) {
            local = IActionSource.empty();
            cachedSource = local;
        }
        return local;
    }

    @Override
    public boolean isEndpoint(Level level, BlockPos pos) {
        return AeNetworks.isEndpoint(level, pos);
    }

    @Override
    @Nullable
    public LongPressureHandler pressureEndpoint(Level level, BlockPos pos) {
        return isEndpoint(level, pos) ? new AirEndpoint(level, pos) : null;
    }

    /** 写入被拒时的限流警告：每 {@link #WARN_INTERVAL_MS} 毫秒最多一条。 */
    private static final long WARN_INTERVAL_MS = 5000L;
    private static final AtomicLong LAST_WARN_AT = new AtomicLong();

    private static void warnRejected(BlockPos pos, String reason) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN_AT.get();
        if (now - last < WARN_INTERVAL_MS || !LAST_WARN_AT.compareAndSet(last, now)) {
            return;
        }
        UselessMod.LOGGER.warn("无线物流：AE 气压端点 {} 无法接收空气——{}", pos.toShortString(), reason);
    }

    /** ME 网络里空气（Applied Pneumatics 的 AirKey）的视角。 */
    private static final class AirEndpoint implements LongPressureHandler {
        private final Level level;
        private final BlockPos pos;

        AirEndpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        private static AEKey airKey() {
            return AirKey.INSTANCE;
        }

        @Override
        public long extract(long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                // 取不到网络：表现为「空容器」，抽不出即可。不必报警——掉电/掉线是暂时的。
                return 0L;
            }
            return Math.max(0L, storage.extract(airKey(), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source()));
        }

        @Override
        public long receive(long amount, boolean simulate) {
            if (amount <= 0L) {
                return 0L;
            }
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                warnRejected(pos, "该方块当前取不到 ME 网络（没有挂上网格，或节点尚未就绪）");
                return 0L;
            }
            long inserted = storage.insert(airKey(), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (inserted <= 0L) {
                warnRejected(pos, "ME 网络没有收下这些空气：网里没有空气存储"
                        + "（需要 Applied Pneumatics 的气体存储元件），或者已经存满了");
            }
            return Math.max(0L, inserted);
        }

        @Override
        public long stored() {
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return 0L;
            }
            return Math.max(0L, storage.getAvailableStacks().get(airKey()));
        }

        @Override
        public long capacity() {
            // ME 网络对每一种资源都没有「容量」这个概念，取 long 上限表示不设限。
            return Long.MAX_VALUE;
        }

        @Override
        public long volume() {
            // 网络里只有一堆空气，没有容器体积 ⇒ 没有压力概念，目标气压对它无意义。
            return 0L;
        }
    }
}
