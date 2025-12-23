package com.sorrowmist.useless.utils;

import com.sorrowmist.useless.config.ConfigManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强连锁挖掘工具类，包含增强连锁模式下的方块查找逻辑
 */
public class EnhancedChainMiningUtils {

    /**
     * 增强连锁模式下查找需要破坏的方块
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
        
        // 收集范围内所有符合条件的方块
        List<BlockPos> allBlocks = new ArrayList<>();
        
        // 遍历范围内的所有方块
        for (int x = -rangeX; x <= rangeX; x++) {
            for (int y = -rangeY; y <= rangeY; y++) {
                for (int z = -rangeZ; z <= rangeZ; z++) {
                    // 计算当前方块位置
                    BlockPos currentPos = originPos.offset(x, y, z);
                    
                    // 检查方块是否存在
                    if (level.isEmptyBlock(currentPos)) {
                        continue;
                    }
                    
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
                    allBlocks.add(currentPos);
                }
            }
        }
        
        // 按照距离原点方块的远近排序
        allBlocks.sort((pos1, pos2) -> {
            double dist1 = pos1.distSqr(originPos);
            double dist2 = pos2.distSqr(originPos);
            return Double.compare(dist1, dist2);
        });
        
        // 获取连锁挖掘最大方块数量
        int maxBlocks = ConfigManager.getChainMiningMaxBlocks();
        
        // 如果超过最大数量，只返回前maxBlocks个
        if (allBlocks.size() > maxBlocks) {
            return allBlocks.subList(0, maxBlocks);
        }
        
        return allBlocks;
    }
}
