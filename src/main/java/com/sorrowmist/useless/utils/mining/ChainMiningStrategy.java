package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.data.PlayerMiningData;
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
    private final boolean enhanced;

    ChainMiningStrategy(boolean enhanced) {
        this.enhanced = enhanced;
    }

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState originState = event.getState();
        Block originBlock = originState.getBlock();

        // 1. 获取玩家的挖掘数据
        PlayerMiningData playerData = MiningDispatcher.getOrCreatePlayerData(player);

        // 检测是否为强制挖掘模式
        boolean forceMining = UComponentUtils.isForceMiningEnabled(hand);

        // 检查缓存
        List<BlockPos> blocksToMine;

        // 2. 缓存一致性检查
        // 检查当前破坏的方块是否是玩家按下 Tab 键时预计算方块
        if (playerData.getCachedPos() != null
                && playerData.getCachedPos().equals(pos)
                && playerData.hasCachedBlocks()) {
            // 直接使用按下 Tab 时预存的列表，无需再次扫描计算
            blocksToMine = playerData.getCachedBlocks();
        } else {
            // 缓存不匹配进行兜底计算
            if (this.enhanced) {
                blocksToMine = MiningUtils.findBlocksToMineEnhanced(pos, originState, level, hand, forceMining);
            } else {
                blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, forceMining);
            }
        }

        if (blocksToMine.isEmpty()) {
            // 清理缓存
            playerData.clearCache();
            return;
        }

        // 在破坏原点方块前捕获单个方块的经验值：破坏后 getBlockEntity(pos) 恒为 null，
        // 会导致依赖方块实体计算经验的方块算错。
        boolean fortuneMode = hand.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE) == EnchantMode.FORTUNE;
        // 强制挖掘 + 精准采集同时激活：不再检查凋落物列表，直接兜底掉落方块本身（含完整 NBT/组件）
        boolean forceSilkFallback = forceMining && MiningUtils.isSilkTouch(hand);
        int expPerBlock = fortuneMode
                ? originBlock.getExpDrop(originState, level, pos, level.getBlockEntity(pos), player, hand)
                : 0;

        // 3. 执行挖掘逻辑
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);

            // 安全性检查：处理竞争问题
            // 缓存计算后，方块被其他玩家挖走或替换，则跳过
            if (!currentState.is(originBlock)) {
                continue;
            }

            // 破坏前用掉落表判断方块是否有自然掉落，避免掉落实体被其他模组吸收导致误判
            if (forceSilkFallback) {
                List<ItemStack> fallbackDrops = MiningUtils.getForcedFallbackDrops(currentState, level, targetPos);
                MiningUtils.destroyBlockAndCollectDrops(level, targetPos, currentState, player, hand);
                allDrops.addAll(fallbackDrops);
                actualMinedCount++;
                continue;
            }
            boolean hasNaturalDrops = !forceMining || MiningUtils.blockHasNaturalDrops(level, targetPos, currentState, player, hand);
            List<ItemStack> fallbackDrops = (forceMining && !hasNaturalDrops)
                    ? MiningUtils.getForcedFallbackDrops(currentState, level, targetPos)
                    : List.of();
            List<ItemStack> drops = MiningUtils.destroyBlockAndCollectDrops(level, targetPos, currentState, player, hand);
            if (forceMining && !hasNaturalDrops && MiningUtils.hasNoValidDrops(drops) && !MiningUtils.hasNoValidDrops(fallbackDrops)) {
                drops = fallbackDrops;
            }
            allDrops.addAll(drops);
            actualMinedCount++;
        }

        // 处理统一掉落物（合并后进背包）
        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        // 经验处理（仅在时运/默认模式下弹出），使用破坏前捕获的经验值
        if (fortuneMode && expPerBlock > 0 && actualMinedCount > 0) {
            // 根据实际破坏的数量倍增经验
            originBlock.popExperience(level, pos, expPerBlock * actualMinedCount);
        }
        
        if (actualMinedCount > 0) {
            String translationKey = this.enhanced
                    ? "gui.useless_mod.enhanced_chain_mining_result"
                    : "gui.useless_mod.chain_mining_result";
            player.displayClientMessage(Component.translatable(translationKey, actualMinedCount), true);
        }

        // 7. 清理并取消原版事件，防止重复破坏
        event.setCanceled(true);
        playerData.clearCache();
    }
}
