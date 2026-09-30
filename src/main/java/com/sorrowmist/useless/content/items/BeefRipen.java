package com.sorrowmist.useless.content.items;

import com.sorrowmist.useless.utils.mining.MiningUtils;
import com.sorrowmist.useless.utils.mining.RightClickChainer;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;

import javax.annotation.Nullable;
import java.util.List;

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

    /**
     * 藤蔓「强制蔓延」每次作用最多新增几株。
     *
     * <p>按住连锁键（Tab）时整片作用，每株都会按这个上限放大，取值不宜太大。</p>
     */
    private static final int MAX_VINE_SPREAD_PER_ACTION = 4;

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
     * <p>判定完全取决于两个开关：催熟只认骨粉目标与「特殊催生目标」（藤蔓 / 花），
     * 强制生长只认「会随机刻」。</p>
     */
    public static boolean canRipen(BlockState state, LevelReader level, BlockPos pos, ItemStack stack) {
        if (EndlessBeafItem.isRipenEnabled(stack)
                && (isBonemealTarget(state, level, pos) || isSpecialRipenTarget(state))) {
            return true;
        }
        return EndlessBeafItem.isForceGrowEnabled(stack) && state.isRandomlyTicking();
    }

    /**
     * 「特殊催生目标」：既不是原版骨粉目标、也不靠随机刻的植物，需要自写逻辑。
     *
     * <ul>
     *   <li><b>藤蔓 {@link VineBlock}</b>：不是 {@code BonemealableBlock}，催熟路径完全覆盖不到；
     *       强制生长虽能靠随机刻推它，但受游戏规则 {@code doVinesSpread}、25% 概率与
     *       「9×3×9 内最多 5 株」三重限制。</li>
     *   <li><b>花 {@link FlowerBlock}</b>：既不是 {@code BonemealableBlock}，也没有
     *       {@code randomTicks()}，两个开关原本都无效。原版对花本身没有任何骨粉行为。</li>
     * </ul>
     *
     * <p>凋灵玫瑰 {@code WitherRoseBlock} 继承自 {@link FlowerBlock}，同样包含在内。</p>
     */
    private static boolean isSpecialRipenTarget(BlockState state) {
        Block block = state.getBlock();
        return block instanceof VineBlock || block instanceof FlowerBlock;
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
        return ripenAt(level, pos, player, stack, null);
    }

    /**
     * 与 {@link #ripenAt(ServerLevel, BlockPos, Player, ItemStack)} 相同，但可以把花的掉落物
     * 收集到 {@code dropCollector} 里延迟提交。
     *
     * <p>连锁（Tab）时由 {@code RightClickChainer#ripenBlocks} 传入一个列表、整片收集后
     * 只调一次 {@code MiningUtils.handleDrops}，避免逐株向 AE 发上千次请求；
     * 传 {@code null} 表示立即走统一掉落出口。</p>
     */
    public static boolean ripenAt(ServerLevel level, BlockPos pos, Player player, ItemStack stack,
                                  @Nullable List<ItemStack> dropCollector) {
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

        // ---------- 路径 1.5：藤蔓 / 花的特殊催生（仅「催熟」开关下） ----------
        // 两者都不是原版骨粉目标：藤蔓不是 BonemealableBlock，花既非骨粉目标也无随机刻，
        // 路径 1、2 都覆盖不到。挂在「催熟」开关下，与用户对「催生」的认知一致。
        if (bonemealMode && !changed) {
            BlockState current = level.getBlockState(pos);
            if (current.getBlock() instanceof VineBlock) {
                changed = ripenVine(level, pos, current);
            } else if (current.getBlock() instanceof FlowerBlock) {
                changed = ripenFlower(level, pos, current, player, stack, dropCollector);
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

    /**
     * 藤蔓的「强制蔓延」：<b>不看</b>游戏规则 {@code doVinesSpread}、<b>不看</b>原版 25% 概率、
     * 也<b>不看</b>原版「9×3×9 内最多 5 株」的上限，每次点击必定长出新藤蔓。
     *
     * <p>原版逻辑在 {@code VineBlock#randomTick}（protected，无法直接调用），这里用公开 API
     * 复刻它的三条分支：向上补面、向水平邻格蔓延、向下复制一份。每次调用最多新增
     * {@link #MAX_VINE_SPREAD_PER_ACTION} 株。</p>
     *
     * <p>成功判定直接取 {@code level.setBlock} 的返回值 —— 蔓延改的可能是<b>邻居</b>方块，
     * 只比较 {@code pos} 自身状态会漏判。</p>
     */
    private static boolean ripenVine(ServerLevel level, BlockPos pos, BlockState state) {
        RandomSource random = level.getRandom();
        boolean acted = false;

        for (int placed = 0; placed < MAX_VINE_SPREAD_PER_ACTION; placed++) {
            BlockState current = level.getBlockState(pos);
            if (!(current.getBlock() instanceof VineBlock)) {
                break;
            }

            // ① 上方能附着 → 给自己补一个 UP 面（藤蔓自上方垂下）
            if (!current.getValue(VineBlock.UP)
                    && MultifaceBlock.canAttachTo(level, Direction.DOWN, pos.above(),
                                                  level.getBlockState(pos.above()))) {
                acted |= level.setBlock(pos, current.setValue(VineBlock.UP, true), 3);
                continue;
            }

            Direction direction = Direction.Plane.HORIZONTAL.getRandomDirection(random);
            BlockPos side = pos.relative(direction);
            BlockState sideState = level.getBlockState(side);

            if (sideState.isAir()) {
                // 在空气邻格放一株新藤蔓：找一个相邻的实体方块作为附着面
                if (tryPlaceVineAt(level, side, random)) {
                    acted = true;
                    continue;
                }
            } else if (!current.getValue(VineBlock.getPropertyForFace(direction))
                    && MultifaceBlock.canAttachTo(level, direction, side, sideState)) {
                // 邻格能支撑 → 给自己这一面也长上藤蔓
                acted |= level.setBlock(pos, current.setValue(VineBlock.getPropertyForFace(direction), true), 3);
                continue;
            }

            // ③ 向下蔓延：把当前各面复制一份到下方（下方是空气或藤蔓）
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isAir() || belowState.getBlock() instanceof VineBlock) {
                BlockState base = belowState.getBlock() instanceof VineBlock
                        ? belowState
                        : Blocks.VINE.defaultBlockState();
                BlockState copied = copyVineFaces(current, base, random);
                if (hasHorizontalFace(copied)) {
                    acted |= level.setBlock(below, copied, 3);
                }
            }
        }

        return acted;
    }

    /**
     * 在空气格 {@code pos} 放一株藤蔓：找一个相邻实体方块作为附着面，必要时退化为一株自上方垂下的藤蔓。
     *
     * @return 是否真的放下了
     */
    private static boolean tryPlaceVineAt(ServerLevel level, BlockPos pos, RandomSource random) {
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos anchor = pos.relative(face);
            if (MultifaceBlock.canAttachTo(level, face, anchor, level.getBlockState(anchor))) {
                return level.setBlock(pos, Blocks.VINE.defaultBlockState()
                                                       .setValue(VineBlock.getPropertyForFace(face), true), 3);
            }
        }
        if (MultifaceBlock.canAttachTo(level, Direction.DOWN, pos.above(), level.getBlockState(pos.above()))) {
            return level.setBlock(pos, Blocks.VINE.defaultBlockState().setValue(VineBlock.UP, true), 3);
        }
        return false;
    }

    /** 复刻原版 {@code VineBlock#copyRandomFaces}：随机把源藤蔓的水平面复制到目标上。 */
    private static BlockState copyVineFaces(BlockState source, BlockState target, RandomSource random) {
        BlockState result = target;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (random.nextBoolean()) {
                BooleanProperty property = VineBlock.getPropertyForFace(direction);
                if (source.getValue(property)) {
                    result = result.setValue(property, true);
                }
            }
        }
        return result;
    }

    private static boolean hasHorizontalFace(BlockState state) {
        return state.getValue(VineBlock.NORTH) || state.getValue(VineBlock.EAST)
                || state.getValue(VineBlock.SOUTH) || state.getValue(VineBlock.WEST);
    }

    /**
     * 花的「催生」：只产出该花的掉落物，<b>花本体原地保留、也不增殖新花</b>。
     *
     * <p>掉落走统一出口 {@code MiningUtils.handleDrops}（AE 存储优先 → 范围磁力），
     * 与挖掘、杀怪、收菜保持一致。连锁时改为收集到 {@code dropCollector} 延迟提交。</p>
     */
    private static boolean ripenFlower(ServerLevel level, BlockPos pos, BlockState state, Player player,
                                       ItemStack stack, @Nullable List<ItemStack> dropCollector) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, stack);
        if (drops.isEmpty()) {
            return false;
        }

        if (dropCollector != null) {
            dropCollector.addAll(drops);
        } else {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(drops), stack, Vec3.atCenterOf(pos));
        }
        return true;
    }
}
