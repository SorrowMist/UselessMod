package com.sorrowmist.useless.content.blockentities;

import com.sorrowmist.useless.content.blocks.CreatorDollBlock;
import com.sorrowmist.useless.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 服务端同步弹跳区间，客户端依据状态序号确定动画起点；时序与同步结构参考 ExtendedAE Plus 提交
 * 3832a827e859807a2fff3857c8719a097181804c。
 */
public final class CreatorDollBlockEntity extends BlockEntity {
    public static final int BOUNCE_DURATION_TICKS = 10;

    private long serverBounceStartedAt = Long.MIN_VALUE;
    private long clientBounceStartedAt = Long.MIN_VALUE;
    private int lastClientBounceId = -1;

    public CreatorDollBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CREATOR_DOLL.get(), pos, state);
    }

    public void startBounce() {
        Level level = getLevel();
        if (level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        if (state.getValue(CreatorDollBlock.BOUNCING)) return;

        serverBounceStartedAt = level.getGameTime();
        int nextBounceId = (state.getValue(CreatorDollBlock.BOUNCE_ID) + 1) % 16;
        level.setBlock(worldPosition, state
                .setValue(CreatorDollBlock.BOUNCING, true)
                .setValue(CreatorDollBlock.BOUNCE_ID, nextBounceId), Block.UPDATE_CLIENTS);
    }

    public float getSquash(float partialTick) {
        Level level = getLevel();
        if (level == null || clientBounceStartedAt == Long.MIN_VALUE) return 0;

        double elapsed = level.getGameTime() - clientBounceStartedAt + partialTick;
        if (elapsed < 0 || elapsed >= BOUNCE_DURATION_TICKS) return 0;

        double beat = elapsed / BOUNCE_DURATION_TICKS;
        return (float) ((1 - Math.cos(beat * Math.PI * 2)) * 0.5);
    }

    public static void clientTick(Level level, BlockState state, CreatorDollBlockEntity entity) {
        int bounceId = state.getValue(CreatorDollBlock.BOUNCE_ID);
        if (bounceId != entity.lastClientBounceId) {
            entity.lastClientBounceId = bounceId;
            entity.clientBounceStartedAt = level.getGameTime();
        }
    }

    public static void serverTick(
            Level level, BlockPos pos, BlockState state, CreatorDollBlockEntity entity) {
        if (!state.getValue(CreatorDollBlock.BOUNCING)) {
            entity.serverBounceStartedAt = Long.MIN_VALUE;
            return;
        }

        if (entity.serverBounceStartedAt == Long.MIN_VALUE
                || level.getGameTime() - entity.serverBounceStartedAt >= BOUNCE_DURATION_TICKS) {
            level.setBlock(pos, state.setValue(CreatorDollBlock.BOUNCING, false), Block.UPDATE_CLIENTS);
            entity.serverBounceStartedAt = Long.MIN_VALUE;
        }
    }
}
