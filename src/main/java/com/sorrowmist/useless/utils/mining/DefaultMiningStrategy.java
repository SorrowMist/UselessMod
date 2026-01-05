package com.sorrowmist.useless.utils.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;


public class DefaultMiningStrategy implements MiningStrategy {
    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        BlockState state = event.getState();
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        List<ItemStack> drops = MiningUtils.getBlockDrops(state, level, pos, player, hand);

        if (MiningUtils.hasNoValidDrops(drops)) {
            MiningUtils.handleFallbackBlockBreak(level, pos, state, player, hand);
        } else {
            MiningUtils.handleNormalBlockBreak(event, level, pos, state, player, hand, drops);
        }
    }
}