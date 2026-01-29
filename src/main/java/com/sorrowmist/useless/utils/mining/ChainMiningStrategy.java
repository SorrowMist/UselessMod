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
import net.minecraft.world.level.block.Block;
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
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState originState = event.getState();
        Block originBlock = originState.getBlock();

        // 1. 获取玩家的挖掘数据
        PlayerMiningData playerData = MiningDispatcher.getOrCreatePlayerData(player);

        // 检测是否为强制挖掘模式
        boolean forceMining = UComponentUtils.hasFunctionMode(hand, FunctionMode.FORCE_MINING);

        // 检查缓存
        List<BlockPos> blocksToMine;

        // 2. 缓存一致性检查
        // 检查当前破坏的方块是否是玩家按下 Tab 键时预计算的那个方块
        if (playerData.getCachedPos() != null
                && playerData.getCachedPos().equals(pos)
                && playerData.hasCachedBlocks()) {
            // 直接使用按下 Tab 时预存的列表，无需再次扫描计算
            blocksToMine = playerData.getCachedBlocks();
        } else {
            // 如果缓存不匹配（例如玩家没按 Tab 直接挖，或者瞬间移动了准星），则进行兜底计算
            blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, forceMining);
        }

        if (blocksToMine.isEmpty()) {
            // 清理缓存
            playerData.clearCache();
            return;
        }

        // 3. 执行挖掘逻辑
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);

            // 安全性检查：处理竞争问题
            // 如果在缓存计算后，方块被其他玩家挖走或替换，则跳过
            if (!currentState.is(originBlock)) {
                continue;
            }

            // 收集掉落物
            allDrops.addAll(MiningUtils.getBlockDrops(currentState, level, targetPos, player, hand, forceMining));

            // 移除方块
            level.removeBlock(targetPos, false);
            actualMinedCount++;
        }

        // 处理统一掉落物（合并后进背包）
        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        // 经验处理（仅在时运/默认模式下弹出）
        if (hand.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE) == EnchantMode.FORTUNE) {
            int exp = originBlock.getExpDrop(originState, level, pos, level.getBlockEntity(pos), player, hand);
            if (exp > 0) {
                // 根据实际破坏的数量倍增经验
                originBlock.popExperience(level, pos, exp * actualMinedCount);
            }
        }
        
        if (actualMinedCount > 0) {
            player.displayClientMessage(Component.literal("连锁挖掘: 破坏了 " + actualMinedCount + " 个方块"), true);
        }

        // 7. 清理并取消原版事件，防止重复破坏
        event.setCanceled(true);
        playerData.clearCache();
    }
}
