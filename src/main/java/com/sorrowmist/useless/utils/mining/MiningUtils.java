package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.UselessMod;
import com.sorrowmist.useless.config.ConfigManager;
import com.sorrowmist.useless.items.EndlessBeafItem;
import com.sorrowmist.useless.utils.pattern.PatternProviderEvent;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.level.BlockEvent;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 挖掘工具类，包含方块查找、破坏、掉落处理等核心逻辑。
 * 基于1.21 neoforge版本的挖掘逻辑重构。
 */
public class MiningUtils {

    // ==================== 方块查找 ====================

    /**
     * 普通连锁模式下查找需要破坏的方块（BFS广度优先搜索，需要相邻）
     *
     * @param originPos   原点位置
     * @param originState 原点方块状态
     * @param level       世界
     * @param stack       工具
     * @param forceMining 是否为强制挖掘模式
     * @return 需要破坏的方块列表（按距离排序）
     */
    public static List<BlockPos> findBlocksToMine(BlockPos originPos, BlockState originState, Level level,
                                                   ItemStack stack, boolean forceMining) {
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        Block originBlock = originState.getBlock();
        List<BlockPos> blocksToMine = new ArrayList<>(maxBlocks);

        if (!forceMining && !stack.isCorrectToolForDrops(originState)) {
            return blocksToMine;
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

                        if (Math.abs(nx - originPos.getX()) > rangeX ||
                                Math.abs(ny - originPos.getY()) > rangeY ||
                                Math.abs(nz - originPos.getZ()) > rangeZ) continue;

                        long nLong = BlockPos.asLong(nx, ny, nz);
                        if (visited.contains(nLong)) continue;

                        BlockPos neighborPos = new BlockPos(nx, ny, nz);
                        BlockState nextState = level.getBlockState(neighborPos);

                        if (nextState.getBlock() == originBlock) {
                            if (forceMining || stack.isCorrectToolForDrops(nextState)) {
                                visited.add(nLong);
                                queue.add(neighborPos);
                            }
                        }
                    }
                }
            }
        }

        blocksToMine.sort(Comparator.comparingDouble(pos -> pos.distSqr(originPos)));
        return blocksToMine;
    }

    /**
     * 增强连锁模式下查找需要破坏的方块（直接扫描范围，不需要相邻）
     *
     * @param originPos   原点位置
     * @param originState 原点方块状态
     * @param level       世界
     * @param stack       工具
     * @param forceMining 是否为强制挖掘模式
     * @return 需要破坏的方块列表（按距离排序）
     */
    public static List<BlockPos> findBlocksToMineEnhanced(BlockPos originPos, BlockState originState, Level level,
                                                           ItemStack stack, boolean forceMining) {
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
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

                    if (nextState.getBlock() == originBlock) {
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

    // ==================== 方块破坏 ====================

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
    public static void processBlockBreak(ServerLevel level, BlockPos pos, BlockState state, Player player,
                                          ItemStack tool, boolean forceMining) {
        // 破坏前用掉落表判断方块是否有自然掉落，避免掉落实体被其他模组吸收导致误判
        boolean hasNaturalDrops = !forceMining || blockHasNaturalDrops(level, pos, state, player, tool);
        List<ItemStack> fallbackDrops = (forceMining && !hasNaturalDrops) ? getForcedFallbackDrops(state, level, pos) : Collections.emptyList();
        List<ItemStack> drops = destroyBlockAndCollectDrops(level, pos, state, player, tool);
        // 仅当方块本身确实无自然掉落时才使用兜底，防止能正常挖出物品的方块被额外补出方块本身
        if (forceMining && !hasNaturalDrops && hasNoValidDrops(drops) && !hasNoValidDrops(fallbackDrops)) {
            drops = fallbackDrops;
        }
        handleDrops(player, drops, tool);

        // 经验处理（时运模式检查）
        if (!isSilkTouchMode(tool)) {
            state.getBlock().popExperience(level, pos, state.getBlock().getExpDrop(state, level, level.random, pos, 0, 0));
        }
    }

    /**
     * 强制挖掘模式兜底掉落物：当方块正常破坏没有有效掉落时，返回方块本身
     * 兜底掉落的语义是"复制目标方块本身"，因此只要存在方块实体就附加其 NBT，
     * 保证掉落物与目标方块 NBT 完全一致（不区分精准采集/时运）。
     */
    static List<ItemStack> getForcedFallbackDrops(BlockState state, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty() || stack.getItem() == Items.AIR) {
            return Collections.emptyList();
        }

        if (be != null) {
            net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata();
            if (!tag.isEmpty()) {
                net.minecraft.nbt.CompoundTag itemTag = stack.getOrCreateTag();
                itemTag.put("BlockEntityTag", tag);
            }
        }
        return Collections.singletonList(stack);
    }

    /**
     * 检查掉落物列表是否均为无效
     */
    public static boolean hasNoValidDrops(List<ItemStack> drops) {
        return drops.isEmpty() || drops.stream().allMatch(stack -> stack.isEmpty() || stack.getItem() == Items.AIR);
    }

    /**
     * 检测掉落物是否全为"降级方块物品"（即非方块本身的其他 BlockItem）。
     * <p>
     * AE2 系列母岩、数据陨石等方块在非精准采集下 loot table 返回的是降级版方块物品，
     * 而非方块本身——这种情况应视为"无正确自身掉落"，触发强制挖掘兜底，让玩家获得方块本身。
     *
     * @param drops     掉落物列表
     * @param selfBlock 被破坏的方块
     * @return 当所有掉落物均为 BlockItem 且没有一个是方块本身的物品时返回 true
     */
    static boolean dropsAreDowngradedBlocks(List<ItemStack> drops, Block selfBlock) {
        if (hasNoValidDrops(drops)) return false;
        Item selfItem = selfBlock.asItem();
        List<ItemStack> valid = drops.stream().filter(s -> !s.isEmpty() && !s.is(Items.AIR)).toList();
        if (valid.isEmpty()) return false;
        // 所有掉落物必须是 BlockItem，且没有一个是方块自身
        return valid.stream().allMatch(s -> s.getItem() instanceof BlockItem)
                && valid.stream().noneMatch(s -> s.is(selfItem));
    }

    /**
     * 在破坏前判断方块用指定工具是否会产生自然掉落。
     * <p>
     * 直接查询方块的掉落表（Block.getDrops），不依赖破坏后世界中的 ItemEntity。
     * 这样即使其他模组（如 FTB Ultimine 连锁挖掘）在破坏期间吸收掉落实体，也能正确判断方块本身是否有掉落，
     * 避免把"实体被吸收"误判为"无掉落"从而错误触发强制挖掘兜底，导致重复掉落。
     */
    static boolean blockHasNaturalDrops(ServerLevel level, BlockPos pos, BlockState state, Player player, ItemStack tool) {
        BlockEntity be = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, player, tool);
        return !hasNoValidDrops(drops);
    }

    /**
     * 通过方块自身的破坏回调破坏方块，并收集本次新生成的掉落实体
     */
    public static List<ItemStack> destroyBlockAndCollectDrops(ServerLevel level, BlockPos pos, BlockState state,
                                                               Player player, ItemStack tool) {
        // 采用破坏前后 2 格膨胀范围内 ItemEntity 的差集来收集本次掉落，
        // 2 格可覆盖部分模组把掉落物生成在方块中心 1 格外的情况；before/after 差集保证不会误收邻近方块的已有掉落。
        AABB area = new AABB(pos).inflate(2.0);
        Set<UUID> before = level.getEntitiesOfClass(ItemEntity.class, area)
                .stream()
                .map(Entity::getUUID)
                .collect(Collectors.toSet());

        destroyBlockWithoutDrops(level, pos, state, player, tool);

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
     * 执行方块破坏回调并移除方块（不产生掉落物）
     */
    public static void destroyBlockWithoutDrops(ServerLevel level, BlockPos pos, BlockState state,
                                                 Player player, ItemStack tool) {
        BlockEntity be = level.getBlockEntity(pos);
        state.getBlock().playerWillDestroy(level, pos, state, player);
        state.getBlock().playerDestroy(level, player, pos, state, be, tool);
        level.removeBlock(pos, false);
    }

    // ==================== 掉落物处理 ====================

    /**
     * 合并相同物品的堆叠
     */
    public static List<ItemStack> mergeItemStacks(List<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) continue;

            boolean mergedFlag = false;
            for (ItemStack mergedItem : merged) {
                if (ItemStack.isSameItemSameTags(item, mergedItem)) {
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

            if (!mergedFlag && !item.isEmpty()) {
                merged.add(item.copy());
            }
        }
        return merged;
    }

    /**
     * 处理掉落物（添加到背包或掉落）
     */
    public static void handleDrops(Player player, List<ItemStack> drops) {
        handleDrops(player, drops, player.getMainHandItem());
    }

    /**
     * 处理掉落物（添加到背包或掉落），支持AE2优先存储
     */
    public static void handleDrops(Player player, List<ItemStack> drops, ItemStack tool) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;

            // 剩余进入背包
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 获取玩家指向的方块位置
     */
    public static BlockPos getTargetBlockPos(Player player) {
        double reach = 4.5D;
        HitResult hitResult = player.pick(reach, 0.0f, false);

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            return ((BlockHitResult) hitResult).getBlockPos();
        }
        return null;
    }

    /**
     * 检查工具是否处于精准采集模式（通过NBT）
     */
    public static boolean isSilkTouchMode(ItemStack stack) {
        if (stack.hasTag()) {
            return stack.getTag().getBoolean("SilkTouchMode");
        }
        return false;
    }

    /**
     * 检查工具是否处于增强连锁模式（通过NBT）
     */
    public static boolean isEnhancedChainMiningMode(ItemStack stack) {
        if (stack.hasTag()) {
            return stack.getTag().getBoolean("EnhancedChainMining");
        }
        return false;
    }

    /**
     * 检查是否启用了连锁挖掘（Tab按下状态）
     */
    public static boolean isChainMiningPressed(ItemStack stack) {
        if (stack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag.contains("ChainMiningPressed")) {
                return tag.getBoolean("ChainMiningPressed");
            }
            // 兼容旧版：检查ToolModes
            if (tag.contains("ToolModes")) {
                net.minecraft.nbt.CompoundTag toolModes = tag.getCompound("ToolModes");
                if (toolModes.contains("CHAIN_MINING")) {
                    return toolModes.getBoolean("CHAIN_MINING");
                }
            }
        }
        return false;
    }

    /**
     * 检查是否启用了强制挖掘模式（通过ModeManager NBT）
     */
    public static boolean isForceMiningMode(ItemStack stack) {
        if (stack.hasTag()) {
            net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag.contains("ToolModes")) {
                net.minecraft.nbt.CompoundTag toolModes = tag.getCompound("ToolModes");
                return toolModes.getBoolean("FORCE_MINING");
            }
        }
        return false;
    }

    /**
     * 快速破坏方块（Shift+右键快速破坏），掉落物直接进背包
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
}
