package com.sorrowmist.useless.utils.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * R键单方块破坏策略
 * 特殊掉落逻辑：
 * 1. 精准采集模式：强制获取带NBT的方块
 * 2. 非精准采集模式：正常获取掉落物，无掉落物时强制掉落方块本身
 * 3. 对万象合金炉特殊处理：使用方块的getDrops方法以保存数据
 */
public class ForceBreakStrategy implements MiningStrategy {

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = event.getState();
        MiningUtils.processBlockBreak(level, pos, state, player, hand, true);
        event.setCanceled(true);
    }
}
