package com.sorrowmist.useless.compat.ars;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * {@link SourceBridge} 的 Ars Nouveau 实现。
 *
 * <p>本类是全模组<b>唯一</b>引用 {@code ISourceTile} 的地方。只有 Ars Nouveau 加载时，
 * {@link ArsSourceCompatLoader} 才会通过 {@code Class.forName} 把本类载入 JVM；
 * 否则这里的方法签名永远不会被解析，缺少该模组的整合包不会因此启动失败。</p>
 */
public final class ArsSourceCompat implements SourceBridge {

    @Override
    public boolean hasTile(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ISourceTile;
    }

    @Override
    public int moveSource(Level sourceLevel, BlockPos sourcePos, Level targetLevel, BlockPos targetPos, int limit) {
        BlockEntity sourceEntity = sourceLevel.getBlockEntity(sourcePos);
        if (!(sourceEntity instanceof ISourceTile source)) {
            return 0;
        }
        BlockEntity targetEntity = targetLevel.getBlockEntity(targetPos);
        if (!(targetEntity instanceof ISourceTile target)) {
            return 0;
        }
        if (!target.canAcceptSource()) {
            return 0;
        }

        int available = source.getSource();
        if (available <= 0) {
            return 0;
        }
        int space = target.getMaxSource() - target.getSource();
        if (space <= 0) {
            return 0;
        }

        int move = Math.min(limit, Math.min(available, space));
        move = Math.min(move, source.getTransferRate());
        move = Math.min(move, target.getTransferRate());
        if (move <= 0) {
            return 0;
        }

        source.removeSource(move);
        target.addSource(move);
        return move;
    }
}
