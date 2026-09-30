package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.api.entity.BeeTimerAccess;
import com.sorrowmist.useless.api.entity.TurtleTimerAccess;
import com.sorrowmist.useless.content.entities.BeefTimeAccelerationEntity;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
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

    /**
     * 对「生物」施加时间加速（潜行右键生物，由「生物加速」模式驱动）。
     *
     * <p>与方块版共用同一套载体实体（{@link BeefTimeAccelerationEntity}）、叠加速度、
     * 续期与悬浮文字，只是目标换成实体。重复右键同一只生物会叠加速度并续期。</p>
     */
    public static InteractionResult tryUseOnEntity(ItemStack stack, Player player, LivingEntity target) {
        if (target instanceof Player) {
            return InteractionResult.PASS;
        }

        Level level = target.level();
        if (level.isClientSide) {
            // 纯预测，零副作用
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (target.isRemoved() || !target.isAlive()) {
            return InteractionResult.PASS;
        }

        // 注意查询盒必须放大：载体悬在目标头顶（y + 身高 + 0.4），
        // 落在目标包围盒之外，用 new AABB(blockPosition()) 永远查不到 → 每次右键都会重复新建。
        List<BeefTimeAccelerationEntity> found = serverLevel.getEntitiesOfClass(
                BeefTimeAccelerationEntity.class,
                target.getBoundingBox().inflate(2.0D),
                entity -> entity.isEntityTarget() && entity.getTargetEntityId() == target.getId()
        );
        BeefTimeAccelerationEntity effect = found.stream().findFirst().orElse(null);
        if (effect == null) {
            serverLevel.addFreshEntity(new BeefTimeAccelerationEntity(serverLevel, target));
            playSound(serverLevel, target.blockPosition(), 1);
            return InteractionResult.sidedSuccess(false);
        }

        int nextSpeed = nextTickSpeed(effect.getTickSpeed(), BeefTimeAccelerationEntity.MAX_ENTITY_TICK_SPEED);
        if (nextSpeed < 0) {
            return InteractionResult.sidedSuccess(false);
        }

        effect.setTickSpeed(nextSpeed);
        effect.setRemainingTime(refreshRemainingTime(effect.getTotalTime(), effect.getRemainingTime()));
        playSound(serverLevel, target.blockPosition(), nextSpeed);
        return InteractionResult.sidedSuccess(false);
    }

    /**
     * 只推进生物身上的「计时器」类数值，<b>不跑 AI / 寻路 / 移动 / 物理</b>。
     *
     * <p>因此生物不会走得变快、不会抖动，开销也远小于额外跑 tick。推进量
     * {@code extra = 1 << min(tickSpeed, MAX_ENTITY_TICK_SPEED)}。</p>
     *
     * <p>覆盖：幼年成长与繁殖后冷却（{@code AgeableMob.age}）、鸡下蛋间隔、
     * 羊剪毛后长毛、蜜蜂不疲倦/立刻进巢/采蜜、海龟下蛋倒计时。</p>
     *
     * <p><b>刻意不推 {@code Animal.inLove}</b>：它是「发情窗口」（600 tick），
     * {@code BreedGoal} 靠它驱动配对，推快会让窗口瞬间归零、动物永远来不及找到配偶，
     * 等于禁止繁殖。真正的「繁殖冷却」是繁殖成功后的 {@code setAge(6000)}，已由 age 覆盖。
     * 也刻意不推 {@code LivingEntity.tickEffects()}（药水时长）。</p>
     */
    public static void accelerateEntityTimers(LivingEntity target, int tickSpeed) {
        int extra = 1 << Math.min(tickSpeed, BeefTimeAccelerationEntity.MAX_ENTITY_TICK_SPEED);

        // ① 幼年成长（age < 0 向 0 推进）+ 繁殖后冷却（age > 0 向 0 递减）
        //    覆盖绝大多数动物、村民与蝌蚪；繁殖成功后 Animal 会 setAge(6000) 也走这里。
        if (target instanceof AgeableMob ageable) {
            int age = ageable.getAge();
            if (age != 0) {
                int step = Math.min(Math.abs(age), extra);
                ageable.setAge(age < 0 ? age + step : age - step);
            }
        }

        // ② 鸡下蛋间隔：eggTime 是 public 字段，扣到 0 后由原版 aiStep 在下一 tick 下蛋并重置
        if (target instanceof Chicken chicken && !chicken.isBaby() && !chicken.isChickenJockey()) {
            chicken.eggTime = Math.max(0, chicken.eggTime - extra);
        }

        // ③ 羊：剪毛后长毛。原版是「吃草后长毛」的事件驱动，这里直接恢复（保留毛色）
        if (target instanceof Sheep sheep && !sheep.isBaby() && sheep.isSheared()) {
            sheep.setSheared(false);
        }

        // ④ 蜜蜂：不再「疲倦」、允许立刻进巢；采蜜状态需访问接口（setHasNectar 为包级私有）
        if (target instanceof Bee bee) {
            bee.resetTicksWithoutNectarSinceExitingHive();
            bee.setStayOutOfHiveCountdown(0);
            if (!bee.hasNectar()) {
                ((BeeTimerAccess) bee).uselessMod$setBeeHasNectar(true);
            }
        }

        // ⑤ 海龟：下蛋倒计时（layEggCounter 为包级私有，需访问接口）
        if (target instanceof Turtle turtle && turtle.isLayingEgg()) {
            TurtleTimerAccess accessor = (TurtleTimerAccess) turtle;
            accessor.uselessMod$setLayEggCounter(accessor.uselessMod$getLayEggCounter() + extra);
        }
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
