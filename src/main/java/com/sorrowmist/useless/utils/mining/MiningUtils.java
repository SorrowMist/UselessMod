package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;
import java.util.stream.Collectors;

public class MiningUtils {
    /**
     * 获取方块掉落物（支持强制挖掘模式）
     */
    static List<ItemStack> getBlockDrops(BlockState state, Level level, BlockPos pos, Player player, ItemStack tool,
                                         boolean forceMining) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return Collections.emptyList();
        }

        List<ItemStack> drops;
        BlockEntity be = level.getBlockEntity(pos);

        // 首先尝试使用原版掉落机制
        drops = Block.getDrops(state, serverLevel, pos, be, player, tool);

        // 强制挖掘模式：如果没有有效掉落物，强制掉落一个方块
        if (forceMining && (drops.isEmpty() || drops.stream()
                                                    .allMatch(stack -> stack.isEmpty() || stack.is(Items.AIR)))) {
            Block block = state.getBlock();

            // 检查是否有精准采集附魔
            boolean hasSilkTouch = tool.getOrDefault(UComponents.EnchantModeComponent,
                                                     EnchantMode.FORTUNE
            ) == EnchantMode.SILK_TOUCH;

            if (hasSilkTouch) {
                // 精准采集：获取方块本身（包含NBT）
                ItemStack stack = new ItemStack(block);

                // 复制方块的NBT数据
                if (be != null) {
                    DataComponentMap components = be.collectComponents();
                    for (TypedDataComponent<?> component : components) {
                        stack.set((DataComponentType) component.type(), component.value());
                    }
                }
                drops.add(stack);
            } else {
                // 非精准采集：获取方块的默认掉落物
                drops.add(new ItemStack(block));
            }
        }

        return drops.stream()
                    .filter(drop -> !drop.isEmpty() && drop.getItem() != Items.AIR)
                    .collect(Collectors.toList());
    }

    /**
     * 检查是否完全没有有效掉落物
     */
    static boolean hasNoValidDrops(List<ItemStack> drops) {
        return drops.isEmpty() || drops.stream().allMatch(stack -> stack.isEmpty() || stack.is(Items.AIR));
    }

    /**
     * 使用原版掉落，然后立即收集物品实体（支持强制挖掘模式）
     */
    static void handleFallbackBlockBreak(BlockEvent.BreakEvent event, Level level, BlockPos pos, BlockState state,
                                         Player player, ItemStack tool,
                                         boolean forceMining) {
        if (level.isClientSide()) return;

        // 强制挖掘模式：使用统一的处理方法
        // 正常模式：使用统一的处理方法
        processBlockBreak(level, pos, state, player, tool, forceMining);
        event.setCanceled(true);
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
    static void processBlockBreak(Level level, BlockPos pos, BlockState state, Player player,
                                  ItemStack tool, boolean forceMining) {
        if (level.isClientSide()) {
            return;
        }

        // 获取方块掉落物
        List<ItemStack> drops = getBlockDrops(state, level, pos, player, tool, forceMining);

        // 处理掉落物
        handleDrops(player, drops);

        // 计算并弹出经验（时运模式）
        if (tool.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
            int exp = state.getBlock().getExpDrop(state, level, pos, level.getBlockEntity(pos), player, tool);
            if (exp > 0) {
                state.getBlock().popExperience((ServerLevel) level, pos, exp);
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
        handleDrops(player, drops);

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
            for (ItemStack mergedItem : merged) {
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
    static void handleDrops(Player player, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
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
    static List<BlockPos> findBlocksToMine(BlockPos originPos, BlockState originState, Level level,
                                           ItemStack stack, boolean forceMining) {
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();

        // 最大连锁数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();

        Block originBlock = originState.getBlock();
        List<BlockPos> blocksToMine = new ArrayList<>(maxBlocks);
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>(maxBlocks * 2);

        queue.add(originPos);
        visited.add(originPos);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        while (!queue.isEmpty() && blocksToMine.size() < maxBlocks) {
            BlockPos currentPos = queue.poll();
            blocksToMine.add(currentPos);

            // 扩散搜索：使用三重循环遍历所有26个方向
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        if (x == 0 && y == 0 && z == 0) continue;

                        mutablePos.set(currentPos.getX() + x, currentPos.getY() + y, currentPos.getZ() + z);

                        // 边界检查
                        if (Math.abs(mutablePos.getX() - originPos.getX()) > rangeX ||
                                Math.abs(mutablePos.getY() - originPos.getY()) > rangeY ||
                                Math.abs(mutablePos.getZ() - originPos.getZ()) > rangeZ) {
                            continue;
                        }

                        if (visited.contains(mutablePos)) continue;

                        BlockState nextState = level.getBlockState(mutablePos);
                        if (nextState.getBlock() == originBlock) {
                            if (forceMining || stack.isCorrectToolForDrops(nextState)) {
                                BlockPos immutableNext = mutablePos.immutable();
                                visited.add(immutableNext);
                                queue.add(immutableNext);

                                // 限制搜索总数
                                if (visited.size() >= maxBlocks * 2) break;
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
}