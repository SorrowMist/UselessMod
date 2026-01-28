package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.tool.FunctionMode;
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

        // 检查是否开启了强制挖掘模式
        boolean forceMining = UComponentUtils.hasFunctionMode(hand, FunctionMode.FORCE_MINING);
        // 检查工具是否适合挖掘此方块
        boolean isCorrectTool = hand.isCorrectToolForDrops(state);

        // 强制挖掘模式：忽略工具类型检查，直接强制挖掘
        if (forceMining) {
            MiningUtils.processBlockBreak(level, pos, state, player, hand, true);
            event.setCanceled(true);
        } else {
            // 非强制挖掘模式：检查工具是否正确
            if (isCorrectTool) {
                MiningUtils.processBlockBreak(level, pos, state, player, hand, false);
                event.setCanceled(true);
            }
            // else
            // 工具不正确且未开启强制挖掘：不处理，使用原版行为
            // 原版会根据工具情况决定是否允许破坏及掉落
        }
    }
}