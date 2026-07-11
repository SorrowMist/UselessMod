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

        // R键连锁始终以强制挖掘语义查找方块（包含工具挖不动的方块）
        List<BlockPos> blocksToMine;
        if (this.enhanced) {
            blocksToMine = MiningUtils.findBlocksToMineEnhanced(pos, originState, level, hand, true);
        } else {
            blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, true);
        }

        if (blocksToMine.isEmpty()) {
            return;
        }

        boolean isSilkTouch = MiningUtils.isSilkTouchMode(hand);

        // 在破坏原点方块前捕获经验值：破坏后 getBlockEntity(pos) 恒为 null
        int expPerBlock = !isSilkTouch ? originBlock.getExpDrop(originState, level, level.random, pos, 0, 0) : 0;

        // 执行连锁挖掘
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);
            if (currentState.getBlock() != originBlock) continue;

            // 强制挖掘（R键）+ 精准采集同时激活：不再检查凋落物列表，直接兜底掉落方块本身（含完整 NBT/组件）
            if (isSilkTouch) {
                List<ItemStack> silkFallback = MiningUtils.getForcedFallbackDrops(currentState, level, targetPos);
                MiningUtils.destroyBlockAndCollectDrops(level, targetPos, currentState, player, hand);
                allDrops.addAll(silkFallback);
                actualMinedCount++;
                continue;
            }

            // 破坏前用掉落表判断方块是否有自然掉落，避免掉落实体被其他模组吸收导致误判
            List<ItemStack> preDrops = MiningUtils.blockHasNaturalDrops(level, targetPos, currentState, player, hand)
                    ? Block.getDrops(currentState, level, targetPos, level.getBlockEntity(targetPos), player, hand)
                    : List.of();
            boolean hasNaturalDrops = !MiningUtils.hasNoValidDrops(preDrops)
                    && !MiningUtils.dropsAreDowngradedBlocks(preDrops, originBlock);
            List<ItemStack> fallbackDrops = hasNaturalDrops ? List.of() : MiningUtils.getForcedFallbackDrops(currentState, level, targetPos);
            List<ItemStack> drops = MiningUtils.destroyBlockAndCollectDrops(level, targetPos, currentState, player, hand);
            if (!hasNaturalDrops && (MiningUtils.hasNoValidDrops(drops) || MiningUtils.dropsAreDowngradedBlocks(drops, originBlock))
                    && !MiningUtils.hasNoValidDrops(fallbackDrops)) {
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
