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
 * R键强制连锁破坏策略
 * 使用R键特殊的掉落逻辑进行连锁破坏
 */
public class ForceChainMiningStrategy implements MiningStrategy {
    private final boolean enhanced;

    public ForceChainMiningStrategy(boolean enhanced) {
        this.enhanced = enhanced;
    }

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState originState = event.getState();
        Block originBlock = originState.getBlock();

        // 查找需要破坏的方块列表
        List<BlockPos> blocksToMine;
        if (this.enhanced) {
            blocksToMine = MiningUtils.findBlocksToMineEnhanced(pos, originState, level, hand, false);
        } else {
            blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, false);
        }

        if (blocksToMine.isEmpty()) {
            return;
        }

        boolean isSilkTouch = MiningUtils.isSilkTouchMode(hand);

        // 执行连锁挖掘
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);
            if (currentState.getBlock() != originBlock) continue;

            // 精准采集模式下先清理容器内容物
            if (isSilkTouch) {
                clearContainerContents(level, targetPos);
            }

            List<ItemStack> fallbackDrops = MiningUtils.getForcedFallbackDrops(currentState, level, targetPos, hand);
            List<ItemStack> drops = MiningUtils.destroyBlockAndCollectDrops(level, targetPos, currentState, player, hand);
            if (MiningUtils.hasNoValidDrops(drops) && !MiningUtils.hasNoValidDrops(fallbackDrops)) {
                drops = fallbackDrops;
            }
            allDrops.addAll(drops);
            actualMinedCount++;
        }

        // 统一处理掉落物
        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        // 经验处理（时运模式）
        if (!isSilkTouch) {
            originBlock.popExperience(level, pos, originBlock.getExpDrop(originState, level, level.random, pos, 0, 0) * actualMinedCount);
        }

        if (actualMinedCount > 0) {
            String key = this.enhanced ? "强制增强连锁挖掘完成：" : "强制连锁挖掘完成：";
            player.displayClientMessage(Component.literal(key + "已挖掘 " + actualMinedCount + " 个方块"), true);
        }

        event.setCanceled(true);
    }

    private void clearContainerContents(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;

        try {
            if (be instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    container.setItem(i, ItemStack.EMPTY);
                }
                container.setChanged();
            } else {
                net.minecraftforge.items.IItemHandler handler = be.getCapability(
                        net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElse(null);
                if (handler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
                    for (int i = 0; i < modifiable.getSlots(); i++) {
                        modifiable.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
