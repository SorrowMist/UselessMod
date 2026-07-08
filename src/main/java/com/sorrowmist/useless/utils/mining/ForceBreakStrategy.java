package com.sorrowmist.useless.utils.mining;

import com.sorrowmist.useless.api.enums.tool.EnchantMode;
import com.sorrowmist.useless.compat.DraconicEvolutionCompat;
import com.sorrowmist.useless.core.component.UComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

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
        Block block = state.getBlock();

        // 检查是否是混沌水晶，如果是则使用特殊处理
        if (DraconicEvolutionCompat.isChaosCrystal(state)) {
            if (DraconicEvolutionCompat.handleChaosCrystalBreak(level, pos, state, player)) {
                event.setCanceled(true);
                return;
            }
        }

        // 检查是否为精准采集模式
        boolean isSilkTouch = hand.getOrDefault(UComponents.EnchantModeComponent.get(), EnchantMode.FORTUNE)
                == EnchantMode.SILK_TOUCH;

        // 破坏前用掉落表判断方块是否有自然掉落，避免掉落实体被其他模组吸收导致误判
        boolean hasNaturalDrops = MiningUtils.blockHasNaturalDrops(level, pos, state, player, hand);
        List<ItemStack> fallbackDrops = hasNaturalDrops ? List.of() : MiningUtils.getForcedFallbackDrops(state, level, pos);
        List<ItemStack> drops = MiningUtils.destroyBlockAndCollectDrops(level, pos, state, player, hand);
        if (!hasNaturalDrops && MiningUtils.hasNoValidDrops(drops) && !MiningUtils.hasNoValidDrops(fallbackDrops)) {
            drops = fallbackDrops;
        }
        MiningUtils.handleDrops(player, drops, hand);

        // 计算并弹出经验（时运模式）
        BlockEntity be = level.getBlockEntity(pos);
        if (!isSilkTouch && hand.get(UComponents.EnchantModeComponent.get()) == EnchantMode.FORTUNE) {
            int exp = block.getExpDrop(state, level, pos, be, player, hand);
            if (exp > 0) {
                block.popExperience(level, pos, exp);
            }
        }

        // 取消原版事件
        event.setCanceled(true);
    }
}
