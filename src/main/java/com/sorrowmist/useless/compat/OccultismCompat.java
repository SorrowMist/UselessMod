package com.sorrowmist.useless.compat;

import com.klikli_dev.occultism.common.block.otherworld.IOtherworldBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Occultism mod 兼容处理
 * 处理 IOtherworldBlock 的特殊掉落逻辑
 */
public class OccultismCompat {

    /**
     * 检查方块是否为 IOtherworldBlock 并获取正确的掉落物
     * 
     * @param state 方块状态
     * @param level 世界
     * @param pos 方块位置
     * @param player 玩家
     * @param tool 工具
     * @return 如果是 IOtherworldBlock 则返回正确的掉落物，否则返回 null
     */
    public static List<ItemStack> getOtherworldBlockDrops(BlockState state, ServerLevel level, BlockPos pos, 
                                                           Player player, ItemStack tool) {
        // 检查 occultism 是否加载
        if (!ModList.get().isLoaded("occultism")) {
            return null;
        }
        
        Block block = state.getBlock();
        
        // 检查是否为 IOtherworldBlock
        if (block instanceof IOtherworldBlock otherworldBlock) {
            // 获取正确的方块状态
            BlockState harvestState = otherworldBlock.getHarvestState(player, state, tool);
            
            // 使用正确的状态获取掉落物
            BlockEntity be = level.getBlockEntity(pos);
            return Block.getDrops(harvestState, level, pos, be, player, tool);
        }
        
        return null;
    }
}
