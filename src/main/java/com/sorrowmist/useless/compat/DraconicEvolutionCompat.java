package com.sorrowmist.useless.compat;

import com.brandon3055.draconicevolution.DEConfig;
import com.brandon3055.draconicevolution.blocks.ChaosCrystal;
import com.brandon3055.draconicevolution.blocks.tileentity.TileChaosCrystal;
import com.brandon3055.draconicevolution.init.DEContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

/**
 * Draconic Evolution 模组兼容性处理
 * 主要用于处理混沌水晶的特殊破坏逻辑
 */
public class DraconicEvolutionCompat {

    private static final String MOD_ID = "draconicevolution";
    private static Boolean isDELoaded = null;

    /**
     * 检查 Draconic Evolution 是否已加载
     */
    public static boolean isLoaded() {
        if (isDELoaded == null) {
            isDELoaded = ModList.get().isLoaded(MOD_ID);
        }
        return isDELoaded;
    }

    /**
     * 检查方块是否是混沌水晶
     */
    public static boolean isChaosCrystal(BlockState state) {
        if (!isLoaded()) return false;
        return state.getBlock() instanceof ChaosCrystal;
    }

    /**
     * 处理混沌水晶的强制破坏
     * 绕过正常的破坏流程，直接破坏并生成掉落物，避免触发 detonate() 的重生逻辑
     *
     * @param level  世界
     * @param pos    位置
     * @param state  方块状态
     * @param player 玩家
     * @return 如果处理了返回 true
     */
    public static boolean handleChaosCrystalBreak(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        if (!isLoaded()) return false;

        // 检查是否是混沌水晶
        if (!(state.getBlock() instanceof ChaosCrystal)) {
            return false;
        }

        BlockEntity tileEntity = level.getBlockEntity(pos);
        if (!(tileEntity instanceof TileChaosCrystal chaosCrystal)) {
            return false;
        }

        // 1. 设置守卫已击败状态
        chaosCrystal.setDefeated();

        // 2. 获取混沌碎片掉落数量并生成掉落物
        int dropCount = DEConfig.chaosDropCount;
        ItemStack chaosShardStack = new ItemStack(DEContent.CHAOS_SHARD.get(), dropCount);
        Block.popResource(level, pos, chaosShardStack);

        // 3. 清除周围的水晶部分（上下各2格）
        clearCrystalParts(level, pos);

        // 4. 直接设置方块为空气，绕过 onRemove 方法的 detonate 调用
        // 先移除 BlockEntity 防止触发 detonate
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        return true;
    }

    /**
     * 清除混沌水晶周围的部分（上下各2格）
     */
    private static void clearCrystalParts(ServerLevel level, BlockPos pos) {
        Block partBlock = DEContent.CHAOS_CRYSTAL_PART.get();

        // 清除上下各2格
        BlockPos[] positions = {
            pos.above(), pos.above(2),
            pos.below(), pos.below(2)
        };

        for (BlockPos partPos : positions) {
            BlockState partState = level.getBlockState(partPos);
            // 如果是混沌水晶部分，先移除 BlockEntity 再设置空气
            if (partState.is(partBlock)) {
                level.removeBlockEntity(partPos);
                level.setBlock(partPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    /**
     * 检查并处理连锁挖掘中的混沌水晶
     * 返回是否应该跳过此方块（由本方法处理）
     */
    public static boolean handleChainMiningChaosCrystal(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        return handleChaosCrystalBreak(level, pos, state, player);
    }
}
