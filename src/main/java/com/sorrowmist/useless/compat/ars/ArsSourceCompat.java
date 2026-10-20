package com.sorrowmist.useless.compat.ars;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.sorrowmist.useless.content.stafflink.SourceHandlerView;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * {@link SourceBridge} 的 Ars Nouveau 实现。
 *
 * <p>本类是全模组<b>唯一</b>引用 {@code ISourceTile} 的地方。只有 Ars Nouveau 加载时，
 * {@link ArsSourceCompatLoader} 才会通过 {@code Class.forName} 把本类载入 JVM；
 * 否则这里的方法签名永远不会被解析，缺少该模组的整合包不会因此启动失败。</p>
 *
 * <p>数量沿用 int：{@code ISourceTile} 本身就是 int 契约，魔源的容量与传输速率离
 * {@link Integer#MAX_VALUE} 极远，包一层 long 只会多一次无意义的转换。</p>
 */
public final class ArsSourceCompat implements SourceBridge {

    @Override
    @Nullable
    public SourceHandlerView sourceHandler(Level level, BlockPos pos) {
        if (level == null || pos == null || !level.isLoaded(pos)) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof ISourceTile tile ? new TileEndpoint(tile) : null;
    }

    private record TileEndpoint(ISourceTile tile) implements SourceHandlerView {

        @Override
        public int amount() {
            return Math.max(0, this.tile.getSource());
        }

        @Override
        public int capacity() {
            return Math.max(0, this.tile.getMaxSource());
        }

        @Override
        public int extract(int amount, boolean simulate) {
            if (amount <= 0 || !this.tile.canProvideSource()) {
                return 0;
            }
            int available = amount();
            if (available <= 0) {
                return 0;
            }
            int move = Math.min(Math.min(amount, available), rate());
            if (move <= 0) {
                return 0;
            }
            if (!simulate) {
                this.tile.removeSource(move);
            }
            return move;
        }

        @Override
        public int receive(int amount, boolean simulate) {
            if (amount <= 0 || !this.tile.canAcceptSource()) {
                return 0;
            }
            int space = capacity() - amount();
            if (space <= 0) {
                return 0;
            }
            int move = Math.min(Math.min(amount, space), rate());
            if (move <= 0) {
                return 0;
            }
            if (!simulate) {
                this.tile.addSource(move);
            }
            return move;
        }

        @Override
        public int transferRate() {
            return rate();
        }

        private int rate() {
            int rate = this.tile.getTransferRate();
            return rate <= 0 ? Integer.MAX_VALUE : rate;
        }
    }
}
