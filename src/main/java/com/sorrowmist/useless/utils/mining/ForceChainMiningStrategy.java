package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.DraconicEvolutionCompat;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.data.PlayerMiningData;
import com.sorrowmist.useless.utils.UComponentUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * R键连锁破坏策略
 * 使用R键特殊的掉落逻辑进行连锁破坏
 */
public class ForceChainMiningStrategy implements MiningStrategy {
    private final boolean enhanced;

    ForceChainMiningStrategy(boolean enhanced) {
        this.enhanced = enhanced;
    }

    @Override
    public void handleBreak(BlockEvent.BreakEvent event, ItemStack hand, Player player) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState originState = event.getState();
        Block originBlock = originState.getBlock();

        // 获取玩家的挖掘数据
        PlayerMiningData playerData = MiningDispatcher.getOrCreatePlayerData(player);

        // R键连锁始终以强制挖掘语义查找方块（包含工具挖不动的方块）
        // 缓存是按组件 isForceMiningEnabled 计算的，只有组件已开启强制挖掘时缓存才等价于强制列表，可安全复用；
        // 否则必须以 forceMining=true 重新计算，避免漏掉挖不动的方块。
        boolean componentForce = UComponentUtils.isForceMiningEnabled(hand);
        List<BlockPos> blocksToMine;
        if (componentForce
                && playerData.getCachedPos() != null
                && playerData.getCachedPos().equals(pos)
                && playerData.hasCachedBlocks()) {
            blocksToMine = playerData.getCachedBlocks();
        } else if (this.enhanced) {
            blocksToMine = MiningUtils.findBlocksToMineEnhanced(pos, originState, level, hand, true);
        } else {
            blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, true);
        }

        if (blocksToMine.isEmpty()) {
            playerData.clearCache();
            return;
        }

        // 检查是否为精准采集模式
        boolean isSilkTouch = hand.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE)
                == EnchantMode.SILK_TOUCH;

        // 在破坏原点方块前捕获单个方块的经验值：破坏后 getBlockEntity(pos) 恒为 null，
        // 会导致依赖方块实体计算经验的方块算错。
        int expPerBlock = !isSilkTouch
                ? originBlock.getExpDrop(originState, level, pos, level.getBlockEntity(pos), player, hand)
                : 0;

        // 执行连锁挖掘
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);

            // 安全性检查
            if (!currentState.is(originBlock)) {
                continue;
            }

            // 检查是否是混沌水晶，如果是则使用特殊处理
            if (DraconicEvolutionCompat.isChaosCrystal(currentState)) {
                if (DraconicEvolutionCompat.handleChainMiningChaosCrystal(level, targetPos, currentState, player)) {
                    actualMinedCount++;
                    continue;
                }
            }

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

        // 处理统一掉落物
        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        // 经验处理（仅在时运模式下），使用破坏前捕获的经验值
        if (expPerBlock > 0 && actualMinedCount > 0) {
            originBlock.popExperience(level, pos, expPerBlock * actualMinedCount);
        }

        // 显示结果
        if (actualMinedCount > 0) {
            String translationKey = this.enhanced
                    ? "gui.useless_mod.force_enhanced_chain_mining_result"
                    : "gui.useless_mod.force_chain_mining_result";
            player.displayClientMessage(Component.translatable(translationKey, actualMinedCount), true);
        }

        // 清理并取消原版事件
        event.setCanceled(true);
        playerData.clearCache();
    }
}
