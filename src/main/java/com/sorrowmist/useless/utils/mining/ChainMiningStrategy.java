package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

    protected ChainMiningStrategy(boolean enhanced) {
        this.enhanced = enhanced;
    }

    protected boolean isForceMining(ItemStack hand) {
        return false;
    }

    protected boolean canUseCache(ItemStack hand, boolean forceMining) {
        return !forceMining || UComponentUtils.isForceMiningEnabled(hand);
    }

    protected String getResultTranslationKey() {
        return this.enhanced
                ? "gui.useless_mod.enhanced_chain_mining_result"
                : "gui.useless_mod.chain_mining_result";
    }

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState originState = event.getState();
        PlayerMiningData playerData = MiningDispatcher.getOrCreatePlayerData(player);
        boolean forceMining = this.isForceMining(hand);
        List<BlockPos> blocksToMine;

        if (this.canUseCache(hand, forceMining)
                && playerData.getCachedPos() != null
                && playerData.getCachedPos().equals(pos)
                && playerData.hasCachedBlocks()) {
            blocksToMine = playerData.getCachedBlocks();
        } else {
            blocksToMine = MiningUtils.scanBlocksToMine(
                    pos, originState, level, hand, forceMining, this.enhanced);
        }

        if (blocksToMine.isEmpty()) {
            playerData.clearCache();
            return;
        }

        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;
        int totalExperience = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);

            if (!currentState.is(originState.getBlock())) {
                continue;
            }

            MiningUtils.MiningResult result = forceMining
                    ? MiningUtils.forceMineBlock(level, targetPos, currentState, player, hand)
                    : MiningUtils.mineBlock(level, targetPos, currentState, player, hand);
            if (result.mined()) {
                allDrops.addAll(result.drops());
                totalExperience += result.experience();
                actualMinedCount++;
            }
        }

        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        if (totalExperience > 0) {
            player.giveExperiencePoints(totalExperience);
        }

        if (actualMinedCount > 0) {
            player.displayClientMessage(Component.translatable(
                    this.getResultTranslationKey(), actualMinedCount), true);
        }

        event.setCanceled(true);
        playerData.clearCache();
    }
}
