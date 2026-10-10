package com.sorrowmist.useless.utils.mining;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.DraconicEvolutionCompat;
import com.sorrowmist.useless.compat.exdeorum.ExDeorumCompat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ChainEquivalence;
import com.sorrowmist.useless.core.config.ChainGroupManager;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.UComponentUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import com.sorrowmist.useless.utils.mining.shape.ChainMiningShapeContext;
import com.sorrowmist.useless.utils.mining.shape.ChainMiningShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class MiningUtils {
    private static final Logger LOGGER = LogUtils.getLogger();

    record MiningResult(List<ItemStack> drops, int experience, boolean mined) {
        private static final MiningResult NOT_MINED = new MiningResult(List.of(), 0, false);
    }

    /**
     * 一次破坏尝试的结果。
     *
     * <p>和 {@link MiningResult} 的区别：这个只描述破坏回调这一步，不带经验等上层语义。</p>
     *
     * <p>两个布尔量必须分开：{@code effective} 表示本次破坏产生了效果（应当消耗该位置并交付掉落），
     * {@code blockRemoved} 表示方块确实从世界消失。二者在方块自行接管移除时并不等价——
     * 例如集成动力线缆按准星命中的部件拆除并保留整块方块，此时 {@code effective=true} 而
     * {@code blockRemoved=false}。依赖「方块已消失」的判定（如精准采集的回退掉落）必须读后者，
     * 否则会在方块仍留在原地时凭空产出其本体物品。</p>
     */
    record BreakOutcome(boolean effective, boolean blockRemoved, List<ItemStack> drops) {
        private static final BreakOutcome REFUSED = new BreakOutcome(false, false, List.of());
    }

    /**
     * 已经报过「破坏回调抛异常」的方块类型。
     *
     * <p>连锁挖掘一次可能命中同一类型的几十个方块，每个都抛一次异常就等于往日志里灌几十条栈。
     * 这里按方块类型去重：第一次带异常对象报，之后同类只累计次数。</p>
     */
    private static final Set<Block> REPORTED_BREAK_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Set<Block> REPORTED_REFUSED_REMOVALS = ConcurrentHashMap.newKeySet();

    /**
     * 获取强制挖掘兜底掉落物
     * 当方块正常破坏没有有效掉落时，返回一个与目标方块 NBT 完全一致的方块物品（含方块实体组件）。
     *
     * @param state 方块状态
     * @param level 世界
     * @param pos   方块位置
     * @return 兜底掉落物列表
     */
    static List<ItemStack> getForcedFallbackDrops(BlockState state, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty() || stack.is(Items.AIR)) {
            return Collections.emptyList();
        }

        // 兜底掉落的语义是"复制目标方块本身"，因此只要存在方块实体就写入其完整 NBT。
        // 使用 saveToItem 而非 collectComponents：后者只保存方块实体暴露的隐式物品组件，
        // 会丢失存放在方块实体 NBT 中的数据（如 AE2 线缆总线的部件/连接信息）。
        // 丢失这些数据会导致放回时方块实体为空而自我失效（物品直接消失）。
        // saveToItem 会写入 BLOCK_ENTITY_DATA（完整自定义 NBT）并附加组件，放置时可完整还原，
        // 与 Mekanism 纸箱保存整块方块实体数据的做法一致。
        if (be != null) {
            be.saveToItem(stack, level.registryAccess());
        }
        return Collections.singletonList(stack);
    }

    /**
     * 判断工具是否处于精准采集（SILK_TOUCH）模式
     */
    static boolean isSilkTouch(ItemStack tool) {
        return tool.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE) == EnchantMode.SILK_TOUCH;
    }

    /**
     * 检查是否完全没有有效掉落物
     */
    static boolean hasNoValidDrops(List<ItemStack> drops) {
        return drops.isEmpty() || drops.stream().allMatch(stack -> stack.isEmpty() || stack.is(Items.AIR));
    }

    public static boolean canMineBlock(BlockState state, ItemStack tool, boolean forceMining) {
        return forceMining || !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    /**
     * 选择强制挖掘最终应交付的掉落物。
     * 仅当掉落表和实际破坏均没有产生有效掉落时才使用方块本体兜底；
     * 任何非空自然掉落都应被视为合法结果，即使掉落物本身是 BlockItem。
     */
    static List<ItemStack> selectForcedDrops(List<ItemStack> naturalDrops, List<ItemStack> actualDrops,
                                             List<ItemStack> fallbackDrops) {
        if (hasNoValidDrops(naturalDrops)
                && hasNoValidDrops(actualDrops)
                && !hasNoValidDrops(fallbackDrops)) {
            return fallbackDrops;
        }
        return actualDrops;
    }

    /**
     * 处理方块破坏的核心逻辑：获取掉落物、处理掉落物、计算经验、破坏方块
     *
     * @param level       世界
     * @param pos         方块位置
     * @param state       方块状态
     * @param player      玩家
     * @param tool        工具
     * @param forceMining 是否为强制挖掘模式
     */
    static void processBlockBreak(ServerLevel level, BlockPos pos, BlockState state, Player player,
                                  ItemStack tool, boolean forceMining) {
        if (level.isClientSide()) {
            return;
        }

        // 本方法由方块破坏事件直接调用：任一模组的破坏回调抛出异常，异常都会沿事件
        // 分发链回传并中断整个 tick。因此在此处捕获并上报异常。
        try {
            MiningResult result = forceMining
                    ? forceMineBlock(level, pos, state, player, tool)
                    : mineBlock(level, pos, state, player, tool);
            List<ItemStack> drops = applyExDeorumDrops(level, state, result.drops(), tool, pos, player);
            handleDrops(player, drops, tool, Vec3.atCenterOf(pos));
            if (result.experience() > 0) {
                player.giveExperiencePoints(result.experience());
            }
        } catch (Throwable failure) {
            reportBlockBreakFailure(state, pos, failure);
        }
    }

    /**
     * 按造化杖上启用的 Ex Deorum 模式改写破坏掉落。
     *
     * <p>Ex Deorum 的三个工具效果由其 GlobalLootModifier 依工具标签触发，
     * 而造化杖不在这些标签内，故此处显式调用其配方缓存完成等价改写。</p>
     *
     * <p>未安装 Ex Deorum 或三个模式均未启用时原样返回入参，
     * 保证未启用该兼容时的行为与改动前完全一致。</p>
     *
     * <p>此处不以原始掉落为空作为提前返回条件：锤子与压缩锤以配方产物替换掉落，
     * 而 Ex Deorum 的锤子配方可覆盖自身无自然掉落的方块（如圆石产出沙砾），
     * 若在此跳过，此类方块将无法获得配方产物。</p>
     *
     * @param level  服务端世界
     * @param state  被破坏的方块状态
     * @param drops  原始掉落
     * @param tool   造化杖
     * @return 改写后的掉落列表
     */
    static List<ItemStack> applyExDeorumDrops(ServerLevel level, BlockState state,
                                              List<ItemStack> drops, ItemStack tool,
                                              BlockPos pos, Player player) {
        // 守卫必须在本方法内完成，且不能触碰 ExDeorumCompat 的任何成员：该类引用了 exdeorum
        // 的类型，链接它即须解析 HammerRecipe 等外部类型。若把守卫放进该类（原 isLoaded() 即是），
        // 那一行本身就会触发链接，未安装模组时直接抛 NoClassDefFoundError，守卫形同虚设。
        // 此处以纯 ModList 短路，使 ExDeorumCompat 仅在模组确已加载后才被链接，
        // 与 FtbUltimineChainKeyCompat 采用同一策略。模组 id 用字面量，避免本行出现对该类的引用。
        if (!ModList.get().isLoaded("exdeorum")) {
            return drops;
        }

        boolean hammer = UComponentUtils.isExDeorumHammerEnabled(tool);
        boolean compressedHammer = UComponentUtils.isExDeorumCompressedHammerEnabled(tool);
        boolean crook = UComponentUtils.isExDeorumCrookEnabled(tool);
        if (!hammer && !compressedHammer && !crook) {
            return drops;
        }

        return ExDeorumCompat.rewriteDrops(level, state, drops, tool,
                hammer, compressedHammer, crook, pos, player);
    }

    static MiningResult mineBlock(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (state.isAir() || !canMineBlock(state, tool, false)) {
            return MiningResult.NOT_MINED;
        }

        int experience = getExperience(level, pos, state, player, tool);
        BreakOutcome outcome = destroyBlockAndCollectDrops(level, pos, state, player, tool, false);
        return outcome.effective()
                ? new MiningResult(outcome.drops(), experience, true)
                : MiningResult.NOT_MINED;
    }

    static MiningResult forceMineBlock(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (state.isAir() || isForceMiningBlacklisted(state)) {
            return MiningResult.NOT_MINED;
        }
        if (DraconicEvolutionCompat.isChaosCrystal(state)
                && DraconicEvolutionCompat.handleChaosCrystalBreak(level, pos, state, player)) {
            return new MiningResult(List.of(), 0, true);
        }

        int experience = getExperience(level, pos, state, player, tool);
        if (isSilkTouch(tool)) {
            List<ItemStack> fallbackDrops = getForcedFallbackDrops(state, level, pos);
            // 回退掉落复制的是方块本体，必须以「方块确实已从世界消失」为准：
            // 方块自行接管移除时（如线缆按部件拆除）方块仍留在原地，此时发放本体会凭空产出物品。
            BreakOutcome outcome = destroyBlockAndCollectDrops(level, pos, state, player, tool, true);
            if (!outcome.blockRemoved()) {
                return outcome.effective()
                        ? new MiningResult(outcome.drops(), 0, true)
                        : MiningResult.NOT_MINED;
            }
            return new MiningResult(fallbackDrops, 0, true);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> naturalDrops = Block.getDrops(state, level, pos, blockEntity, player, tool);
        boolean useFallback = hasNoValidDrops(naturalDrops);
        List<ItemStack> fallbackDrops = useFallback
                ? getForcedFallbackDrops(state, level, pos)
                : List.of();
        BreakOutcome outcome = destroyBlockAndCollectDrops(level, pos, state, player, tool, true);
        if (!outcome.effective()) {
            return MiningResult.NOT_MINED;
        }
        // 回退掉落仅适用于方块确已消失的情形；方块被模组接管保留时只能交付实际产出的掉落。
        List<ItemStack> drops = outcome.blockRemoved()
                ? selectForcedDrops(naturalDrops, outcome.drops(), fallbackDrops)
                : outcome.drops();
        return new MiningResult(drops, experience, true);
    }

    private static int getExperience(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (tool.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE) != EnchantMode.FORTUNE) {
            return 0;
        }
        return state.getBlock().getExpDrop(state, level, pos, level.getBlockEntity(pos), player, tool);
    }

    /**
     * 快速破坏指定方块（Shift+右键物品使用时调用）
     * 功能：掉落物直接进背包、背包满掉脚下、正确保留 waterlogged 水源、弹出经验、粒子音效
     *
     * @param world  世界
     * @param pos    方块位置
     * @param state  方块状态
     * @param player 玩家（必须非空）
     * @param tool   手中物品（用于计算掉落、附魔、耐久等）
     */
    public static void quickBreakBlock(Level world, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (world.isClientSide()) {
            world.playSound(player, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.7F, 1.0F);
            return;
        }

        ServerLevel serverLevel = (ServerLevel) world;

        // 同样要把模组回调的异常挡在事件链之外：数据能源的三位一体样板核心在状态未就绪时
        // getDrops / playerWillDestroy 都会抛，异常冒回事件总线就会打断整 tick。
        try {
            // 破坏必须走与准星挖掘同一套回调次序，否则重写 onDestroyedByPlayer 的模组
            // （如集成动力线缆按部件拆除）会在此被整块移除，其部件本体无从掉落。
            // 掉落直接取该流程的收集结果：回调与移除两阶段的产出均在其中。
            // 不可改用 Block.getDrops 重新计算——该流程已把收集到的掉落实体移除，
            // 且掉落表结果不含移除阶段产出的内容物，重算会导致这部分掉落整体丢失。
            BreakOutcome outcome = destroyBlockAndCollectDrops(serverLevel, pos, state, player, tool, false);
            if (!outcome.effective()) {
                return;
            }

            List<ItemStack> drops = applyExDeorumDrops(serverLevel, state, outcome.drops(), tool, pos, player);
            handleDrops(player, drops, tool, Vec3.atCenterOf(pos));
        } catch (Throwable failure) {
            reportBlockBreakFailure(state, pos, failure);
        }
    }

    /**
     * 合并相同物品的堆叠
     *
     * @param items 要合并的物品列表
     * @return 合并后的物品列表
     */
    public static List<ItemStack> mergeItemStacks(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) continue;
            
            boolean mergedFlag = false;
            // 尝试合并到已有的堆叠中
            for (ItemStack mergedItem : merged) {
                // 检查：物品相同、组件相同、且有堆叠空间
                if (ItemStack.isSameItemSameComponents(item, mergedItem)) {
                    int remaining = mergedItem.getMaxStackSize() - mergedItem.getCount();
                    if (remaining > 0) {
                        int addCount = Math.min(remaining, item.getCount());
                        mergedItem.grow(addCount);
                        item.shrink(addCount);
                        if (item.isEmpty()) {
                            mergedFlag = true;
                            break;
                        }
                    }
                }
            }

            // 未完成合并（组件不同或空间不足）时，作为新堆加入
            if (!mergedFlag && !item.isEmpty()) {
                merged.add(item.copy());
            }
        }
        return merged;
    }

    /**
     * 处理掉落物（优先入 AE，其次按磁力开关决定进背包还是留在原地）。
     *
     * <p>兼容旧调用：落地点退回玩家脚下。</p>
     *
     * @param player 玩家
     * @param drops  掉落物列表
     * @param tool   工具
     */
    public static void handleDrops(Player player, List<ItemStack> drops, ItemStack tool) {
        handleDrops(player, drops, tool, player.position());
    }

    /**
     * 处理掉落物（优先入 AE，其次按磁力开关决定进背包还是留在原地）。
     *
     * <p>行为矩阵：</p>
     * <ul>
     *   <li>AE 存储优先开启且已绑定无线访问点：先尝试存入 AE，未能存入的继续下一步。</li>
     *   <li>范围磁力开启：剩余物品进背包，背包满则掉在玩家脚下。</li>
     *   <li>范围磁力关闭：剩余物品在 {@code dropOrigin} 处落地，走原版拾取。</li>
     * </ul>
     *
     * <p>注意 AE 存储优先是独立于范围磁力生效的：即使磁力关闭，只要 AE 优先开启，
     * 产物仍会优先存入 AE，仅 AE 无法存入的部分落地。</p>
     *
     * @param player     玩家
     * @param drops      掉落物列表
     * @param tool       工具
     * @param dropOrigin 磁力关闭时的落地点（AE 无法存入的部分亦落于此）
     */
    public static void handleDrops(Player player, List<ItemStack> drops, ItemStack tool, Vec3 dropOrigin) {
        handleDrops(player, drops, tool, dropOrigin, true);
    }

    /**
     * 与 {@link #handleDrops(Player, List, ItemStack, Vec3)} 相同，但可以关掉 AE 分支。
     *
     * <p>捕捉（刷怪蛋）走的是 {@code allowAe = false}：刷怪蛋是玩家想立刻拿在手上的东西，
     * 不该被「AE 存储优先」吸进网络里，只按「范围磁力」决定进背包还是落地。</p>
     *
     * @param allowAe 是否允许走「AE 存储优先」分支
     */
    public static void handleDrops(Player player, List<ItemStack> drops, ItemStack tool, Vec3 dropOrigin,
                                   boolean allowAe) {
        boolean isAE2Loaded = ModList.get().isLoaded("ae2");
        boolean magnetEnabled = UComponentUtils.isBeefMagnetEnabled(tool);

        // 自动熔炼：在入库/落地之前先把掉落物炼一遍，
        // 这样后续的 AE 优先与磁力拾取拿到的都是成品，不需要各自再处理。
        drops = AutoSmeltHelper.smeltDrops(player.level(), drops, tool);

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;

            // 1. 尝试存入 AE2 (内部处理跨维度)
            if (allowAe
                    && isAE2Loaded
                    && UComponentUtils.isAEStoragePriorityEnabled(tool)
                    && tool.has(UComponents.WIRELESS_LINK_TARGET.get())) {
                try {
                    int inserted = AE2Compat.tryInsertToLinkedGrid(tool, player, drop);
                    if (inserted > 0) {
                        drop.shrink(inserted);
                    }
                } catch (Throwable ignored) {
                }
            }

            if (drop.isEmpty()) continue;

            // 2. 磁力开启：剩余进入背包
            if (magnetEnabled) {
                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }
                continue;
            }

            // 3. 磁力关闭：剩余留在原地走原版拾取
            dropAtOrigin(player.level(), dropOrigin, drop);
        }
    }

    /**
     * 在指定位置生成一个掉落实体，让它走原版拾取流程。
     *
     * <p>仅在服务端生效；客户端调用会被忽略。</p>
     *
     * @param level  世界
     * @param origin 落点
     * @param stack  掉落物
     */
    private static void dropAtOrigin(Level level, Vec3 origin, ItemStack stack) {
        if (stack.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemEntity entity = new ItemEntity(serverLevel, origin.x, origin.y, origin.z, stack.copy());
        serverLevel.addFreshEntity(entity);
    }

    /**
     * 普通连锁模式下查找需要破坏的方块
     *
     * @param originPos   原点位置
     * @param originState 原点方块状态
     * @param level       世界
     * @param stack       工具
     * @param forceMining 是否为强制挖掘模式
     * @param shape       连锁形状
     * @param face        玩家点击的面
     * @param player      触发连锁的玩家，用于取该玩家自己的等价组
     * @return 需要破坏的方块列表
     */
    static List<BlockPos> scanBlocksToMine(BlockPos originPos, BlockState originState, Level level, ItemStack stack,
                                           boolean forceMining, boolean enhanced, ChainMiningShapes shape,
                                           Direction face, Player player) {
        return scanBlocks(originPos, originState, level, stack, forceMining, enhanced, true, shape, face, player);
    }

    /**
     * 右键连锁用的扫描：等价组 / 范围 / 数量上限与连锁挖掘完全共用，
     * 区别只是不做「工具能否挖掘该方块」的门槛判定
     * （右键要作用的是耕地、原木、作物这些「工具动作能生效」的方块，
     * 而它们未必是当前工具能正确采集的方块）。
     *
     * @param originPos   原点位置
     * @param originState 原点方块状态
     * @param level       世界
     * @param enhanced    是否增强连锁（增强模式取消相邻限制，改为范围内扫描）
     * @param shape       连锁形状
     * @param face        玩家点击的面
     * @param player      触发连锁的玩家
     * @return 连锁范围（含原点，按挖掘顺序排列）
     */
    static List<BlockPos> scanBlocksForUse(BlockPos originPos, BlockState originState, Level level, boolean enhanced,
                                           ChainMiningShapes shape, Direction face, Player player) {
        return scanBlocks(originPos, originState, level, ItemStack.EMPTY, false, enhanced, false, shape, face, player);
    }

    /**
     * 连锁扫描主流程
     *
     * @param requireMineable true = 挖矿语义（额外做工具等级 / 强制挖掘黑名单判定），
     *                        false = 右键语义（只看等价组与范围）
     */
    private static List<BlockPos> scanBlocks(BlockPos originPos, BlockState originState, Level level, ItemStack stack,
                                             boolean forceMining, boolean enhanced, boolean requireMineable,
ChainMiningShapes shape, Direction face, Player player) {
        if (forceMining && isForceMiningBlacklisted(originState)) {
            return List.of();
        }
        if (shape != null && !shape.usesBuiltinScan()) {
            return scanShapeBlocks(originPos, originState, level, stack, forceMining, requireMineable, shape, face, player);
        }
        if (enhanced) {
            return scanAreaBlocks(originPos, originState, level, stack, forceMining, requireMineable, player);
        }
        // 最大连锁数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        // 同类方块判定：玩家自己的等价组命中时按组匹配，否则退回严格同方块
        ChainEquivalence equivalence = ChainGroupManager.equivalenceFor(player, originState.getBlock());
        List<BlockPos> blocksToMine = new ArrayList<>(maxBlocks);

        // 检查原点方块是否可以被挖掘（工具等级检查）
        if (requireMineable && !canMineBlock(originState, stack, forceMining)) {
            return blocksToMine; // 返回空列表
        }

        Queue<BlockPos> queue = new LinkedList<>();
        LongOpenHashSet visited = new LongOpenHashSet(maxBlocks * 2);

        queue.add(originPos);
        visited.add(originPos.asLong());

        while (!queue.isEmpty() && blocksToMine.size() < maxBlocks) {
            BlockPos currentPos = queue.poll();
            blocksToMine.add(currentPos);

            int cx = currentPos.getX();
            int cy = currentPos.getY();
            int cz = currentPos.getZ();

            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        int nx = cx + x;
                        int ny = cy + y;
                        int nz = cz + z;

                        // 1. 距离快速过滤
                        if (Math.abs(nx - originPos.getX()) > rangeX ||
                                Math.abs(ny - originPos.getY()) > rangeY ||
                                Math.abs(nz - originPos.getZ()) > rangeZ) continue;

                        // 2. 访问过滤
                        long nLong = BlockPos.asLong(nx, ny, nz);
                        if (visited.contains(nLong)) continue;

                        // 3. 状态检查
                        BlockPos neighborPos = new BlockPos(nx, ny, nz);
                        BlockState nextState = level.getBlockState(neighborPos);

                        if (equivalence.matches(nextState)) {
                            if ((!requireMineable || canMineBlock(nextState, stack, forceMining))
                                    && !(forceMining && isForceMiningBlacklisted(nextState))) {
                                visited.add(nLong);
                                queue.add(neighborPos);
                            }
                        }
                    }
                }
            }
        }

        // 使用欧几里得距离平方进行排序
        blocksToMine.sort(Comparator.comparingDouble(pos -> pos.distSqr(originPos)));

        return blocksToMine;
    }

    public static boolean isForceMiningBlacklisted(BlockState state) {
        return ConfigManager.isBeefToolForceMiningBlockBlacklisted(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /**
     * 按指定形状查找需要破坏的方块。
     *
     * <p>形状负责布局，某个坐标是否可用仍由 {@link ChainMiningShapeContext#check} 依据等价组、
     * 范围、工具可采集与强制挖掘黑名单判定，因此形状实现无需重复这些规则。
     *
     * <p>返回序列保持形状给出的挖掘顺序并截断到数量上限；布局重叠导致的重复坐标会被去重，
     * 避免同一格被破坏两次。原点方块不可采集时返回空列表，与相邻扩散扫描的既有语义一致。
     *
     * @param originPos       原点位置
     * @param originState     原点方块状态
     * @param level           世界
     * @param stack           工具
     * @param forceMining     是否为强制挖掘模式
     * @param requireMineable 是否需要「工具可采集」门槛
     * @param shape           连锁形状
     * @param face            玩家点击的面
     * @param player          触发连锁的玩家
     * @return 需要破坏的方块列表
     */
    private static List<BlockPos> scanShapeBlocks(BlockPos originPos, BlockState originState, Level level,
                                                  ItemStack stack, boolean forceMining, boolean requireMineable,
                                                  ChainMiningShapes shape, Direction face, Player player) {
        if (requireMineable && !canMineBlock(originState, stack, forceMining)) {
            return List.of();
        }

        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 同类方块判定：玩家自己的等价组命中时按组匹配，否则退回严格同方块
        ChainEquivalence equivalence = ChainGroupManager.equivalenceFor(player, originState.getBlock());
        ChainMiningShapeContext context = new ChainMiningShapeContext(
                level, originPos, originState, face, player, stack, equivalence,
                forceMining, requireMineable, maxBlocks,
                ConfigManager.getChainMiningRangeX(), ConfigManager.getChainMiningRangeY(),
                ConfigManager.getChainMiningRangeZ());

        List<BlockPos> shapeBlocks = shape.getBlocks(context);
        List<BlockPos> blocksToMine = new ArrayList<>(Math.min(shapeBlocks.size(), maxBlocks));
        LongOpenHashSet seen = new LongOpenHashSet(Math.max(16, shapeBlocks.size() * 2));
        for (BlockPos pos : shapeBlocks) {
            if (blocksToMine.size() >= maxBlocks) {
                break;
            }
            if (!seen.add(pos.asLong())) {
                continue;
            }
            if (forceMining && isForceMiningBlacklisted(level.getBlockState(pos))) {
                continue;
            }
            blocksToMine.add(pos);
        }

        return blocksToMine;
    }

    /**
     * 增强连锁模式下查找需要破坏的方块
     * 增强连锁：取消相邻才能连锁的限制
     *
     * @param originPos   原点位置
     * @param originState 原点方块状态
     * @param level       世界
     * @param stack       工具
     * @param forceMining 是否为强制挖掘模式
     * @param player      触发连锁的玩家，用于取该玩家自己的等价组
     * @return 需要破坏的方块列表
     */
    private static List<BlockPos> scanAreaBlocks(BlockPos originPos, BlockState originState, Level level,
                                                 ItemStack stack, boolean forceMining, boolean requireMineable,
                                                 Player player) {
        // 最大连锁数量（包含原点方块）
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        // 同类方块判定：玩家自己的等价组命中时按组匹配，否则退回严格同方块
        ChainEquivalence equivalence = ChainGroupManager.equivalenceFor(player, originState.getBlock());
        List<BlockPos> blocksToMine = new ArrayList<>(maxBlocks);

        // 增强连锁：直接在范围内扫描所有相同方块，不需要相邻限制
        // 先收集范围内全部匹配方块，再按距离排序、截断到上限，保证保留的是"最近"的方块而非扫描顺序靠前的。
        for (int x = -rangeX; x <= rangeX; x++) {
            for (int y = -rangeY; y <= rangeY; y++) {
                for (int z = -rangeZ; z <= rangeZ; z++) {
                    int nx = originPos.getX() + x;
                    int ny = originPos.getY() + y;
                    int nz = originPos.getZ() + z;

                    BlockPos targetPos = new BlockPos(nx, ny, nz);
                    BlockState nextState = level.getBlockState(targetPos);

                    if (equivalence.matches(nextState)) {
                        if ((!requireMineable || canMineBlock(nextState, stack, forceMining))
                                && !(forceMining && isForceMiningBlacklisted(nextState))) {
                            blocksToMine.add(targetPos);
                        }
                    }
                }
            }
        }

        // 使用欧几里得距离平方进行排序（最近优先）
        blocksToMine.sort(Comparator.comparingDouble(pos -> pos.distSqr(originPos)));

        // 排序后截断到上限，确保保留距离最近的方块
        if (blocksToMine.size() > maxBlocks) {
            return new ArrayList<>(blocksToMine.subList(0, maxBlocks));
        }

        return blocksToMine;
    }

    /**
     * 通过方块自身的破坏回调破坏方块，并收集本次新生成的掉落实体。
     * 这样可以保留其他模组在破坏回调中实现的特殊掉落逻辑，同时仍然让掉落物进入背包。
     *
     * <p>破坏按原版 {@code ServerPlayerGameMode#destroyBlock} 的次序执行，三步各自都可能产出掉落：</p>
     * <ol>
     *   <li>{@code playerWillDestroy}：不产出掉落，其返回值才是后续流程使用的方块状态。</li>
     *   <li>{@code onDestroyedByPlayer}：由方块自身决定是否移除。NeoForge 的默认实现即
     *       {@code Level#removeBlock}，因而会触发 {@code onRemove}，容器类方块通常在此掉落内容物；
     *       重写该方法的模组可以接管本次移除并返回 {@code false}（如集成动力线缆按准星命中的部件拆除，
     *       整块方块予以保留）。返回 {@code true} 时随之调用 {@code Block#destroy} 收尾。</li>
     *   <li>{@code playerDestroy}：仅在方块确被移除时执行，经 {@code getDrops} 产出掉落表物品，
     *       模组也可在此直接追加自定义掉落。</li>
     * </ol>
     *
     * <p>缺少第 2 步时，任何依赖 {@code onDestroyedByPlayer} 决定移除方式的模组都会失效：
     * 其接管逻辑被完全跳过，方块按整块移除处理，且不再有机会掉落自身部件。</p>
     *
     * <p>若方块既重写了 {@code getDrops} 并返回内容物，又在 {@code onRemove} 中掉落内容物，
     * 同一批物品会被两条路径各生成一次。此处以 {@code playerDestroy} 阶段的掉落为基准，
     * 移除阶段中与之完全相同的条目视为重复并丢弃，避免采集端把两份都收走；
     * 配对要求物品、组件与数量三者同时一致，因此方块自身重复产出的同类掉落不受影响。</p>
     *
     * @param level  世界
     * @param pos    方块位置
     * @param state  方块状态
     * @param player 玩家
     * @param tool   工具
     * @param force  是否为强制挖掘：为真时方块对本次移除的拒绝不成立，将补做一次整块移除
     * @return 本次破坏的结果（是否产生了有效破坏 + 掉落物列表）
     */
    static BreakOutcome destroyBlockAndCollectDrops(ServerLevel level, BlockPos pos, BlockState state,
                                                    Player player, ItemStack tool, boolean force) {
        // 采用破坏前后 2 格膨胀范围内 ItemEntity 的差集来收集本次掉落，
        // 2 格可覆盖部分模组把掉落物生成在方块中心 1 格外的情况；before/after 差集保证不会误收邻近方块的已有掉落。
        AABB area = new AABB(pos).inflate(2.0);
        Set<UUID> before = level.getEntitiesOfClass(ItemEntity.class, area)
                                .stream()
                                .map(Entity::getUUID)
                                .collect(Collectors.toSet());
        Set<UUID> experienceBefore = level.getEntitiesOfClass(ExperienceOrb.class, area)
                                          .stream()
                                          .map(Entity::getUUID)
                                          .collect(Collectors.toSet());

        Block block = state.getBlock();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        // 第一步：playerWillDestroy 不产出掉落，其返回值才是后续流程使用的方块状态。
        // 该回调抛出即视为模组拒绝这次移除，尊重它并放弃本格的后续步骤。
        BlockState currentState;
        try {
            currentState = block.playerWillDestroy(level, pos, state, player);
        } catch (Throwable refusal) {
            reportRefusedRemoval(block, pos, "playerWillDestroy", refusal);
            return BreakOutcome.REFUSED;
        }

        // 第二步：移除阶段交给方块自身决定。重写 onDestroyedByPlayer 的模组可在此按部件拆除
        // 并返回 false；此时方块保留，但该模组通常已产出掉落，需一并收走。
        boolean removed;
        try {
            boolean canHarvest = currentState.canHarvestBlock(level, pos, player);
            removed = currentState.onDestroyedByPlayer(level, pos, player, canHarvest,
                    level.getFluidState(pos));
            if (removed) {
                currentState.getBlock().destroy(level, pos, currentState);
            }
        } catch (Throwable refusal) {
            reportRefusedRemoval(block, pos, "onDestroyedByPlayer", refusal);
            return BreakOutcome.REFUSED;
        }

        Set<UUID> consumed = ConcurrentHashMap.newKeySet();
        List<ItemStack> removalDrops = collectNewDrops(level, area, before, consumed);

        if (!removed) {
            // 方块未被移除时存在两种情形，必须区分对待：
            // 一是模组接管了本次破坏并按自身语义完成拆除（如集成动力线缆按准星命中的部件拆除），
            // 此时已有掉落产出，该结果即本次破坏的最终产物；
            // 二是模组明确拒绝移除，未产出任何掉落。
            // 仅后者属于强制挖掘不接受的范围，补做一次整块移除；对前者补做强拆会连带移除
            // 该位置剩余部件，使组合体整块消失。
            // 常规挖掘一律尊重该结果——方块仍留在原地，按掉落表产出物品等同于凭空生成。
            if (hasNoValidDrops(removalDrops) && force && !level.getBlockState(pos).isAir()) {
                try {
                    level.removeBlock(pos, false);
                    if (level.getBlockState(pos).isAir()) {
                        currentState.getBlock().destroy(level, pos, currentState);
                    }
                    removalDrops.addAll(collectNewDrops(level, area, before, consumed));
                } catch (Throwable refusal) {
                    reportRefusedRemoval(block, pos, "forced removal", refusal);
                }
            }
            if (!level.getBlockState(pos).isAir()) {
                discardNewExperience(level, area, experienceBefore);
                return hasNoValidDrops(removalDrops)
                        ? BreakOutcome.REFUSED
                        : new BreakOutcome(true, false, removalDrops);
            }
            removed = true;
        }

        // 第三步：方块已移除，按掉落表产出物品。该回调抛出不影响方块已被移除的事实，仅上报。
        try {
            currentState.getBlock().playerDestroy(level, player, pos, currentState, blockEntity, tool);
        } catch (Throwable refusal) {
            reportRefusedRemoval(block, pos, "playerDestroy", refusal);
        }
        List<ItemStack> callbackDrops = collectNewDrops(level, area, before, consumed);

        // 经验球统一在两个阶段结束后清理：本流程自行结算经验，两阶段新生成的经验球都不应留在世界上。
        discardNewExperience(level, area, experienceBefore);

        return new BreakOutcome(true, true, mergePhaseDrops(callbackDrops, removalDrops));
    }

    /**
     * 清理本次破坏新生成的经验球。
     *
     * <p>经验由调用方按 {@code getExpDrop} 自行结算，这些经验球若留在世界上会与结算值重复，
     * 且会被下一次差集收集误计为掉落。</p>
     */
    private static void discardNewExperience(ServerLevel level, AABB area, Set<UUID> experienceBefore) {
        for (ExperienceOrb experienceOrb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {
            if (!experienceBefore.contains(experienceOrb.getUUID())) {
                experienceOrb.discard();
            }
        }
    }

    /**
     * 收集区域内自 {@code before} 之后新生成的 ItemEntity，取走其内容并移除实体。
     *
     * @param consumed 跨阶段累积的已收走实体 id，避免同一实体被两个阶段重复计入
     * @return 本次新收走的掉落物列表
     */
    private static List<ItemStack> collectNewDrops(ServerLevel level, AABB area, Set<UUID> before,
                                                   Set<UUID> consumed) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, area)) {
            UUID id = entity.getUUID();
            if (before.contains(id) || !consumed.add(id)) {
                continue;
            }
            ItemStack drop = entity.getItem().copy();
            if (!drop.isEmpty()) {
                drops.add(drop);
            }
            entity.discard();
        }
        return drops;
    }

    /**
     * 合并回调阶段与移除阶段的掉落，剔除移除阶段中与回调阶段完全重复的条目。
     *
     * <p>重复来源：方块同时重写 {@code getDrops} 并返回内容物、又在 {@code onRemove} 中掉落内容物时，
     * 同一批物品被两条路径各生成一次。仅当物品、组件与数量三者同时一致时才判定为重复，
     * 且每个基准条目至多抵消一个候选条目。</p>
     */
    private static List<ItemStack> mergePhaseDrops(List<ItemStack> callbackDrops, List<ItemStack> removalDrops) {
        List<ItemStack> remaining = new ArrayList<>(callbackDrops);
        List<ItemStack> merged = new ArrayList<>(callbackDrops);
        for (ItemStack candidate : removalDrops) {
            int matched = indexOfSameStack(remaining, candidate);
            if (matched >= 0) {
                remaining.remove(matched);
                continue;
            }
            merged.add(candidate);
        }
        return merged;
    }

    /** 在池中查找与候选完全一致的条目（物品、组件、数量均相同）；未命中返回 -1。 */
    private static int indexOfSameStack(List<ItemStack> pool, ItemStack candidate) {
        for (int i = 0; i < pool.size(); i++) {
            ItemStack existing = pool.get(i);
            if (existing.getCount() == candidate.getCount()
                    && ItemStack.isSameItemSameComponents(existing, candidate)) {
                return i;
            }
        }
        return -1;
    }

    /** 方块自身拒绝被移除：按类型去重上报，避免一次连锁挖掘产生大量重复日志。 */
    private static void reportRefusedRemoval(Block block, BlockPos pos, String callback, Throwable refusal) {
        if (REPORTED_REFUSED_REMOVALS.add(block)) {
            LOGGER.warn("Block {} refused removal at {} ({} threw {}: {}); skipping it, chain mining continues",
                    BuiltInRegistries.BLOCK.getKey(block), pos, callback,
                    refusal.getClass().getSimpleName(), refusal.getMessage(), refusal);
        }
    }

    /**
     * 单个方块在连锁挖掘中出错时的保护性上报：异常拦截于本格，后续方块继续挖掘。
     * 同样是按方块类型去重。
     */
    static void reportBlockBreakFailure(BlockState state, BlockPos pos, Throwable failure) {
        Block block = state.getBlock();
        if (REPORTED_BREAK_FAILURES.add(block)) {
            LOGGER.warn("Failed to break {} at {}; skipping it, chain mining continues",
                    BuiltInRegistries.BLOCK.getKey(block), pos, failure);
        }
    }

    /**
     * 获取玩家指向的方块位置
     *
     * @param player 玩家
     * @return 方块位置，如果没有指向方块则返回null
     */
    static BlockPos getTargetBlockPos(Player player) {
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        HitResult hitResult = player.pick(reach, 0.0f, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hitResult).getBlockPos();
        }
        return null;
    }

    /**
     * 获取形状推进所依据的面。
     *
     * <p>隧道类形状按「该面的反方向」推进，因此命中方块时取点击面，其反方向即远离玩家、
     * 指向方块内部的方向，与玩家对连锁走向的预期一致。
     *
     * <p>未命中任何方块时没有真实点击面可用，此时退化为玩家朝向的反方向：该方向的反方向
     * 恰为玩家朝向，使推进仍沿视线向前。若直接返回玩家朝向，推进方向会反转成玩家背后，
     * 与视线方向相反。
     *
     * @param player 玩家
     * @return 命中面，未命中时为玩家朝向的反方向
     */
    static Direction getTargetFace(Player player) {
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        HitResult hitResult = player.pick(reach, 0.0f, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hitResult).getDirection();
        }
        return player.getDirection().getOpposite();
    }
}
