package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.SophisticatedCompat;
import com.sorrowmist.useless.content.blocks.AdvancedAlloyFurnaceBlock;
import com.sorrowmist.useless.core.component.UComponents;
import com.sorrowmist.useless.data.PlayerMiningData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.Collections;
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

        // 查找需要破坏的方块列表
        List<BlockPos> blocksToMine;
        if (playerData.getCachedPos() != null
                && playerData.getCachedPos().equals(pos)
                && playerData.hasCachedBlocks()) {
            blocksToMine = playerData.getCachedBlocks();
        } else {
            if (this.enhanced) {
                blocksToMine = MiningUtils.findBlocksToMineEnhanced(pos, originState, level, hand, false);
            } else {
                blocksToMine = MiningUtils.findBlocksToMine(pos, originState, level, hand, false);
            }
        }

        if (blocksToMine.isEmpty()) {
            playerData.clearCache();
            return;
        }

        // 检查是否为精准采集模式
        boolean isSilkTouch = hand.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE)
                == EnchantMode.SILK_TOUCH;

        // 执行连锁挖掘
        List<ItemStack> allDrops = new ArrayList<>();
        int actualMinedCount = 0;

        for (BlockPos targetPos : blocksToMine) {
            BlockState currentState = level.getBlockState(targetPos);

            // 安全性检查
            if (!currentState.is(originBlock)) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(targetPos);
            Block currentBlock = currentState.getBlock();

            // 根据模式获取掉落物
            List<ItemStack> drops;
            // 对万象合金炉特殊处理：使用方块的getDrops方法以保存数据
            if (currentBlock instanceof AdvancedAlloyFurnaceBlock alloyFurnaceBlock) {
                LootParams.Builder lootParams = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, targetPos.getCenter())
                        .withParameter(LootContextParams.TOOL, hand)
                        .withParameter(LootContextParams.THIS_ENTITY, player);
                if (be != null) {
                    lootParams.withParameter(LootContextParams.BLOCK_ENTITY, be);
                }
                drops = alloyFurnaceBlock.getDrops(currentState, lootParams);
            } else if (isSilkTouch) {
                drops = MiningUtils.getSilkTouchDrops(currentState, level, targetPos);
            } else {
                drops = Block.getDrops(currentState, level, targetPos, be, player, hand);
                if (MiningUtils.hasNoValidDrops(drops)) {
                    drops = Collections.singletonList(new ItemStack(currentBlock.asItem()));
                }
            }

            allDrops.addAll(drops);

            // 对于 SophisticatedStorage 方块，需要先设置 packed 状态
            if (ModList.get().isLoaded("sophisticatedstorage") && be != null) {
                SophisticatedCompat.handlePreRemoval(be);
            }

            MiningUtils.removeBlockSafely(level, targetPos);
            actualMinedCount++;
        }

        // 处理统一掉落物
        if (!MiningUtils.hasNoValidDrops(allDrops)) {
            MiningUtils.handleDrops(player, MiningUtils.mergeItemStacks(allDrops), hand);
        }

        // 经验处理（仅在时运模式下）
        if (!isSilkTouch && hand.getOrDefault(UComponents.EnchantModeComponent.get(),
                                              EnchantMode.FORTUNE
        ) == EnchantMode.FORTUNE) {
            int exp = originBlock.getExpDrop(originState, level, pos, level.getBlockEntity(pos), player, hand);
            if (exp > 0) {
                originBlock.popExperience(level, pos, exp * actualMinedCount);
            }
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
