package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.component.UComponents;
import com.sorrowmist.useless.api.data.PlayerMiningData;
import com.sorrowmist.useless.api.tool.EnchantMode;
import com.sorrowmist.useless.api.tool.FunctionMode;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2026 C-H716
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
public class ChainMiningStrategy implements MiningStrategy {
    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        BlockState state = event.getState();
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        // 获取玩家的挖矿数据
        PlayerMiningData playerData = MiningDispatcher.getOrCreatePlayerData(player);

        // 检测是否为强制挖掘模式
        boolean forceMining = UComponentUtils.hasFunctionMode(hand, FunctionMode.FORCE_MINING);

        // 检查缓存
        List<BlockPos> blocksToMine;
        if (playerData.getCachedPos() != null && playerData.getCachedState() != null
                && playerData.getCachedPos().equals(pos) && playerData.getCachedState() == state
                && playerData.hasCachedBlocks()) {
            blocksToMine = playerData.getCachedBlocks();
        } else {
            // 计算要挖掘的方块
            blocksToMine = MiningUtils.findBlocksToMine(pos, state, level, hand, forceMining);
            // 更新缓存
            playerData.setCachedPos(pos);
            playerData.setCachedState(state);
            playerData.setCachedBlocks(blocksToMine);
        }

        if (blocksToMine.isEmpty()) {
            // 清理缓存
            playerData.clearCache();
            return;
        }

        // 显示连锁挖矿信息
        player.sendSystemMessage(Component.literal("连锁挖矿: 破坏了 " + blocksToMine.size() + " 个方块"));

        List<ItemStack> drops = new ArrayList<>();

        // 收集所有方块的掉落物
        for (BlockPos blockPos : blocksToMine) {
            BlockState blockState = level.getBlockState(blockPos);
            drops.addAll(MiningUtils.getBlockDrops(blockState,
                                                   level,
                                                   blockPos,
                                                   player,
                                                   hand,
                                                   forceMining
            ));
        }

        if (MiningUtils.hasNoValidDrops(drops)) {
            // 回退处理：破坏所有方块
            for (BlockPos blockPos : blocksToMine) {
                BlockState blockState = level.getBlockState(blockPos);
                MiningUtils.processBlockBreak(level, blockPos, blockState, player, hand, forceMining);
            }
            // 取消原始事件，避免重复处理
            event.setCanceled(true);
        } else {
            // 合并相同物品的堆叠
            List<ItemStack> mergedDrops = MiningUtils.mergeItemStacks(drops);

            // 处理掉落物（只处理一次）
            MiningUtils.handleDrops(player, mergedDrops);

            // 计算并弹出经验（只处理一次）
            if (hand.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
                int exp = state.getBlock().getExpDrop(state, level, pos, level.getBlockEntity(pos), player, hand);
                if (exp > 0) {
                    state.getBlock().popExperience((ServerLevel) level, pos, exp * blocksToMine.size());
                }
            }

            // 破坏所有方块（静默移除）
            for (BlockPos blockPos : blocksToMine) {
                level.removeBlock(blockPos, false);
            }

            // 取消原始事件，避免重复处理
            event.setCanceled(true);
        }

        // 挖掘完成后清理缓存
        playerData.clearCache();
    }
}
