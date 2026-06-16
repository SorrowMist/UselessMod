package com.sorrowmist.useless.utils.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

/**
 * 默认挖掘策略：普通单方块挖掘
 * - 开启强制挖掘模式时，忽略工具检查直接挖掘
 * - 未开启时，仅当工具适当时才挖掘
 */
public class DefaultMiningStrategy implements MiningStrategy {

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        BlockState state = event.getState();
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();

        boolean forceMining = MiningUtils.isForceMiningMode(hand);
        boolean isCorrectTool = hand.isCorrectToolForDrops(state);

        if (forceMining) {
            MiningUtils.processBlockBreak(level, pos, state, player, hand, true);
            event.setCanceled(true);
        } else if (isCorrectTool) {
            MiningUtils.processBlockBreak(level, pos, state, player, hand, false);
            event.setCanceled(true);
        }
        // 工具不正确且未开启强制挖掘：不处理，使用原版行为
    }
}
