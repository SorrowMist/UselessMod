package com.sorrowmist.useless.compat.ae;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.sorrowmist.useless.content.stafflink.SourceHandlerView;
import gripe._90.arseng.me.key.SourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * {@link AeSourceBridge} 的 Ars Énergistique 实现。
 *
 * <p>只有 AE2 与 arseng 同时在场时，{@link AeSourceCompatLoader} 才会加载本类。</p>
 *
 * <h2>为什么需要一个条目而不是直接读写</h2>
 *
 * <p>ME 网络里的魔源就是<b>一条</b> {@link SourceKey#KEY} 记录，量走 key 的值。
 * 因此端点不需要快照列表：每次操作直接对这条记录做 long 的 {@code insert}/{@code extract}，
 * 再夹到 int 返回给搬运侧——魔源的 int 上限是 Ars Nouveau 自身的物理事实，不是我们的截断。</p>
 */
public final class AeSourceCompat implements AeSourceBridge {

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
    public SourceHandlerView sourceEndpoint(Level level, BlockPos pos) {
        return AeNetworks.isEndpoint(level, pos) ? new Endpoint(level, pos) : null;
    }

    private static final class Endpoint implements SourceHandlerView {
        private final Level level;
        private final BlockPos pos;

        Endpoint(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        /**
         * 网络里的魔源存量。
         *
         * <p>每次现查而不是缓存：ME 网络内容随时在变，缓存出来的值会立刻过时。</p>
         */
        private long stored() {
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return 0L;
            }
            long total = 0L;
            for (var entry : storage.getAvailableStacks()) {
                AEKey key = entry.getKey();
                if (key == SourceKey.KEY) {
                    total += Math.max(0L, entry.getLongValue());
                }
            }
            return total;
        }

        @Override
        public int amount() {
            return clamp(stored());
        }

        @Override
        public int capacity() {
            // ME 网络对魔源不设「罐容量」，用 SourceKey 自己的上限表示。
            return SourceKey.MAX_SOURCE;
        }

        @Override
        public int extract(int amount, boolean simulate) {
            if (amount <= 0) {
                return 0;
            }
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return 0;
            }
            long taken = storage.extract(SourceKey.KEY, amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            return clamp(taken);
        }

        @Override
        public int receive(int amount, boolean simulate) {
            if (amount <= 0) {
                return 0;
            }
            MEStorage storage = AeNetworks.storage(level, pos);
            if (storage == null) {
                return 0;
            }
            long inserted = storage.insert(SourceKey.KEY, amount,
                    simulate ? Actionable.SIMULATE : Actionable.MODULATE, source());
            return clamp(inserted);
        }

        private static int clamp(long value) {
            if (value <= 0L) {
                return 0;
            }
            return (int) Math.min(value, Integer.MAX_VALUE);
        }
    }
}
