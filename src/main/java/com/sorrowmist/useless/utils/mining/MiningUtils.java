package com.sorrowmist.useless.utils.mining;

import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.DraconicEvolutionCompat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ChainEquivalence;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.UComponentUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
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
     * 一次破坏尝试的结果：方块是否真的被移除，以及本次新生成的掉落。
     *
     * <p>和 {@link MiningResult} 的区别：这个只描述「破坏回调 + removeBlock」这一步，
     * 不带经验等上层语义，专门用来表达「模组拒绝了这次移除」。</p>
     */
    record BreakOutcome(boolean removed, List<ItemStack> drops) {
        private static final BreakOutcome REFUSED = new BreakOutcome(false, List.of());
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

    static boolean canMineBlock(BlockState state, ItemStack tool, boolean forceMining) {
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

        // 这条路径是从方块破坏事件里直接进来的：任何一个模组的破坏回调抛异常，
        // 异常都会一路冒回事件总线，把整 tick 打断。所以在这里就把异常挡住并上报。
        try {
            MiningResult result = forceMining
                    ? forceMineBlock(level, pos, state, player, tool)
                    : mineBlock(level, pos, state, player, tool);
            handleDrops(player, result.drops(), tool, Vec3.atCenterOf(pos));
            if (result.experience() > 0) {
                player.giveExperiencePoints(result.experience());
            }
        } catch (Throwable failure) {
            reportBlockBreakFailure(state, pos, failure);
        }
    }

    static MiningResult mineBlock(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (state.isAir() || !canMineBlock(state, tool, false)) {
            return MiningResult.NOT_MINED;
        }

        int experience = getExperience(level, pos, state, player, tool);
        BreakOutcome outcome = destroyBlockAndCollectDrops(level, pos, state, player, tool);
        return outcome.removed()
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
            // 被方块自身拒绝移除时不发兜底掉落，否则等于凭空刷出一个核心物品。
            return destroyBlockAndCollectDrops(level, pos, state, player, tool).removed()
                    ? new MiningResult(fallbackDrops, 0, true)
                    : MiningResult.NOT_MINED;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> naturalDrops = Block.getDrops(state, level, pos, blockEntity, player, tool);
        boolean useFallback = hasNoValidDrops(naturalDrops);
        List<ItemStack> fallbackDrops = useFallback
                ? getForcedFallbackDrops(state, level, pos)
                : List.of();
        BreakOutcome outcome = destroyBlockAndCollectDrops(level, pos, state, player, tool);
        if (!outcome.removed()) {
            return MiningResult.NOT_MINED;
        }
        List<ItemStack> drops = selectForcedDrops(naturalDrops, outcome.drops(), fallbackDrops);
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
        BlockEntity blockEntity = world.getBlockEntity(pos);

        // 同样要把模组回调的异常挡在事件链之外：数据能源的三位一体样板核心在状态未就绪时
        // getDrops / playerWillDestroy 都会抛，异常冒回事件总线就会打断整 tick。
        try {
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, tool);
            handleDrops(player, drops, tool, Vec3.atCenterOf(pos));

            world.destroyBlock(pos, false, player);
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

            // 如果还没合并完（或者组件不同/空间不够），作为新的一堆加入
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
     *   <li>AE 存储优先开启且绑定了无线访问点：先尝试存入 AE，塞不下的继续下一步。</li>
     *   <li>范围磁力开启：剩余物品进背包，背包满则掉在玩家脚下。</li>
     *   <li>范围磁力关闭：剩余物品在 {@code dropOrigin} 处落地，走原版拾取。</li>
     * </ul>
     *
     * <p>注意 AE 存储优先是独立于范围磁力生效的：即使磁力关闭，只要 AE 优先开启，
     * 产物仍会优先存入 AE，只有 AE 塞不下的部分才落地。</p>
     *
     * @param player     玩家
     * @param drops      掉落物列表
     * @param tool       工具
     * @param dropOrigin 磁力关闭时的落地点（AE 塞不下的部分也会落在这里）
     */
    public static void handleDrops(Player player, List<ItemStack> drops, ItemStack tool, Vec3 dropOrigin) {
        boolean isAE2Loaded = ModList.get().isLoaded("ae2");
        boolean magnetEnabled = UComponentUtils.isBeefMagnetEnabled(tool);

        // 自动熔炼：在入库/落地之前先把掉落物炼一遍，
        // 这样后续的 AE 优先与磁力拾取拿到的都是成品，不需要各自再处理。
        drops = AutoSmeltHelper.smeltDrops(player.level(), drops, tool);

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;

            // 1. 尝试存入 AE2 (内部处理跨维度)
            if (isAE2Loaded
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
     * @return 需要破坏的方块列表
     */
    static List<BlockPos> scanBlocksToMine(BlockPos originPos, BlockState originState, Level level, ItemStack stack,
                                           boolean forceMining, boolean enhanced) {
        return scanBlocks(originPos, originState, level, stack, forceMining, enhanced, true);
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
     * @return 连锁范围（含原点，按距离从近到远排序）
     */
    static List<BlockPos> scanBlocksForUse(BlockPos originPos, BlockState originState, Level level, boolean enhanced) {
        return scanBlocks(originPos, originState, level, ItemStack.EMPTY, false, enhanced, false);
    }

    /**
     * 连锁扫描主流程
     *
     * @param requireMineable true = 挖矿语义（额外做工具等级 / 强制挖掘黑名单判定），
     *                        false = 右键语义（只看等价组与范围）
     */
    private static List<BlockPos> scanBlocks(BlockPos originPos, BlockState originState, Level level, ItemStack stack,
                                             boolean forceMining, boolean enhanced, boolean requireMineable) {
        if (forceMining && isForceMiningBlacklisted(originState)) {
            return List.of();
        }
        if (enhanced) {
            return scanAreaBlocks(originPos, originState, level, stack, forceMining, requireMineable);
        }
        // 最大连锁数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        // 同类方块判定：命中配置的等价组时按组匹配，否则退回严格同方块
        ChainEquivalence equivalence = ConfigManager.getChainMiningEquivalence(originState.getBlock());
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

    private static boolean isForceMiningBlacklisted(BlockState state) {
        return ConfigManager.isBeefToolForceMiningBlockBlacklisted(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()));
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
     * @return 需要破坏的方块列表
     */
    private static List<BlockPos> scanAreaBlocks(BlockPos originPos, BlockState originState, Level level,
                                                 ItemStack stack, boolean forceMining, boolean requireMineable) {
        // 最大连锁数量（包含原点方块）
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        // 同类方块判定：命中配置的等价组时按组匹配，否则退回严格同方块
        ChainEquivalence equivalence = ConfigManager.getChainMiningEquivalence(originState.getBlock());
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
     * 通过方块自身的破坏回调破坏方块，并收集本次新生成的掉落实体
     * 这样可以保留其他模组在 playerDestroy 中实现的特殊掉落逻辑，同时仍然让掉落物进入背包。
     *
     * @param level  世界
     * @param pos    方块位置
     * @param state  方块状态
     * @param player 玩家
     * @param tool   工具
     * @return 本次破坏的结果（方块是否真的被移除 + 掉落物列表）
     */
    static BreakOutcome destroyBlockAndCollectDrops(ServerLevel level, BlockPos pos, BlockState state,
                                                    Player player, ItemStack tool) {
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

        if (!destroyBlockWithoutDrops(level, pos, state, player, tool)) {
            // 方块被模组拒绝移除：这次没有产生任何东西，也就没有掉落可收。
            return BreakOutcome.REFUSED;
        }

        for (ExperienceOrb experienceOrb : level.getEntitiesOfClass(ExperienceOrb.class, area)) {
            if (!experienceBefore.contains(experienceOrb.getUUID())) {
                experienceOrb.discard();
            }
        }

        List<ItemStack> drops = new ArrayList<>();
        level.getEntitiesOfClass(ItemEntity.class, area).stream()
             .filter(entity -> !before.contains(entity.getUUID()))
             .forEach(entity -> {
                 ItemStack drop = entity.getItem().copy();
                 if (!drop.isEmpty()) {
                     drops.add(drop);
                 }
                 entity.discard();
             });
        return new BreakOutcome(true, drops);
    }

    /**
     * 执行方块破坏回调并移除方块
     * 用于集中走 playerWillDestroy 和 playerDestroy，避免直接 removeBlock 跳过模组自定义破坏逻辑。
     *
     * <p><b>两个回调都可能抛</b>：数据能源的三位一体样板核心在持久化状态读不出来时，
     * {@code playerWillDestroy} / {@code getDrops} 会抛 {@link IllegalStateException}
     * （并在自己的日志里写明「拒绝移除 / 拒绝给出空白核心掉落」）。原来的写法一旦抛异常，
     * 后面的 {@code removeBlock} 就永远走不到 —— 方块留在原地，而异常一路冒到事件总线，
     * 把整批连锁挖掘一起打断。</p>
     *
     * <p>现在的语义是：<b>回调抛异常 = 模组拒绝这次移除，尊重它</b>（不强行 removeBlock，
     * 否则对方要保留的样板 / 待输出内容会被静默丢掉），但把异常收在这一层，
     * 按方块类型去重后只报一次，剩下的方块照常挖。</p>
     *
     * @return {@code true} 表示方块已被移除；{@code false} 表示被方块自身的回调拒绝
     */
    static boolean destroyBlockWithoutDrops(ServerLevel level, BlockPos pos, BlockState state,
                                            Player player, ItemStack tool) {
        BlockEntity be = level.getBlockEntity(pos);
        Block block = state.getBlock();
        boolean accepted = true;
        try {
            block.playerWillDestroy(level, pos, state, player);
        } catch (Throwable refusal) {
            accepted = false;
            reportRefusedRemoval(block, pos, "playerWillDestroy", refusal);
        }
        try {
            block.playerDestroy(level, player, pos, state, be, tool);
        } catch (Throwable refusal) {
            accepted = false;
            reportRefusedRemoval(block, pos, "playerDestroy", refusal);
        }
        if (!accepted) {
            return false;
        }
        level.removeBlock(pos, false);
        return true;
    }

    /** 方块自身拒绝被移除：按类型去重上报，别让一次连锁挖掘灌出一屏栈。 */
    private static void reportRefusedRemoval(Block block, BlockPos pos, String callback, Throwable refusal) {
        if (REPORTED_REFUSED_REMOVALS.add(block)) {
            LOGGER.warn("Block {} refused removal at {} ({} threw {}: {}); skipping it, chain mining continues",
                    BuiltInRegistries.BLOCK.getKey(block), pos, callback,
                    refusal.getClass().getSimpleName(), refusal.getMessage(), refusal);
        }
    }

    /**
     * 单个方块在连锁挖掘里出错时的兜底上报：把异常挡在这一格，后面的方块继续挖。
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
}
