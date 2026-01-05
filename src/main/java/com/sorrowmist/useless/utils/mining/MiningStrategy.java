package com.sorrowmist.useless.utils.mining;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.level.BlockEvent;

public interface MiningStrategy {
    /**
     * 处理方块破坏逻辑。
     *
     * @param event  BreakEvent事件
     * @param item   主手物品
     * @param player 玩家
     */
    void handleBreak(BlockEvent.BreakEvent event, ItemStack item, Player player);
}