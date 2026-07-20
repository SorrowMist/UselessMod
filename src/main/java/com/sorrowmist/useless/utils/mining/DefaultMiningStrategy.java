package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;


public class DefaultMiningStrategy implements MiningStrategy {
    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        BlockState state = event.getState();
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();

        boolean forceMining = UComponentUtils.isForceMiningEnabled(hand);

        if (MiningUtils.canMineBlock(state, hand, forceMining)) {
            MiningUtils.processBlockBreak(level, pos, state, player, hand, forceMining);
            event.setCanceled(true);
        }
    }
}
