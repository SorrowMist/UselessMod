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
 * 造化杖的「催熟 / 强制生长」行为。
 *
 * <p>由两个<b>互相独立</b>的开关驱动，玩家自行抉择：
 *
 * <ul>
 *   <li><b>催熟</b>（{@code beef_ripen}）：只走原版骨粉判定。方块实现了
 *       {@link BonemealableBlock} 且当前是有效目标时，循环 {@code performBonemeal}
 *       直到长满。覆盖作物、树苗、菌类、藤蔓等。<b>对原版骨粉无效的方块不起作用</b>
 *       ——甘蔗、仙人掌就没实现 {@code BonemealableBlock}。</li>
 *   <li><b>强制生长</b>（{@code beef_force_grow}）：改用「随机刻」推进方块。
 *       凡是 {@code isRandomlyTicking()} 的方块都能被推起来，因此甘蔗、仙人掌、
 *       竹子这些骨粉无效的方块也长；南瓜/西瓜茎长满后靠随机刻结果实，同样靠它补上。</li>
 * </ul>
 *
 * <p>两个开关可以同时开：催熟先把骨粉能做的做完，没推进时才轮到强制生长补刀。
 * 只开强制生长也能单独工作（此时作物靠随机刻慢慢长）。
 *
 * <p><b>强制生长为什么不设白名单</b>：它本质是「无条件推进随机刻」，而随机刻行为
 * 不止生长——树叶会枯萎掉落、火会蔓延、雪冰会融化、耕地会退化成泥土、紫颂花在无法
 * 继续生长时会打掉自己。任何白名单都只能覆盖原版方块、对模组方块失明。与其猜，不如
 * 把判断权交给玩家：默认关闭 + tooltip 里明确写清代价。
 *
 * <p>两条路径的「停止条件」刻意不同：
 * <ul>
 *   <li>骨粉路径<b>严格</b>：方块状态一旦没变化就立刻停。草方块长花、菌岩蔓延这类
 *       <b>传播型</b>方块的 {@code isValidBonemealTarget} 会一直为真，不严格停就会
 *       重复几十次、刷出一整片花海并造成卡顿。</li>
 *   <li>随机刻路径<b>宽容</b>：一次作用里连推 {@link #MAX_RANDOM_TICKS} 次随机刻。
 *       因为这类生长本身带随机概率（仙人掌每刻只有一定概率长龄，茎结果实更是概率事件），
 *       一次没动静不代表不会长；而每次随机刻只是几次方块读取，成本极低。
 *       唯一的例外是草方块/菌岩这类「向邻居散布」的方块，见 {@link #SPREADER_RANDOM_TICKS}。</li>
 * </ul>
 *
 * <p>按住连锁键（Tab）时会整片作用，扫描规则与「顺手收菜」共用
 * （{@link RightClickChainer} 的等价组 + X/Y/Z 范围 + 数量上限）。
 *
 * <p>与打火石 / 顺手收菜保持一致：潜行右键时让出这次交互，交给其它模组处理。
 * 造化杖本身不可损坏，因此不消耗任何物品。
 */
public final class BeefRipen {

    /** 骨粉路径：单个方块最多施加多少次骨粉效果（正常情况下 2~4 次即可长满）。 */
    private static final int MAX_STEPS = 32;

    /** 随机刻路径：普通方块最多推进多少次随机刻。 */
    private static final int MAX_RANDOM_TICKS = 128;

    /**
     * 随机刻路径：传播型方块（草方块、菌岩）单独限到很少几次。
     *
     * <p>它们每次随机刻都要在周围扫一片区域做散布尝试（草方块一次约 128 次），
     * 而自身方块状态<b>永远不变</b>——因此会一直吃满上限，给 128 次就变成
     * 上万次大范围散布尝试，纯属浪费且会卡顿。其余方块每次随机刻只有几次方块读取，
     * 给满额几乎无成本。
     */
    private static final int SPREADER_RANDOM_TICKS = 4;

    /** 骨粉粒子（happy villager）的 LevelEvent id。 */
    private static final int BONE_MEAL_PARTICLES = 2005;

    private BeefRipen() {
    }

    /**
     * 尝试执行一次催熟 / 强制生长（按住连锁键时整片作用）。
     *
     * @return {@link InteractionResult#PASS} 表示该位置没有可作用的目标，
     *         交由后续行为链继续处理。
     */
    public static InteractionResult tryUse(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        Player player = ctx.getPlayer();
        if (player == null || player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        boolean bonemealMode = EndlessBeafItem.isRipenEnabled(stack);
        boolean forceGrowMode = EndlessBeafItem.isForceGrowEnabled(stack);
        if (!bonemealMode && !forceGrowMode) {
            return InteractionResult.PASS;
        }

        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        if (!canRipen(level.getBlockState(pos), level, pos, stack)) {
            return InteractionResult.PASS;
        }

        // 两端都消费本次交互，避免客户端反复摆动
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        // 按住连锁键时整片作用；Tab 状态由服务端维护，两端判定一致
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

    /**
     * 该方块状态当前是否可被作用（客户端也用它做交互预测）。
     *
     * <p>判定完全取决于两个开关：催熟只认骨粉目标，强制生长只认「会随机刻」。
     */
    public static boolean canRipen(BlockState state, LevelReader level, BlockPos pos, ItemStack stack) {
        if (EndlessBeafItem.isRipenEnabled(stack) && isBonemealTarget(state, level, pos)) {
            return true;
        }
        return EndlessBeafItem.isForceGrowEnabled(stack) && state.isRandomlyTicking();
    }

    /**
     * 服务端作用于单个方块：先走骨粉路径，再按需走随机刻路径。
     *
     * <p>骨粉路径发 {@link BonemealEvent}（语义就是「玩家在施骨粉」，让其它模组可以否决）；
     * 随机刻路径不发该事件——它不是骨粉——改由 {@code CommonHooks.canCropGrow} 把关，
     * 这也正是 {@code randomTick} 自身会查的那个生长许可钩子。
     *
     * @return 是否真的产生了作用（没有可作用目标、或事件被取消且未标记成功时返回 false）
     */
    public static boolean ripenAt(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        boolean bonemealMode = EndlessBeafItem.isRipenEnabled(stack);
        boolean forceGrowMode = EndlessBeafItem.isForceGrowEnabled(stack);

        BlockState state = level.getBlockState(pos);
        if (!canRipen(state, level, pos, stack)) {
            return false;
        }

        if (bonemealMode && isBonemealTarget(state, level, pos)) {
            BonemealEvent event = new BonemealEvent(player, level, pos, state, stack);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                return event.isSuccessful();
            }
        }
        if (!CommonHooks.canCropGrow(level, pos, state, true)) {
            return false;
        }

        boolean bonemealApplied = false;
        boolean changed = false;

        // ---------- 路径 1：骨粉（严格停止） ----------
        if (bonemealMode) {
            for (int i = 0; i < MAX_STEPS; i++) {
                BlockState before = level.getBlockState(pos);
                if (!(before.getBlock() instanceof BonemealableBlock growable)
                        || !growable.isValidBonemealTarget(level, pos, before)) {
                    break;
                }

                growable.performBonemeal(level, level.getRandom(), pos, before);
                bonemealApplied = true;

                // 传播型骨粉（草方块长花、菌岩蔓延等）不会改变自身方块状态，只作用一次即可。
                if (level.getBlockState(pos) == before) {
                    break;
                }
                changed = true;
            }
        }

        // ---------- 路径 2：随机刻（按方块类型给上限） ----------
        // 只在骨粉没能让方块状态前进时才走：既覆盖骨粉不支持的甘蔗/仙人掌/竹子，
        // 也覆盖「长满后靠随机刻结果」的南瓜/西瓜茎。
        if (forceGrowMode && !changed && level.getBlockState(pos).isRandomlyTicking()) {
            int limit = randomTickLimit(level.getBlockState(pos));
            for (int i = 0; i < limit; i++) {
                BlockState before = level.getBlockState(pos);
                before.randomTick(level, pos, level.getRandom());
                if (level.getBlockState(pos) != before) {
                    changed = true;
                }
            }
        }

        // 完全没动作（例如本来就长满、又没有随机刻可推）时返回 false，交回后续行为链
        if (!changed && !bonemealApplied) {
            return false;
        }

        level.levelEvent(BONE_MEAL_PARTICLES, pos, 0);
        return true;
    }

    /** 该方块当前是否为有效的原版骨粉目标。 */
    private static boolean isBonemealTarget(BlockState state, LevelReader level, BlockPos pos) {
        return state.getBlock() instanceof BonemealableBlock target
                && target.isValidBonemealTarget(level, pos, state);
    }

    /**
     * 单次作用里最多推进多少次随机刻。
     *
     * <p>普通方块给 {@link #MAX_RANDOM_TICKS}：这类生长本身带随机概率（仙人掌每刻只有一定
     * 概率长龄，茎结果实更是概率事件），需要足够多次尝试才可靠；而每次随机刻只是几次方块读取，
     * 成本极低。
     *
     * <p>草方块 / 菌岩这类「向邻居散布」的方块单独限到 {@link #SPREADER_RANDOM_TICKS}。
     * 这里直接借用原版 {@link BonemealableBlock#getType()} 的判定，不需要自己维护白名单——
     * 它本来就是原版为区分「自身生长」与「向邻居散布」而提供的信号。
     */
    private static int randomTickLimit(BlockState state) {
        if (state.getBlock() instanceof BonemealableBlock target
                && target.getType() == BonemealableBlock.Type.NEIGHBOR_SPREADER) {
            return SPREADER_RANDOM_TICKS;
        }
        return MAX_RANDOM_TICKS;
    }
}
