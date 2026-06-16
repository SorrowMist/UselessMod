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

        for (int x = -rangeX; x <= rangeX && blocksToMine.size() < maxBlocks; x++) {
            for (int y = -rangeY; y <= rangeY && blocksToMine.size() < maxBlocks; y++) {
                for (int z = -rangeZ; z <= rangeZ && blocksToMine.size() < maxBlocks; z++) {
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

        blocksToMine.sort(Comparator.comparingDouble(pos -> pos.distSqr(originPos)));
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
        List<ItemStack> fallbackDrops = forceMining ? getForcedFallbackDrops(state, level, pos, tool) : Collections.emptyList();
        List<ItemStack> drops = destroyBlockAndCollectDrops(level, pos, state, player, tool);
        if (forceMining && hasNoValidDrops(drops) && !hasNoValidDrops(fallbackDrops)) {
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
     */
    public static List<ItemStack> getForcedFallbackDrops(BlockState state, ServerLevel level, BlockPos pos,
                                                          ItemStack tool) {
        BlockEntity be = level.getBlockEntity(pos);
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        if (stack.isEmpty() || stack.getItem() == Items.AIR) {
            return Collections.emptyList();
        }

        boolean isSilk = isSilkTouchMode(tool);
        if (isSilk && be != null) {
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
     * 通过方块自身的破坏回调破坏方块，并收集本次新生成的掉落实体
     */
    public static List<ItemStack> destroyBlockAndCollectDrops(ServerLevel level, BlockPos pos, BlockState state,
                                                               Player player, ItemStack tool) {
        AABB area = new AABB(pos).inflate(1.0);
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
