package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.DraconicEvolutionCompat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.UComponentUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
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
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MiningUtils {
    record MiningResult(List<ItemStack> drops, int experience, boolean mined) {
        private static final MiningResult NOT_MINED = new MiningResult(List.of(), 0, false);
    }

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

        MiningResult result = forceMining
                ? forceMineBlock(level, pos, state, player, tool)
                : mineBlock(level, pos, state, player, tool);
        handleDrops(player, result.drops(), tool);
        if (result.experience() > 0) {
            player.giveExperiencePoints(result.experience());
        }
    }

    static MiningResult mineBlock(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (state.isAir() || !tool.isCorrectToolForDrops(state)) {
            return MiningResult.NOT_MINED;
        }

        int experience = getExperience(level, pos, state, player, tool);
        List<ItemStack> drops = destroyBlockAndCollectDrops(level, pos, state, player, tool);
        return new MiningResult(drops, experience, true);
    }

    static MiningResult forceMineBlock(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        if (state.isAir()) {
            return MiningResult.NOT_MINED;
        }
        if (DraconicEvolutionCompat.isChaosCrystal(state)
                && DraconicEvolutionCompat.handleChaosCrystalBreak(level, pos, state, player)) {
            return new MiningResult(List.of(), 0, true);
        }

        int experience = getExperience(level, pos, state, player, tool);
        if (isSilkTouch(tool)) {
            List<ItemStack> fallbackDrops = getForcedFallbackDrops(state, level, pos);
            destroyBlockAndCollectDrops(level, pos, state, player, tool);
            return new MiningResult(fallbackDrops, 0, true);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        List<ItemStack> naturalDrops = Block.getDrops(state, level, pos, blockEntity, player, tool);
        boolean useFallback = hasNoValidDrops(naturalDrops);
        List<ItemStack> fallbackDrops = useFallback
                ? getForcedFallbackDrops(state, level, pos)
                : List.of();
        List<ItemStack> actualDrops = destroyBlockAndCollectDrops(level, pos, state, player, tool);
        List<ItemStack> drops = selectForcedDrops(naturalDrops, actualDrops, fallbackDrops);
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

        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, tool);
        handleDrops(player, drops, tool);

        world.destroyBlock(pos, false, player);
    }

    /**
     * 合并相同物品的堆叠
     *
     * @param items 要合并的物品列表
     * @return 合并后的物品列表
     */
    static List<ItemStack> mergeItemStacks(List<ItemStack> items) {
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
     * 处理掉落物（添加到背包或掉落）
     *
     * @param player 玩家
     * @param drops  掉落物列表
     */
    static void handleDrops(Player player, List<ItemStack> drops, ItemStack tool) {
        boolean isAE2Loaded = ModList.get().isLoaded("ae2");

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

            // 2. 剩余进入背包
            if (!drop.isEmpty()) {
                if (!player.getInventory().add(drop)) {
                    player.drop(drop, false);
                }
            }
        }
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
        if (enhanced) {
            return scanAreaBlocks(originPos, originState, level, stack, forceMining);
        }
        // 最大连锁数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        Block originBlock = originState.getBlock();
        List<BlockPos> blocksToMine = new ArrayList<>(maxBlocks);

        // 检查原点方块是否可以被挖掘（工具等级检查）
        if (!forceMining && !stack.isCorrectToolForDrops(originState)) {
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

                        if (nextState.is(originBlock)) {
                            if (forceMining || stack.isCorrectToolForDrops(nextState)) {
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
                                                 ItemStack stack, boolean forceMining) {
        // 最大连锁数量（包含原点方块）
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        Block originBlock = originState.getBlock();
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

                    if (nextState.is(originBlock)) {
                        if (forceMining || stack.isCorrectToolForDrops(nextState)) {
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
     * @return 本次破坏生成的掉落物列表
     */
    static List<ItemStack> destroyBlockAndCollectDrops(ServerLevel level, BlockPos pos, BlockState state,
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

        destroyBlockWithoutDrops(level, pos, state, player, tool);

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
        return drops;
    }

    /**
     * 执行方块破坏回调并移除方块
     * 用于集中走 playerWillDestroy 和 playerDestroy，避免直接 removeBlock 跳过模组自定义破坏逻辑。
     *
     * @param level  世界
     * @param pos    方块位置
     * @param state  方块状态
     * @param player 玩家
     * @param tool   工具
     */
    static void destroyBlockWithoutDrops(ServerLevel level, BlockPos pos, BlockState state,
                                         Player player, ItemStack tool) {
        BlockEntity be = level.getBlockEntity(pos);
        state.getBlock().playerWillDestroy(level, pos, state, player);
        state.getBlock().playerDestroy(level, player, pos, state, be, tool);
        level.removeBlock(pos, false);
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
