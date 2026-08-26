package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.content.entities.BeefTimeAccelerationEntity;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

public final class BeefTimeAcceleration {
    private static final int RANDOM_TICK_CHANCE = 1365;

    private BeefTimeAcceleration() {
    }

    public static InteractionResult tryUse(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        if (!shouldBlockOtherRightClick(stack, player)) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        BlockState state = serverLevel.getBlockState(pos);
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        if (!isValidTarget(serverLevel, state, blockEntity)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockPos immutablePos = pos.immutable();
        List<BeefTimeAccelerationEntity> entities = serverLevel.getEntitiesOfClass(
                BeefTimeAccelerationEntity.class,
                new AABB(immutablePos),
                entity -> entity.getTargetPos().equals(immutablePos)
        );
        BeefTimeAccelerationEntity effect = entities.stream().findFirst().orElse(null);
        if (effect == null) {
            serverLevel.addFreshEntity(new BeefTimeAccelerationEntity(serverLevel, immutablePos));
            playSound(serverLevel, immutablePos, 1);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        int nextSpeed = nextTickSpeed(effect.getTickSpeed(), BeefTimeAccelerationEntity.MAX_TICK_SPEED);
        if (nextSpeed < 0) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        effect.setTickSpeed(nextSpeed);
        effect.setRemainingTime(refreshRemainingTime(effect.getTotalTime(), effect.getRemainingTime()));
        playSound(serverLevel, immutablePos, nextSpeed);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    public static boolean shouldBlockOtherRightClick(ItemStack stack, Player player) {
        return player != null
                && player.isShiftKeyDown()
                && stack.getOrDefault(UComponents.BeefTimeAccelerationEnabledComponent.get(), false);
    }

    static int nextTickSpeed(int currentTickSpeed, int maxTickSpeed) {
        int next = currentTickSpeed + 1;
        return next <= maxTickSpeed ? next : -1;
    }

    static int refreshRemainingTime(int remainingTicks) {
        return refreshRemainingTime(BeefTimeAccelerationEntity.DEFAULT_TOTAL_TIME, remainingTicks);
    }

    static int refreshRemainingTime(int totalTime, int remainingTicks) {
        return remainingTicks + Math.max(0, totalTime - remainingTicks) / 2;
    }

    public static boolean isValidTarget(ServerLevel level, BlockState state, BlockEntity blockEntity) {
        if (blockEntity == null) {
            return state.isRandomlyTicking();
        }
        return state.getTicker(level, blockEntity.getType()) != null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void tickTarget(ServerLevel level,
                                  BlockPos pos,
                                  BlockState state,
                                  BlockEntity blockEntity,
                                  int tickSpeed) {
        int extraTicks = 1 << tickSpeed;
        if (blockEntity != null) {
            BlockEntityTicker ticker = state.getTicker(level, blockEntity.getType());
            if (ticker == null) {
                return;
            }
            for (int i = 0; i < extraTicks; i++) {
                ticker.tick(level, pos, state, blockEntity);
            }
            return;
        }

        if (!state.isRandomlyTicking()) {
            return;
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < extraTicks; i++) {
            if (random.nextInt(RANDOM_TICK_CHANCE) == 0) {
                state.randomTick(level, pos, random);
            }
        }
    }

    private static void playSound(ServerLevel level, BlockPos pos, int tickSpeed) {
        level.playSound(
                null,
                pos,
                SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value(),
                SoundSource.PLAYERS,
                1.0F,
                (float) Math.pow(2.0D, (tickSpeed - 5) / 12.0D)
        );
    }

}
