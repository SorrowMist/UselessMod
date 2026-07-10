package com.sorrowmist.useless.utils.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 连锁挖掘策略
 * - 普通连锁：BFS相邻方块
 * - 增强连锁：扫描范围内所有相同方块
 */
public class ChainMiningStrategy implements MiningStrategy {
    private final boolean enhanced;

    public ChainMiningStrategy(boolean enhanced) {
        this.enhanced = enhanced;
    }

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        BlockState originState = event.getState();
        Block originBlock = originState.getBlock();

        boolean forceMining = MiningUtils.isForceMiningMode(hand);

        // 查找需要破坏的方块列表
        List<BlockPos> blocksToMine;
        if (this.enhanced) {
            blocksToMine = MiningUtils.findBlocksToMineEnhanced(pos, originState, level, hand, forceMining);
        } else {
            blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, forceMining);
        }

        if (blocksToMine.isEmpty()) {
            return;
        }

        // 在破坏原点方块前捕获经验值：破坏后 getBlockEntity(pos) 恒为 null
        int expPerBlock = !MiningUtils.isSilkTouchMode(hand)
                ? originBlock.getExpDrop(originState, level, level.random, pos, 0, 0) : 0;

        // 执行挖掘
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);
            if (currentState.getBlock() != originBlock) continue;

            // 破坏前用掉落表判断方块是否有自然掉落，避免掉落实体被其他模组吸收导致误判
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

        // 统一处理掉落物
        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        // 经验处理（时运模式），使用破坏前捕获的经验值
        if (expPerBlock > 0 && actualMinedCount > 0) {
            originBlock.popExperience(level, pos, expPerBlock * actualMinedCount);
        }

        // 显示结果
        if (actualMinedCount > 0) {
            String key = this.enhanced ? "增强连锁挖掘完成：" : "连锁挖掘完成：";
            player.displayClientMessage(Component.literal(key + "已挖掘 " + actualMinedCount + " 个方块"), true);
        }

        event.setCanceled(true);
    }
}
