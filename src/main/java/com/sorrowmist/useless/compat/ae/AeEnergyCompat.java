package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AeEnergyBridge} 的 AppliedFlux 实现。
 *
 * <p><b>只有本类（以及它引用的 AE2 / AppliedFlux 类）会在两个模组都装了时被加载</b>：
 * 常驻代码碰到的永远是 {@link AeEnergyBridge} 那个不含模组类型的接口。</p>
 *
 * <h2>为什么「AE 能量」要靠 AppliedFlux</h2>
 *
 * <p>AE2 自身没有「把 FE 当资源存进网络」这回事，通量元件是 AppliedFlux 加的。它把 FE 存成
 * {@link FluxKey}（{@link EnergyType#FE}），<b>键的数量就是 FE 数量</b>，走的就是 ME 存储那套
 * long 契约的 {@code extract} / {@code insert}。所以这里把整个 ME 网络包成一个标准的
 * {@link LongEnergyHandler}，搬运逻辑（先模拟后提交、余量退回源）与方块能量完全同源。</p>
 *
 * <p>判定「是不是端点」复用 {@link AeNetworks#isEndpoint}：只要方块注册了 AE2 的网格节点宿主
 * 能力即可，无线访问点、ME 接口、终端、总线都能绑。</p>
 */
public final class AeEnergyCompat implements AeEnergyBridge {

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
    public LongEnergyHandler energyEndpoint(Level level, BlockPos pos) {
        return isEndpoint(level, pos) ? new FluxEndpoint(level, pos) : null;
    }

    /**
     * 写入被拒时的限流警告：每 {@link #WARN_INTERVAL_MS} 毫秒最多一条。
     *
     * <p>「接受不了」最常见的原因是网络里压根没有通量存储——FE 不是 AE2 自带的资源，
     * 得靠 AppliedFlux 的通量元件。这跟「网络取不到」是两回事，所以要写进日志。
     * 搬运每秒都在重试，不限流会把日志刷爆。</p>
     */
    private static final long WARN_INTERVAL_MS = 5000L;
    private static final AtomicLong LAST_WARN_AT = new AtomicLong();

    private static void warnRejected(BlockPos pos, String reason) {
        long now = System.currentTimeMillis();
        long last = LAST_WARN_AT.get();
        if (now - last < WARN_INTERVAL_MS || !LAST_WARN_AT.compareAndSet(last, now)) {
            return;
        }
        UselessMod.LOGGER.warn("无线物流：AE 能量端点 {} 无法接收 FE——{}", pos.toShortString(), reason);
    }

    /** ME 网络的 FE（AppliedFlux 通量）视角。 */
    private static final class FluxEndpoint implements LongEnergyHandler {
        private final Level level;
        private final BlockPos pos;

        FluxEndpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        /** 通量键：AppliedFlux 把 FE 存成 {@code FluxKey(FE)}，数量即 FE 数。 */
        private static AEKey fluxKey() {
            return FluxKey.of(EnergyType.FE);
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
            return Math.max(0L, storage.extract(fluxKey(), amount,
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
            long inserted = storage.insert(fluxKey(), amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            if (inserted <= 0L) {
                warnRejected(pos, "ME 网络没有收下这些 FE：网里没有通量存储"
                        + "（需要 AppliedFlux 的通量元件放进 ME 驱动器），或者已经存满了");
            }
            return Math.max(0L, inserted);
        }

        @Override
        public long stored() {
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return 0L;
            }
            return Math.max(0L, storage.getAvailableStacks().get(fluxKey()));
        }

        @Override
        public long capacity() {
            // ME 网络对每一种资源都没有「容量」这个概念，取 long 上限表示不设限。
            return Long.MAX_VALUE;
        }
    }
}
