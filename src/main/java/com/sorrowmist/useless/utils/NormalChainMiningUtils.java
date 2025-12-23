package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * 普通连锁挖掘工具类，包含普通连锁模式下的方块查找逻辑
 */
public class NormalChainMiningUtils {

    /**
     * 普通连锁模式下查找需要破坏的方块
     * @param originPos 原点位置
     * @param originState 原点方块状态
     * @param level 世界
     * @param stack 工具
     * @param forceMining 是否为强制挖掘模式
     * @return 需要破坏的方块列表
     */
    public static List<BlockPos> findBlocksToMine(BlockPos originPos, BlockState originState, Level level, ItemStack stack, boolean forceMining) {
        // 获取连锁挖掘范围
        int rangeX = ConfigManager.getChainMiningRangeX();
        int rangeY = ConfigManager.getChainMiningRangeY();
        int rangeZ = ConfigManager.getChainMiningRangeZ();
        
        // 获取原点方块类型
        Block originBlock = originState.getBlock();
        
        // 获取连锁挖掘最大方块数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        int blocksMined = 0;
        
        // 使用队列进行广度优先搜索
        Queue<BlockPos> queue = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> blocksToMine = new ArrayList<>();
        
        // 添加原点方块
        queue.add(originPos);
        visited.add(originPos);
        
        while (!queue.isEmpty() && blocksMined < maxBlocks) {
            BlockPos currentPos = queue.poll();
            
            // 获取当前方块状态
            BlockState currentState = level.getBlockState(currentPos);
            Block currentBlock = currentState.getBlock();
            
            // 检查是否是相同类型的方块
            if (currentBlock != originBlock) {
                continue;
            }
            
            // 检查是否可以被该工具挖掘，强制挖掘模式下跳过此检查
            if (!forceMining && !stack.isCorrectToolForDrops(currentState)) {
                continue;
            }
            
            // 添加到待挖掘列表
            blocksToMine.add(currentPos);
            blocksMined++;
            
            // 遍历相邻方块
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        // 跳过中心方块
                        if (x == 0 && y == 0 && z == 0) continue;
                        
                        BlockPos neighborPos = currentPos.offset(x, y, z);
                        
                        // 检查是否在范围内
                        int dx = Math.abs(neighborPos.getX() - originPos.getX());
                        int dy = Math.abs(neighborPos.getY() - originPos.getY());
                        int dz = Math.abs(neighborPos.getZ() - originPos.getZ());
                        
                        if (dx <= rangeX && dy <= rangeY && dz <= rangeZ) {
                            if (!visited.contains(neighborPos)) {
                                // 获取相邻方块状态
                                BlockState neighborState = level.getBlockState(neighborPos);
                                Block neighborBlock = neighborState.getBlock();
                                
                                // 检查是否是相同类型的方块且可以被挖掘，强制挖掘模式下跳过工具适合性检查
                        if (neighborBlock == originBlock && (forceMining || stack.isCorrectToolForDrops(neighborState))) {
                            queue.add(neighborPos);
                            visited.add(neighborPos);
                        }
                            }
                        }
                    }
                }
            }
        }
        
        return blocksToMine;
    }
}
