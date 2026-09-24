package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.utils.mining.RightClickChainer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;

/**
 * 造化杖的「催熟」行为。
 *
 * <p>右键可催熟方块时，直接循环施加骨粉效果直到它长满（一键催到成熟），
 * 而不是像骨粉那样一次只推进一点。判定完全走原版 {@link BonemealableBlock}，
 * 因此作物、树苗、菌类、藤蔓等所有原版与模组方块都自动支持。
 *
 * <p>按住连锁键（Tab，与连锁挖掘同一个键）时会整片催熟，扫描规则与「顺手收菜」
 * 完全共用（{@link RightClickChainer} 的等价组 + X/Y/Z 范围 + 数量上限）。
 * 等价组按方块种类匹配，所以不同生长阶段的同一作物会被一起催熟。
 *
 * <p>两个关键保护：
 * <ul>
 *   <li>{@link #MAX_STEPS} 硬上限，避免个别方块 {@code isValidBonemealTarget} 永远为真时死循环；</li>
 *   <li>「方块状态没变化就停」——草方块长花、茎结瓜这类<b>传播型</b>骨粉只作用一次，
 *       既避免刷出一整片花海造成卡顿，也避免无意义的重复调用。</li>
 * </ul>
 *
 * <p>与打火石 / 顺手收菜保持一致：潜行右键时让出这次交互，交给其它模组处理。
 * 造化杖本身不可损坏，因此不消耗任何物品。
 */
public final class BeefRipen {

    /** 单个方块最多施加多少次骨粉效果（正常情况下 2~4 次即可长满）。 */
    private static final int MAX_STEPS = 32;

    /** 骨粉粒子（happy villager）的 LevelEvent id。 */
    private static final int BONE_MEAL_PARTICLES = 2005;

    private BeefRipen() {
    }

    /**
     * 尝试执行一次催熟（按住连锁键时整片催熟）。
     *
     * @return {@link InteractionResult#PASS} 表示该位置没有可催熟的目标，
     *         交由后续行为链继续处理。
     */
    public static InteractionResult tryUse(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        if (player == null
                || !EndlessBeafItem.isRipenEnabled(stack)
                || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!canRipen(level.getBlockState(pos), level, pos)) {
            return InteractionResult.PASS;
        }

        // 两端都消费本次交互，避免客户端反复摆动
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        // 按住连锁键时整片催熟；Tab 状态由服务端维护，两端判定一致
        int grown = RightClickChainer.shouldChain(player, stack)
                ? RightClickChainer.ripenBlocks(serverLevel, pos, player, stack)
                : (ripenAt(serverLevel, pos, player, stack) ? 1 : 0);

        if (grown == 0) {
            return InteractionResult.PASS;
        }

        // 整片只响一次，避免叠成噪音（粒子在 ripenAt 里逐块播）
        serverLevel.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
            serverPlayer.awardStat(Stats.ITEM_USED.get(stack.getItem()));
        }
        return InteractionResult.sidedSuccess(false);
    }

    /** 该方块状态当前是否可被催熟（客户端也用它做交互预测）。 */
    public static boolean canRipen(BlockState state, LevelReader level, BlockPos pos) {
        return state.getBlock() instanceof BonemealableBlock target
                && target.isValidBonemealTarget(level, pos, state);
    }

    /**
     * 服务端催熟单个方块：循环施加骨粉直到长满。
     *
     * <p>逐块发 {@link BonemealEvent} 与 {@code CommonHooks.canCropGrow}，让其它模组
     * 可以逐块否决（与骨粉走同一套兼容钩子）。事件只发一次，不要放进循环里。
     *
     * @return 是否真的催动了（没有可催熟目标、或事件被取消且未标记成功时返回 false）
     */
    public static boolean ripenAt(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        if (!canRipen(state, level, pos)) {
            return false;
        }

        BonemealEvent event = new BonemealEvent(player, level, pos, state, stack);
        if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
            return event.isSuccessful();
        }
        if (!CommonHooks.canCropGrow(level, pos, state, true)) {
            return false;
        }

        int grown = 0;
        for (int i = 0; i < MAX_STEPS; i++) {
            BlockState before = level.getBlockState(pos);
            if (!(before.getBlock() instanceof BonemealableBlock growable)
                    || !growable.isValidBonemealTarget(level, pos, before)) {
                break;
            }

            growable.performBonemeal(level, level.getRandom(), pos, before);
            grown++;

            // 传播型骨粉（草方块长花、茎结瓜等）不会改变自身方块状态，
            // 只作用一次即可，否则会重复几十次造成卡顿。
            if (level.getBlockState(pos) == before) {
                break;
            }
        }

        if (grown == 0) {
            return false;
        }

        level.levelEvent(BONE_MEAL_PARTICLES, pos, 0);
        return true;
    }
}
