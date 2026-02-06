package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.AE2Compat;
import com.sorrowmist.useless.compat.SophisticatedCompat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.core.config.ConfigManager;
import com.sorrowmist.useless.utils.UComponentUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.fml.ModList;

import java.util.*;

public class MiningUtils {

    /**
     * 获取方块掉落物（支持强制挖掘模式）
     */
    static List<ItemStack> getBlockDrops(BlockState state, ServerLevel level, BlockPos pos, Player player,
                                         ItemStack tool, boolean forceMining) {
        BlockEntity be = level.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, level, pos, be, player, tool);

        if (forceMining && hasNoValidDrops(drops)) {
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            boolean isSilk = tool.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE
            ) == EnchantMode.SILK_TOUCH;

            if (isSilk && be != null) {
                stack.applyComponents(be.collectComponents());
            }
            return Collections.singletonList(stack);
        }
        return drops;
    }

    /**
     * 检查是否完全没有有效掉落物
     */
    static boolean hasNoValidDrops(List<ItemStack> drops) {
        return drops.isEmpty() || drops.stream().allMatch(stack -> stack.isEmpty() || stack.is(Items.AIR));
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

        // 获取方块掉落物
        List<ItemStack> drops = getBlockDrops(state, level, pos, player, tool, forceMining);

        // 处理掉落物
        handleDrops(player, drops, tool);

        // 计算并弹出经验（时运模式）
        if (tool.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
            int exp = state.getBlock().getExpDrop(state, level, pos, level.getBlockEntity(pos), player, tool);
            if (exp > 0) {
                state.getBlock().popExperience(level, pos, exp);
            }
        }

        // 破坏方块
        level.removeBlock(pos, false);
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
    static List<BlockPos> findBlocksToMine(BlockPos originPos, BlockState originState, Level level, ItemStack stack,
                                           boolean forceMining) {
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
    static List<BlockPos> findBlocksToMineEnhanced(BlockPos originPos, BlockState originState, Level level,
                                                   ItemStack stack,
                                                   boolean forceMining) {
        // 最大连锁数量（包含原点方块）
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        Block originBlock = originState.getBlock();
        List<BlockPos> blocksToMine = new ArrayList<>(maxBlocks);

        // 增强连锁：直接在范围内扫描所有相同方块，不需要相邻限制
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

                    if (blocksToMine.size() >= maxBlocks) {
                        break;
                    }
                }
                if (blocksToMine.size() >= maxBlocks) {
                    break;
                }
            }
            if (blocksToMine.size() >= maxBlocks) {
                break;
            }
        }

        // 使用欧几里得距离平方进行排序
        blocksToMine.sort(Comparator.comparingDouble(pos -> pos.distSqr(originPos)));

        return blocksToMine;
    }

    /**
     * 获取精准采集模式的掉落物（带NBT）
     *
     * @param state 方块状态
     * @param level 世界
     * @param pos   方块位置
     * @return 带NBT的物品堆列表
     */
    static List<ItemStack> getSilkTouchDrops(BlockState state, ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        Block block = state.getBlock();

        // 使用 getCloneItemStack 获取正确的物品（处理 asItem() 返回空气的情况）
        ItemStack stack = block.getCloneItemStack(level, pos, state);

        // 如果还是空，尝试从掉落物列表获取
        if (stack.isEmpty()) {
            List<ItemStack> drops = Block.getDrops(state, level, pos, be, null, ItemStack.EMPTY);
            if (!drops.isEmpty()) {
                stack = drops.get(0).copy();
            }
        }

        if (be != null && !stack.isEmpty()) {
            // 尝试处理 SophisticatedStorage 的方块
            boolean handledByCompat = false;
            if (ModList.get().isLoaded("sophisticatedstorage")) {
                handledByCompat = SophisticatedCompat.handleSilkTouchDrop(block, be, stack);
            }

            // 如果不是 SophisticatedStorage 方块，使用标准方式复制NBT数据
            if (!handledByCompat) {
                stack.applyComponents(be.collectComponents());

                // 只有包含 container 组件时才添加 "+nbt" tooltip
                if (stack.has(DataComponents.CONTAINER)) {
                    Component nbtTooltip = Component.literal("+nbt")
                                                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                                                    .withStyle(ChatFormatting.ITALIC);

                    // 获取已有的 lore
                    ItemLore existingLore = stack.get(DataComponents.LORE);
                    List<Component> newLoreLines = new ArrayList<>();
                    if (existingLore != null) {
                        newLoreLines.addAll(existingLore.lines());
                    }

                    // 检查是否已经存在 "+nbt" tooltip，避免重复添加
                    boolean alreadyHasNbtTooltip = newLoreLines.stream()
                            .anyMatch(line -> line.getString().equals("+nbt"));

                    if (!alreadyHasNbtTooltip) {
                        newLoreLines.add(nbtTooltip);
                        stack.set(DataComponents.LORE, new ItemLore(newLoreLines));
                    }
                }
            }
        }

        return Collections.singletonList(stack);
    }

    /**
     * 安全移除方块，防止容器方块（箱子、潜影盒等）的内容物额外掉落
     * 在移除方块前会先清空 BlockEntity 的容器内容
     *
     * @param level 世界
     * @param pos   方块位置
     */
    static void removeBlockSafely(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        // 如果方块有 BlockEntity 且实现了 Container 接口（容器方块），先清空内容
        if (be instanceof Container container) {
            container.clearContent();
        }

        // 安全移除方块
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